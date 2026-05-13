package com.example.medilens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64                          // ← Android built-in, works from API 1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Stage 2A — handles image prescriptions only.
 * Sends a Base64-encoded image to the HuggingFace Space running the Donut
 * medical-prescription-ocr model and returns the raw extracted text.
 *
 * Uses android.util.Base64 (API 1+) — NOT java.util.Base64 (API 26+).
 */
object DonutApiClient {

    private const val SPACE_URL = "https://fahad1175-medilens-ocr.hf.space/extract"

    // Maximum image dimension — keeps payload manageable
    private const val MAX_IMAGE_DIM = 1024

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)   // Donut can be slow on free tier
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Extracts text from a prescription image URI.
     * Runs on IO dispatcher — safe to call from a coroutine.
     */
    suspend fun extractText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap    = loadAndResize(context, uri)
        val base64Img = encodeToBase64(bitmap)

        val payload = JSONObject().apply { put("image", base64Img) }
        val body    = payload.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(SPACE_URL)
            .post(body)
            .addHeader("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Donut server returned HTTP ${response.code}. Check your Space URL.")
        }

        val responseBody = response.body?.string()
            ?: throw Exception("Empty response from Donut server")

        val json = JSONObject(responseBody)

        if (!json.optBoolean("success", false)) {
            throw Exception("Donut error: ${json.optString("error", "unknown")}")
        }

        val text = json.optString("text", "").trim()
        if (text.isEmpty()) throw Exception("Donut returned empty text")

        text
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun loadAndResize(context: Context, uri: Uri): Bitmap {
        val stream   = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot open image file")
        val original = BitmapFactory.decodeStream(stream)
            ?: throw Exception("Cannot decode image, unsupported format")
        stream.close()

        val w = original.width
        val h = original.height
        if (w <= MAX_IMAGE_DIM && h <= MAX_IMAGE_DIM) return original

        val scale = MAX_IMAGE_DIM.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(
            original,
            (w * scale).toInt(),
            (h * scale).toInt(),
            true
        )
    }

    private fun encodeToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        // android.util.Base64.NO_WRAP = no line breaks, clean string for JSON
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}