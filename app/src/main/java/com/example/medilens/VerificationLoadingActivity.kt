package com.example.medilens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
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
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VerificationLoadingActivity : AppCompatActivity() {

    private lateinit var btnClose: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView

    companion object {
        private const val TAG = "VerificationLoading"
        private lateinit var tts: MediLensTTS

        const val EXTRA_SCHEDULE_TITLE   = "schedule_title"
        const val EXTRA_SCHEDULE_TIME    = "schedule_time"
        const val EXTRA_PRESCRIPTION_IDS = "prescription_ids"
        const val EXTRA_IMAGE_URI        = "image_uri"

        const val EXTRA_PILL_NAME        = "pill_name"
        const val EXTRA_CONFIDENCE       = "confidence"
        const val EXTRA_PILL_COUNT       = "pill_count"
        const val EXTRA_ALL_DETECTED     = "all_detected"
        const val EXTRA_NO_DETECTION     = "no_detection"
        const val EXTRA_DETECTED_PILLS_JSON = "detected_pills_json"

        private const val API_KEY = "tboC49f87cK9XGbo5tbm"
        private const val API_URL =
            "https://serverless.roboflow.com/panadol-pill-detection/11" +
                    "?api_key=$API_KEY&confidence=40&overlap=30"
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
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
                    val raw = contentResolver.openInputStream(Uri.parse(imageUriString))?.use { it.readBytes() }
                    raw?.let { correctImageRotation(it) }
                }

                if (bytes == null || bytes.isEmpty()) {
                    anim.cancel()
                    sendToResults(scheduleTitle, scheduleTime, prescriptionIds,
                        imageUriString, noDetection = true)
                    return@launch
                }

                val result = withContext(Dispatchers.IO) { callRoboflow(bytes) }
                anim.cancel()

                Log.d(TAG, "Sending to results: noDetection=${result.noDetection}, pill=${result.pillName}")

                sendToResults(
                    scheduleTitle, scheduleTime, prescriptionIds, imageUriString,
                    pillName      = result.pillName,
                    confidence    = result.confidence,
                    pillCount     = result.pillCount,
                    allDetected   = result.allDetected,
                    noDetection   = result.noDetection,
                    detectedPills = result.detectedPills,
                    imageWidth    = result.imageWidth,
                    imageHeight   = result.imageHeight
                )

            } catch (e: Exception) {
                anim.cancel()
                Log.e(TAG, "Detection failed: ${e.message}", e)
                withContext(Dispatchers.Main) { tvStatus.text = "⚠️ ${e.message}" }
                delay(5000)
                sendToResults(scheduleTitle, scheduleTime, prescriptionIds,
                    imageUriString, noDetection = true)
            }
        }
    }

    private fun correctImageRotation(bytes: ByteArray): ByteArray {
        return try {
            // Decode the bitmap first
            val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return bytes

            // Read EXIF orientation
            val exif = ExifInterface(bytes.inputStream())
            val orient = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            // Build rotation matrix
            val matrix = Matrix()
            when (orient) {
                ExifInterface.ORIENTATION_ROTATE_90  -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL   -> matrix.preScale(1f, -1f)
                else -> {
                    // Even if EXIF says normal, check if image is landscape
                    // when it should be portrait — cameras often lie
                    if (original.width > original.height) {
                        // Image is landscape but was likely taken in portrait
                        matrix.postRotate(90f)
                    } else {
                        return bytes // truly no rotation needed
                    }
                }
            }

            val rotated = Bitmap.createBitmap(
                original, 0, 0, original.width, original.height, matrix, true
            )

            val stream = java.io.ByteArrayOutputStream()
            rotated.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            stream.toByteArray()

        } catch (e: Exception) {
            Log.e(TAG, "Rotation fix failed: ${e.message}")
            bytes
        }
    }

    private fun callRoboflow(bytes: ByteArray): ApiResult {
        val base64String = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val postData     = base64String.toByteArray(Charsets.UTF_8)
        val body         = postData.toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())

        val req = Request.Builder()
            .url(API_URL)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .post(body)
            .build()

        val resp         = http.newCall(req).execute()
        val responseBody = resp.body?.string() ?: throw Exception("Empty response from Roboflow")

        Log.d(TAG, "HTTP ${resp.code} | $responseBody")
        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: $responseBody")

        return parseJson(responseBody)
    }

    private fun parseJson(json: String): ApiResult {
        return try {
            val root        = JSONObject(json)
            val imageWidth  = root.optJSONObject("image")?.optDouble("width",  640.0)?.toFloat() ?: 640f
            val imageHeight = root.optJSONObject("image")?.optDouble("height", 640.0)?.toFloat() ?: 640f
            val preds = root.optJSONArray("predictions")
                ?: root.optJSONObject("outputs")?.optJSONArray("predictions")
                ?: root.optJSONObject("response")?.optJSONArray("predictions")

            if (preds == null || preds.length() == 0) {
                Log.d(TAG, "No predictions. JSON: $json")
                return ApiResult(noDetection = true)
            }

            val all          = mutableListOf<String>()
            val pills        = mutableListOf<DetectedPill>()
            var bestName     = ""
            var bestConf     = 0.0

            for (i in 0 until preds.length()) {
                val p    = preds.getJSONObject(i)
                val name = p.optString("class", p.optString("label", "unknown"))
                val conf = p.optDouble("confidence", 0.0)

                if (name.isNotEmpty() && name != "unknown") all.add(name)
                if (conf > bestConf) { bestConf = conf; bestName = name }

                pills.add(DetectedPill(
                    className  = name,
                    confidence = conf.toFloat(),
                    x          = p.optDouble("x", 0.0).toFloat(),
                    y          = p.optDouble("y", 0.0).toFloat(),
                    width      = p.optDouble("width", 0.0).toFloat(),
                    height     = p.optDouble("height", 0.0).toFloat()
                ))
            }

            if (all.isEmpty()) return ApiResult(noDetection = true)

            Log.d(TAG, "✅ Detected: $bestName @ ${(bestConf * 100).toInt()}% | all=$all")

            ApiResult(
                pillName      = bestName,
                confidence    = (bestConf * 100).toFloat(),
                pillCount     = preds.length(),
                allDetected   = all.distinct(),
                noDetection   = false,
                detectedPills = pills,
                imageWidth    = imageWidth,
                imageHeight   = imageHeight
            )

        } catch (e: Exception) {
            Log.e(TAG, "parseJson error. JSON: $json", e)
            throw Exception("Parse error: ${e.message}")
        }
    }

    private fun sendToResults(
        scheduleTitle: String,
        scheduleTime: String,
        prescriptionIds: LongArray?,
        imageUriString: String?,
        pillName: String           = "",
        confidence: Float          = 0f,
        pillCount: Int             = 0,
        allDetected: List<String>  = emptyList(),
        noDetection: Boolean       = false,
        detectedPills: List<DetectedPill> = emptyList(),
        imageWidth:  Float         = 640f,
        imageHeight: Float         = 640f
    ) {
        // Serialize detected pills to JSON string for intent
        val pillsJson = JSONArray().apply {
            detectedPills.forEach { pill ->
                put(JSONObject().apply {
                    put("class",      pill.className)
                    put("confidence", pill.confidence)
                    put("x",          pill.x)
                    put("y",          pill.y)
                    put("width",      pill.width)
                    put("height",     pill.height)
                })
            }
        }.toString()

        startActivity(Intent(this, VerificationResultsActivity::class.java).apply {
            putExtra(VerificationResultsActivity.EXTRA_SCHEDULE_TITLE,   scheduleTitle)
            putExtra(VerificationResultsActivity.EXTRA_SCHEDULE_TIME,    scheduleTime)
            putExtra(VerificationResultsActivity.EXTRA_PRESCRIPTION_IDS, prescriptionIds)
            putExtra(VerificationResultsActivity.EXTRA_IMAGE_URI,        imageUriString)
            putExtra(EXTRA_PILL_NAME,             pillName)
            putExtra(EXTRA_CONFIDENCE,            confidence)
            putExtra(EXTRA_PILL_COUNT,            pillCount)
            putExtra(EXTRA_ALL_DETECTED,          allDetected.toTypedArray())
            putExtra(EXTRA_NO_DETECTION,          noDetection)
            putExtra(EXTRA_DETECTED_PILLS_JSON,   pillsJson)
            putExtra("roboflow_image_width",      imageWidth)
            putExtra("roboflow_image_height",     imageHeight)
        })
        finish()
    }

    data class DetectedPill(
        val className:  String,
        val confidence: Float,
        val x:          Float,
        val y:          Float,
        val width:      Float,
        val height:     Float
    )

    data class ApiResult(
        val pillName:      String             = "",
        val confidence:    Float              = 0f,
        val pillCount:     Int                = 0,
        val allDetected:   List<String>       = emptyList(),
        val noDetection:   Boolean            = true,
        val detectedPills: List<DetectedPill> = emptyList(),
        val imageWidth:    Float              = 640f,
        val imageHeight:   Float              = 640f
    )
}