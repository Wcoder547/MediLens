package com.example.medilens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface

class VerificationResultsActivity : AppCompatActivity() {

    private lateinit var toolbar:                  MaterialToolbar
    private lateinit var tvTitle:                  TextView
    private lateinit var llStatusMessage:          LinearLayout
    private lateinit var ivStatusIcon:             ImageView
    private lateinit var tvStatusTitle:            TextView
    private lateinit var tvStatusDescription:      TextView
    private lateinit var llAddMissingSection:      LinearLayout
    private lateinit var llMissingPillsList:       LinearLayout
    private lateinit var llRemoveIncorrectSection: LinearLayout
    private lateinit var rvIncorrectPills:         RecyclerView
    private lateinit var cvResultImage:            MaterialCardView
    private lateinit var ivResultImage:            ImageView
    private lateinit var btnPrevious:              MaterialButton
    private lateinit var btnFinish:                MaterialButton
    private lateinit var db:                       AppDatabase

    private var scheduleTime    = ""
    private var prescriptionIds: List<Long> = emptyList()

    companion object {
        const val EXTRA_SCHEDULE_TITLE   = "schedule_title"
        const val EXTRA_SCHEDULE_TIME    = "schedule_time"
        const val EXTRA_PRESCRIPTION_IDS = "prescription_ids"
        const val EXTRA_IMAGE_URI        = "image_uri"
        const val EXTRA_IS_VALID         = "is_valid"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification_results)

        db = AppDatabase.getDatabase(this)

        toolbar                  = findViewById(R.id.toolbar)
        tvTitle                  = findViewById(R.id.tvTitle)
        llStatusMessage          = findViewById(R.id.llStatusMessage)
        ivStatusIcon             = findViewById(R.id.ivStatusIcon)
        tvStatusTitle            = findViewById(R.id.tvStatusTitle)
        tvStatusDescription      = findViewById(R.id.tvStatusDescription)
        llAddMissingSection      = findViewById(R.id.llAddMissingSection)
        llMissingPillsList       = findViewById(R.id.llMissingPillsList)
        llRemoveIncorrectSection = findViewById(R.id.llRemoveIncorrectSection)
        rvIncorrectPills         = findViewById(R.id.rvIncorrectPills)
        cvResultImage            = findViewById(R.id.cvResultImage)
        ivResultImage            = findViewById(R.id.ivResultImage)
        btnPrevious              = findViewById(R.id.btnPrevious)
        btnFinish                = findViewById(R.id.btnFinish)

        // IMPORTANT: must be fitCenter so coordinates map correctly
        ivResultImage.scaleType = ImageView.ScaleType.FIT_CENTER

        toolbar.setNavigationOnClickListener { finish() }

        val scheduleTitle   = intent.getStringExtra(EXTRA_SCHEDULE_TITLE) ?: "Taking medication"
        scheduleTime        = intent.getStringExtra(EXTRA_SCHEDULE_TIME) ?: ""
        prescriptionIds     = intent.getLongArrayExtra(EXTRA_PRESCRIPTION_IDS)?.toList() ?: emptyList()
        val imageUri        = intent.getStringExtra(EXTRA_IMAGE_URI)

        val pillName        = intent.getStringExtra(VerificationLoadingActivity.EXTRA_PILL_NAME) ?: ""
        val confidence      = intent.getFloatExtra(VerificationLoadingActivity.EXTRA_CONFIDENCE, 0f)
        val pillCount       = intent.getIntExtra(VerificationLoadingActivity.EXTRA_PILL_COUNT, 0)
        val allDetected     = intent.getStringArrayExtra(VerificationLoadingActivity.EXTRA_ALL_DETECTED)?.toList() ?: emptyList()
        val noDetection     = intent.getBooleanExtra(VerificationLoadingActivity.EXTRA_NO_DETECTION, false)
        val pillsJson       = intent.getStringExtra(VerificationLoadingActivity.EXTRA_DETECTED_PILLS_JSON) ?: "[]"
        val roboflowW       = intent.getFloatExtra("roboflow_image_width",  640f)
        val roboflowH       = intent.getFloatExtra("roboflow_image_height", 640f)

        val detectedPills   = parsePillsJson(pillsJson)

