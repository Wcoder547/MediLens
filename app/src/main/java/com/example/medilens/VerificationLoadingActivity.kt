package com.example.medilens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VerificationLoadingActivity : AppCompatActivity() {

    private lateinit var btnClose: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView

    companion object {
        private const val TAG = "VerificationLoading"

        const val EXTRA_SCHEDULE_TITLE   = "schedule_title"
        const val EXTRA_SCHEDULE_TIME    = "schedule_time"
        const val EXTRA_PRESCRIPTION_IDS = "prescription_ids"
        const val EXTRA_IMAGE_URI        = "image_uri"

        const val EXTRA_PILL_NAME        = "pill_name"
        const val EXTRA_CONFIDENCE       = "confidence"
        const val EXTRA_PILL_COUNT       = "pill_count"
        const val EXTRA_ALL_DETECTED     = "all_detected"
        const val EXTRA_NO_DETECTION     = "no_detection"

        private const val API_KEY = "tboC49f87cK9XGbo5tbm"
        private const val PROJECT = "panadol-pill-detection"
        private const val VERSION = 6
        // FIX 1: confidence=15 (was 40) — catches lower-confidence real detections
        private const val API_URL =
            "https://detect.roboflow.com/$PROJECT/$VERSION" +
                    "?api_key=$API_KEY&confidence=15&overlap=30"
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val steps = listOf(
        "Analyzing pill shape...",
        "Detecting color & imprint...",
        "Running YOLOv11 model...",
        "Matching against database...",
        "Verifying prescription...",
        "Preparing results..."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification_loading)

        btnClose = findViewById(R.id.btnClose)
        tvTitle  = findViewById(R.id.tvTitle)
        tvStatus = findViewById(R.id.tvStatus)

        val scheduleTitle   = intent.getStringExtra(EXTRA_SCHEDULE_TITLE) ?: "Taking medication"
        val scheduleTime    = intent.getStringExtra(EXTRA_SCHEDULE_TIME) ?: ""
        val prescriptionIds = intent.getLongArrayExtra(EXTRA_PRESCRIPTION_IDS)
        val imageUriString  = intent.getStringExtra(EXTRA_IMAGE_URI)

        // FIX 2: collapse "Taking Taking X" → "Taking X"
        tvTitle.text = cleanTitle(scheduleTitle)

        btnClose.setOnClickListener { finish() }

        if (imageUriString == null) {
            sendToResults(scheduleTitle, scheduleTime, prescriptionIds, null, noDetection = true)
            return
        }

        runDetection(scheduleTitle, scheduleTime, prescriptionIds, imageUriString)
    }

    private fun cleanTitle(title: String): String {
        val base = title.split(" - ").firstOrNull() ?: title
        return if (base.startsWith("Taking Taking ", ignoreCase = true))
            base.replaceFirst("Taking Taking ", "Taking ", ignoreCase = true)
        else base
    }

    private fun runDetection(
        scheduleTitle: String,
        scheduleTime: String,
        prescriptionIds: LongArray?,
        imageUriString: String
    ) {
        lifecycleScope.launch {
            val anim = launch {
                steps.forEach { step -> tvStatus.text = step; delay(600) }
                while (true) { tvStatus.text = "Almost done..."; delay(800) }
            }

            try {
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(Uri.parse(imageUriString))?.use { it.readBytes() }
                }

                if (bytes == null || bytes.isEmpty()) {
                    anim.cancel()
                    sendToResults(scheduleTitle, scheduleTime, prescriptionIds,
                        imageUriString, noDetection = true)
                    return@launch
                }

                val result = withContext(Dispatchers.IO) { callRoboflow(bytes) }
                anim.cancel()

                sendToResults(
                    scheduleTitle, scheduleTime, prescriptionIds, imageUriString,
                    pillName    = result.pillName,
                    confidence  = result.confidence,
                    pillCount   = result.pillCount,
                    allDetected = result.allDetected,
                    noDetection = result.noDetection
                )

            } catch (e: Exception) {
                anim.cancel()
                Log.e(TAG, "Detection failed", e)
                tvStatus.text = "⚠️ ${e.message}"
                delay(2000)
                sendToResults(scheduleTitle, scheduleTime, prescriptionIds,
                    imageUriString, noDetection = true)
            }
        }
    }

    private fun callRoboflow(bytes: ByteArray): ApiResult {
        // FIX 3: NO_WRAP removes newlines — Base64.DEFAULT adds \n every 76 chars
        // which breaks Roboflow's parser and causes "Invalid base64 input" error
        val base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val body = base64Image.toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())
        val req  = Request.Builder().url(API_URL).post(body).build()
        val resp = http.newCall(req).execute()

        if (!resp.isSuccessful)
            throw Exception("Roboflow error ${resp.code}: ${resp.body?.string()}")

        val json = resp.body?.string() ?: throw Exception("Empty response")
        Log.d(TAG, "Roboflow JSON: $json")
        return parseJson(json)
    }

    private fun parseJson(json: String): ApiResult {
        val preds = JSONObject(json).optJSONArray("predictions")
        if (preds == null || preds.length() == 0) return ApiResult(noDetection = true)

        val all = mutableListOf<String>()
        var bestName = ""; var bestConf = 0.0

        for (i in 0 until preds.length()) {
            val p    = preds.getJSONObject(i)
            val name = p.getString("class")
            val conf = p.getDouble("confidence")
            all.add(name)
            if (conf > bestConf) { bestConf = conf; bestName = name }
        }

        return ApiResult(bestName, (bestConf * 100).toFloat(), preds.length(), all.distinct())
    }

    private fun sendToResults(
        scheduleTitle: String,
        scheduleTime: String,
        prescriptionIds: LongArray?,
        imageUriString: String?,
        pillName: String          = "",
        confidence: Float         = 0f,
        pillCount: Int            = 0,
        allDetected: List<String> = emptyList(),
        noDetection: Boolean      = false
    ) {
        startActivity(Intent(this, VerificationResultsActivity::class.java).apply {
            putExtra(VerificationResultsActivity.EXTRA_SCHEDULE_TITLE,   scheduleTitle)
            putExtra(VerificationResultsActivity.EXTRA_SCHEDULE_TIME,    scheduleTime)
            putExtra(VerificationResultsActivity.EXTRA_PRESCRIPTION_IDS, prescriptionIds)
            putExtra(VerificationResultsActivity.EXTRA_IMAGE_URI,        imageUriString)
            putExtra(EXTRA_PILL_NAME,    pillName)
            putExtra(EXTRA_CONFIDENCE,   confidence)
            putExtra(EXTRA_PILL_COUNT,   pillCount)
            putExtra(EXTRA_ALL_DETECTED, allDetected.toTypedArray())
            putExtra(EXTRA_NO_DETECTION, noDetection)
        })
        finish()
    }

    data class ApiResult(
        val pillName: String          = "",
        val confidence: Float         = 0f,
        val pillCount: Int            = 0,
        val allDetected: List<String> = emptyList(),
        val noDetection: Boolean      = true
    )
}