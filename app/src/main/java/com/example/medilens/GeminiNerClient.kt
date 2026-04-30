package com.example.medilens

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Stage 3 — Named Entity Recognition using Gemini via direct REST API.
 *
 * Model waterfall (all free tier):
 *   1. gemini-3.1-flash-lite-preview  — 500 RPD, fastest
 *   2. gemini-2.5-flash-lite          — 20 RPD fallback
 *   3. gemini-2.0-flash               — 200 RPD fallback
 *
 * On 503 or timeout: waits 8 seconds then tries next model.
 * On 429 (quota): skips immediately to next model.
 */
object GeminiNerClient {

    private const val API_KEY = "AIzaSyAMX-4yLWLNU9Agi1sprG-rxNFMFaRA-JY"

    // Model waterfall — tried in order
    private val MODELS = listOf(
        "gemini-3.1-flash-lite-preview",
        "gemini-2.0-flash",
        "gemini-2.5-flash-lite"
    )

    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"

    private val YOLO_CLASSES = setOf("panadol", "risek", "myteka", "ventolin")

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)   // increased for slower models
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun extractMedicines(rawText: String): List<ParsedMedicine> =
        withContext(Dispatchers.IO) {
            var lastError: Exception? = null

            for ((index, model) in MODELS.withIndex()) {
                try {
                    android.util.Log.d("GeminiNER", "Trying model: $model")
                    val result = callGemini(model, rawText)
                    android.util.Log.d("GeminiNER", "Success with model: $model")
                    return@withContext result
                } catch (e: Exception) {
                    lastError = e
                    val msg = e.message ?: ""
                    android.util.Log.w("GeminiNER", "Model $model failed: $msg")

                    val isRetryable = msg.contains("503") || msg.contains("timeout", ignoreCase = true)
                    val isQuotaError = msg.contains("429") || msg.contains("quota", ignoreCase = true)
                    val hasNextModel = index < MODELS.size - 1

                    when {
                        !hasNextModel -> {
                            // All models exhausted
                            throw Exception("All Gemini models failed. Last error: $msg")
                        }
                        isRetryable -> {
                            // Server overloaded — wait 8s then try next model
                            android.util.Log.d("GeminiNER", "Waiting 8s before trying next model…")
                            delay(8_000)
                        }
                        isQuotaError -> {
                            // Quota hit — skip immediately to next model
                            android.util.Log.d("GeminiNER", "Quota hit, skipping to next model immediately")
                        }
                        else -> {
                            // Unknown error — don't retry, throw immediately
                            throw e
                        }
                    }
                }
            }

            throw lastError ?: Exception("All Gemini models failed")
        }

    // ── Single model call ─────────────────────────────────────────────────

    private fun callGemini(model: String, rawText: String): List<ParsedMedicine> {
        val url  = "$BASE/$model:generateContent?key=$API_KEY"
        val body = buildRequestBody(rawText)

        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val responseStr = response.body?.string()
            ?: throw Exception("Empty response from Gemini ($model)")

        if (!response.isSuccessful) {
            val errorMsg = try {
                JSONObject(responseStr)
                    .getJSONObject("error")
                    .getString("message")
            } catch (e: Exception) {
                responseStr.take(300)
            }
            throw Exception("Gemini API error ${response.code}: $errorMsg")
        }

        val text = extractTextFromResponse(responseStr, model)
        return parseJsonResponse(text)
    }

    // ── Build the REST request body ───────────────────────────────────────

    private fun buildRequestBody(rawText: String): JSONObject {
        val part     = JSONObject().put("text", buildPrompt(rawText))
        val parts    = JSONArray().put(part)
        val content  = JSONObject().put("parts", parts)
        val contents = JSONArray().put(content)

        val generationConfig = JSONObject()
            .put("temperature", 0.1)
            .put("maxOutputTokens", 2048)

        return JSONObject()
            .put("contents", contents)
            .put("generationConfig", generationConfig)
    }

    // ── Extract text from the REST response ──────────────────────────────

    private fun extractTextFromResponse(responseStr: String, model: String): String {
        return try {
            JSONObject(responseStr)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        } catch (e: Exception) {
            throw Exception("Could not parse Gemini response ($model): ${e.message}\nRaw: ${responseStr.take(500)}")
        }
    }

    // ── Prompt ────────────────────────────────────────────────────────────

    private fun buildPrompt(rawText: String): String {
        val cleanedText = rawText
            .replace(Regex("(?i)(dr\\.?|doctor)\\s+[\\w\\s,]+?(hospital|clinic|centre|center)[\\w\\s,]*"), "")
            .replace(Regex("(?i)reg\\s*no[\\s:]*[\\w-]+"), "")
            .replace(Regex("(?i)patient[:\\s]+[\\w\\s]+age[:\\s]+\\d+"), "")
            .replace(Regex("(?i)(date|wt|weight)[:\\s]+[\\w\\s./-]+"), "")
            .replace(Regex("(?i)diagnosis[:\\s]+[\\w\\s,]+"), "")
            .replace(Regex("(?i)tel[:\\s]+[\\d-]+"), "")
            .trim()

        return """
You are a medical prescription parser for a Pakistani medication management app.

The text below is from a Pakistani prescription. It may contain doctor name,
patient info, diagnosis, and medicine list all mixed together.

YOUR TASK: Extract ONLY the medicines/drugs. Completely IGNORE:
- Doctor name, qualifications, hospital name, registration number, phone
- Patient name, age, weight, date
- Diagnosis text

A medicine entry has: a drug name + dose (mg/mcg/ml) + frequency abbreviation.

Return ONLY a raw JSON array. No explanation, no markdown, no backticks.

Pakistani prescription abbreviations:
OD = once daily
BD / BID = twice daily
TDS / TID = three times daily
QID = four times daily
1+0+1 = twice daily (morning and evening)
1+1+1 = three times daily
0+0+1 = once at night only
1+0+0 = once in morning only
AC = before meals
PC = after meals
HS = at bedtime
SOS = when needed / as required

Default schedule times if not explicitly stated:
Once daily:   ["08:00 AM"]
Twice daily:  ["08:00 AM", "08:00 PM"]
Three times:  ["08:00 AM", "02:00 PM", "08:00 PM"]
Four times:   ["08:00 AM", "12:00 PM", "04:00 PM", "08:00 PM"]
At bedtime:   ["09:00 PM"]
When needed:  ["08:00 AM"]

Fix obvious OCR errors in medicine names.
Capitalise first letter of each medicine name.

Return exactly this JSON schema — one object per medicine:
[
  {
    "medicineName":  "string",
    "dose":          "string e.g. 500mg",
    "form":          "tablet | capsule | syrup | inhaler | injection | drops",
    "timesPerDay":   number,
    "scheduleTimes": ["HH:MM AM/PM"],
    "duration":      "string e.g. 7 days, 2 weeks, ongoing, or empty string",
    "instructions":  "string e.g. after meals, or empty string",
    "quantity":      number,
    "confidence":    number between 0.0 and 1.0
  }
]

Prescription text:
$cleanedText
        """.trimIndent()
    }

    // ── Parse Gemini JSON response ────────────────────────────────────────

    private fun parseJsonResponse(responseText: String): List<ParsedMedicine> {
        val cleaned = responseText
            .replace(Regex("```json\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("```\\s*"), "")
            .trim()

        val jsonArray = try {
            JSONArray(cleaned)
        } catch (e: Exception) {
            val start = cleaned.indexOf('[')
            val end   = cleaned.lastIndexOf(']')
            if (start != -1 && end != -1 && end > start) {
                JSONArray(cleaned.substring(start, end + 1))
            } else {
                throw Exception("Gemini response is not valid JSON: ${e.message}")
            }
        }

        val medicines = mutableListOf<ParsedMedicine>()

        for (i in 0 until jsonArray.length()) {
            val obj          = jsonArray.getJSONObject(i)
            val medicineName = obj.optString("medicineName", "").trim()
            if (medicineName.isBlank()) continue

            val scheduleTimes = mutableListOf<String>()
            val timesArr      = obj.optJSONArray("scheduleTimes")
            if (timesArr != null) {
                for (j in 0 until timesArr.length()) scheduleTimes.add(timesArr.getString(j))
            }

            val timesPerDay = obj.optInt("timesPerDay", 1).coerceAtLeast(1)

            val verificationStatus =
                if (YOLO_CLASSES.contains(medicineName.lowercase()))
                    VerificationStatus.YOLO_VERIFIED
                else
                    VerificationStatus.ENROLLMENT_PENDING

            medicines.add(
                ParsedMedicine(
                    medicineName       = medicineName,
                    dose               = obj.optString("dose", "").trim(),
                    form               = obj.optString("form", "tablet").trim().lowercase(),
                    timesPerDay        = timesPerDay,
                    scheduleTimes      = scheduleTimes.ifEmpty { defaultScheduleTimes(timesPerDay) },
                    duration           = obj.optString("duration", "").trim(),
                    instructions       = obj.optString("instructions", "").trim(),
                    quantity           = obj.optInt("quantity", 1).coerceAtLeast(1),
                    confidence         = obj.optDouble("confidence", 0.75).toFloat().coerceIn(0f, 1f),
                    verificationStatus = verificationStatus
                )
            )
        }

        if (medicines.isEmpty()) throw Exception("Gemini found no medicines in this text")
        return medicines
    }

    private fun defaultScheduleTimes(timesPerDay: Int): List<String> = when (timesPerDay) {
        1    -> listOf("08:00 AM")
        2    -> listOf("08:00 AM", "08:00 PM")
        3    -> listOf("08:00 AM", "02:00 PM", "08:00 PM")
        4    -> listOf("08:00 AM", "12:00 PM", "04:00 PM", "08:00 PM")
        else -> listOf("08:00 AM")
    }
}