        tvTitle.text = cleanTitle(scheduleTitle)
        setFinishEnabled(false)

        lifecycleScope.launch {
            val allPrescriptions = db.prescriptionDao().getAllPrescriptions().first()
            val scheduledMeds    = allPrescriptions.filter { it.id in prescriptionIds }
            val prescribedDrugs  = scheduledMeds.map { it.drugName }

            when {
                noDetection -> {
                    // Show raw image with no boxes
                    imageUri?.let { ivResultImage.setImageURI(Uri.parse(it)) }
                    showNoDetection()
                }

                prescribedDrugs.isEmpty() -> {
                    imageUri?.let {
                        drawBoundingBoxes(it, detectedPills, emptyList(), roboflowW, roboflowH)
                    }
                    showValidResult(pillName, confidence, pillCount,
                        "No prescription linked — pill detected and accepted.")
                }

                else -> {
                    imageUri?.let {
                        drawBoundingBoxes(it, detectedPills, prescribedDrugs, roboflowW, roboflowH)
                    }
                    buildResult(pillName, confidence, pillCount, allDetected, prescribedDrugs)
                }
            }
        }

        btnPrevious.setOnClickListener { finish() }

        btnFinish.setOnClickListener {
            // Pass detected pill names so only matched prescriptions are marked done
            markTaskAsCompleted(onlyDetected = allDetected)
            Toast.makeText(this, "✅ Medication logged!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }
    }

    // ── Parse pills JSON ──────────────────────────────────────────────────
    private fun parsePillsJson(json: String): List<VerificationLoadingActivity.DetectedPill> {
        return try {
            val arr   = JSONArray(json)
            val pills = mutableListOf<VerificationLoadingActivity.DetectedPill>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                pills.add(VerificationLoadingActivity.DetectedPill(
                    className  = obj.getString("class"),
                    confidence = obj.getDouble("confidence").toFloat(),
                    x          = obj.getDouble("x").toFloat(),
                    y          = obj.getDouble("y").toFloat(),
                    width      = obj.getDouble("width").toFloat(),
                    height     = obj.getDouble("height").toFloat()
                ))
            }
            pills
        } catch (e: Exception) { emptyList() }
    }

    // ── Draw bounding boxes with correct coordinate scaling ───────────────
    private fun drawBoundingBoxes(
        imageUri: String,
        pills: List<VerificationLoadingActivity.DetectedPill>,
        prescribedDrugs: List<String>,
        roboflowW: Float,
        roboflowH: Float
    ) {
        try {
            val rawBitmap = MediaStore.Images.Media.getBitmap(contentResolver, Uri.parse(imageUri))

            // Fix display rotation using EXIF
            val rotatedBitmap = try {
                val inputStream = contentResolver.openInputStream(Uri.parse(imageUri))
                val exif = inputStream?.let { ExifInterface(it) }
                val orient = exif?.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                ) ?: ExifInterface.ORIENTATION_NORMAL

                val matrix = Matrix()
                when (orient) {
                    ExifInterface.ORIENTATION_ROTATE_90  -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    else -> {
                        if (rawBitmap.width > rawBitmap.height) matrix.postRotate(90f)
                    }
                }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            } catch (e: Exception) {
                rawBitmap
            }

            val mutable = rotatedBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas  = Canvas(mutable)

            // Scale factors: Roboflow coords → actual bitmap pixel coords
            val scaleX = mutable.width.toFloat()  / roboflowW
            val scaleY = mutable.height.toFloat() / roboflowH

            val strokeWidth = (mutable.width * 0.006f).coerceAtLeast(4f)
            val textSize    = (mutable.width * 0.035f).coerceAtLeast(24f)

            val greenPaint = Paint().apply {
                color       = Color.parseColor("#4CAF50")
                style       = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                isAntiAlias = true
            }
            val redPaint = Paint().apply {
                color       = Color.parseColor("#F44336")
                style       = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                isAntiAlias = true
            }
            val bgPaint   = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
            val textPaint = Paint().apply {
                color          = Color.WHITE
                this.textSize  = textSize
                isFakeBoldText = true
                isAntiAlias    = true
            }

            pills.forEach { pill ->
                val isCorrect = prescribedDrugs.isEmpty() || prescribedDrugs.any { prescribed ->
                    pill.className.contains(prescribed, ignoreCase = true) ||
                            prescribed.contains(pill.className, ignoreCase = true)
                }

                val boxPaint   = if (isCorrect) greenPaint else redPaint
                val labelColor = if (isCorrect) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")

                // Convert center-based Roboflow coords to bitmap pixel rect
                val left   = (pill.x - pill.width  / 2f) * scaleX
                val top    = (pill.y - pill.height / 2f) * scaleY
                val right  = (pill.x + pill.width  / 2f) * scaleX
                val bottom = (pill.y + pill.height / 2f) * scaleY

                // Draw bounding box
                canvas.drawRect(RectF(left, top, right, bottom), boxPaint)

                // Draw label background + text
                val label     = "${pill.className} ${(pill.confidence * 100).toInt()}%"
                val textW     = textPaint.measureText(label)
                val padding   = textSize * 0.3f
                val labelTop  = (top - textSize - padding * 2).coerceAtLeast(0f)

                bgPaint.color = labelColor
                canvas.drawRect(
                    RectF(left, labelTop, left + textW + padding * 2, top),
                    bgPaint
                )
                canvas.drawText(label, left + padding, top - padding, textPaint)
            }

            ivResultImage.setImageBitmap(mutable)

        } catch (e: Exception) {
            // Fallback: show original image
            ivResultImage.setImageURI(Uri.parse(imageUri))
        }
    }

    private fun cleanTitle(title: String): String {
        val base = title.split(" - ").firstOrNull() ?: title
        return if (base.startsWith("Taking Taking ", ignoreCase = true))
            base.replaceFirst("Taking Taking ", "Taking ", ignoreCase = true)
        else base
    }

    private fun showNoDetection() {
        llStatusMessage.visibility          = View.VISIBLE
        llAddMissingSection.visibility      = View.GONE
        llRemoveIncorrectSection.visibility = View.GONE
        ivStatusIcon.setImageResource(R.drawable.ic_error)
        ivStatusIcon.setColorFilter(Color.parseColor("#FF9800"))
        tvStatusTitle.text = "No pills detected."
        tvStatusTitle.setTextColor(Color.parseColor("#FF9800"))
        tvStatusDescription.text =
            "We couldn't clearly identify a pill in the photo.\n\n" +
                    "Tip: Place the pill flat on a white surface in good lighting.\n\n" +
                    "If you've already taken your medication correctly, tap " +
                    "\"Log Manually\" to record this dose."
        btnFinish.text = "Log Manually"
        setFinishEnabled(true)
    }

    private fun showValidResult(pillName: String, confidence: Float, pillCount: Int, note: String = "") {
        llStatusMessage.visibility          = View.VISIBLE
        llAddMissingSection.visibility      = View.GONE
        llRemoveIncorrectSection.visibility = View.GONE
        btnFinish.text = "Finish"
        ivStatusIcon.setImageResource(R.drawable.ic_success)
        ivStatusIcon.setColorFilter(Color.parseColor("#4CAF50"))
        tvStatusTitle.text = "Medication valid! ✅"
        tvStatusTitle.setTextColor(Color.parseColor("#4CAF50"))
        tvStatusDescription.text =
            "Detected: ${pillName.replaceFirstChar { it.uppercase() }}\n" +
                    "Confidence: ${confidence.toInt()}%  •  $pillCount pill${if (pillCount != 1) "s" else ""} found\n\n" +
                    (if (note.isNotEmpty()) "$note\n\n" else "") +
                    "All medications correctly placed. Tap Finish to log this dose."
        setFinishEnabled(true)
    }

    private fun buildResult(
        pillName: String,
        confidence: Float,
        pillCount: Int,
        allDetected: List<String>,
        prescribedDrugs: List<String>
    ) {
        llStatusMessage.visibility = View.VISIBLE

        val matchedDrugs = prescribedDrugs.filter { prescribed ->
            allDetected.any { detected ->
                detected.contains(prescribed, ignoreCase = true) ||
                        prescribed.contains(detected, ignoreCase = true)
            }
        }
        val missingDrugs = prescribedDrugs.filter { it !in matchedDrugs }
        val isValid      = missingDrugs.isEmpty()

        if (isValid) {
            showValidResult(pillName, confidence, pillCount)
        } else {
            btnFinish.text = "Finish"
            ivStatusIcon.setImageResource(R.drawable.ic_error)
            ivStatusIcon.setColorFilter(Color.parseColor("#F44336"))
            tvStatusTitle.text = "Wrong medication detected!"
            tvStatusTitle.setTextColor(Color.parseColor("#F44336"))

            val detectedStr = if (allDetected.isEmpty()) "unknown" else allDetected.joinToString(", ")
            val expectedStr = prescribedDrugs.joinToString(", ")
            val missingStr  = if (missingDrugs.isEmpty()) "" else "\nMissing: ${missingDrugs.joinToString(", ")}"

            tvStatusDescription.text =
                "Detected: $detectedStr\n" +
                        "Expected: $expectedStr$missingStr\n\n" +
                        "❌ Red boxes = wrong/unexpected pills\n" +
                        "✅ Green boxes = correct pills\n\n" +
                        "Please check your medication before proceeding."

            if (missingDrugs.isNotEmpty()) {
                llAddMissingSection.visibility = View.VISIBLE
                llMissingPillsList.removeAllViews()
                missingDrugs.forEach { drug ->
                    llMissingPillsList.addView(createMissingPillItem(drug, "1"))
                }
            } else {
                llAddMissingSection.visibility = View.GONE
            }
            llRemoveIncorrectSection.visibility = View.GONE
            setFinishEnabled(false)
        }
    }

    private fun setFinishEnabled(enabled: Boolean) {
        btnFinish.isEnabled = enabled
        btnFinish.backgroundTintList = ContextCompat.getColorStateList(
            this, if (enabled) R.color.purple_700 else android.R.color.darker_gray
        )
    }

    private fun markTaskAsCompleted(onlyDetected: List<String> = emptyList()) {
        lifecycleScope.launch {
            val currentDate      = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val prefs            = getSharedPreferences("medilens_prefs", MODE_PRIVATE)
            val allPrescriptions = db.prescriptionDao().getAllPrescriptions().first()

            // scheduleTime may be "02:26 PM,02:30 PM" — split into list
            val sessionTimes = scheduleTime.split(",").map { it.trim() }

            prescriptionIds.forEach { prescriptionId ->
                val prescription = allPrescriptions.find { it.id == prescriptionId }

                val shouldMark = onlyDetected.isEmpty() || onlyDetected.any { detected ->
                    prescription?.drugName?.contains(detected, ignoreCase = true) == true ||
                            detected.contains(prescription?.drugName ?: "", ignoreCase = true)
                }

                if (shouldMark && prescription != null) {
                    // Find which of this prescription's times is in the session times
                    val prescriptionTimes = listOfNotNull(
                        prescription.time1,
                        prescription.time2,
                        prescription.time3
                    )

                    val matchingTime = prescriptionTimes.firstOrNull { time ->
                        sessionTimes.any { sessionTime ->
                            time.trim().equals(sessionTime, ignoreCase = true)
                        }
                    } ?: sessionTimes.firstOrNull() ?: scheduleTime

                    db.medicationLogDao().insertLog(
                        MedicationLogEntity(
                            prescriptionId = prescriptionId,
                            scheduledTime  = matchingTime,
                            completedAt    = System.currentTimeMillis(),
                            completedDate  = currentDate
                        )
                    )
                    prefs.edit()
                        .putBoolean("done_${currentDate}_${matchingTime}_${prescriptionId}", true)
                        .apply()
                }
            }
        }
    }

    private fun createMissingPillItem(name: String, quantity: String): View {
        val itemView = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
            orientation = LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER_VERTICAL
        }
        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(24, 24).apply { marginEnd = 12 }
            setImageResource(R.drawable.ic_pill)
            setColorFilter(Color.parseColor("#6200EE"))
        }
        val nameText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = name
            setTextColor(Color.parseColor("#1A1A1A"))
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val quantityText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(80, LinearLayout.LayoutParams.WRAP_CONTENT)
            text = quantity
            setTextColor(Color.parseColor("#1A1A1A"))
            textSize = 16f
            gravity = android.view.Gravity.END
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        itemView.addView(icon)
        itemView.addView(nameText)
        itemView.addView(quantityText)
        return itemView
    }
}