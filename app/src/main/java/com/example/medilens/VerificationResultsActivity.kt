package com.example.medilens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
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
import android.graphics.BitmapFactory
import android.util.Log

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
    private lateinit var tts: MediLensTTS


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

        tts = MediLensTTS(this)


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
            val uri = Uri.parse(imageUri)

            // ── Step 1: Decode the raw bitmap from URI (no rotation yet) ──────
            val rawBitmap = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: run {
                Log.e("BBoxDebug", "Failed to decode bitmap from URI")
                return
            }

            // ── Step 2: Apply EXACT same rotation logic as correctImageRotation() ──
            // correctImageRotation() reads EXIF from bytes, but the URI still has
            // original EXIF. We must mirror the EXACT same decision tree.
            val exifOrient = contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

            val matrix = Matrix()
            var rotationApplied = false

            when (exifOrient) {
                ExifInterface.ORIENTATION_ROTATE_90 -> {
                    matrix.postRotate(90f); rotationApplied = true
                }
                ExifInterface.ORIENTATION_ROTATE_180 -> {
                    matrix.postRotate(180f); rotationApplied = true
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> {
                    matrix.postRotate(270f); rotationApplied = true
                }
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                    matrix.preScale(-1f, 1f); rotationApplied = true
                }
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                    matrix.preScale(1f, -1f); rotationApplied = true
                }
                else -> {
                    // Mirror the else branch in correctImageRotation():
                    // if width > height (landscape), rotate 90°
                    if (rawBitmap.width > rawBitmap.height) {
                        matrix.postRotate(90f); rotationApplied = true
                    }
                    // else: no rotation — portrait image is already correct
                }
            }

            val displayBitmap = if (rotationApplied) {
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            } else {
                rawBitmap
            }

            // ── Step 3: Create mutable copy for drawing ───────────────────────
            val mutable = displayBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas  = Canvas(mutable)

            // ── Step 4: Scale factors ─────────────────────────────────────────
            // roboflowW/H = dimensions of the EXIF-corrected bitmap used during detection
            // displayBitmap = same rotation applied → should match roboflowW/H
            val scaleX = mutable.width.toFloat() / roboflowW
            val scaleY = mutable.height.toFloat() / roboflowH

            Log.d("BBoxDebug", "mutable=${mutable.width}x${mutable.height} " +
                    "roboflow=${roboflowW}x${roboflowH} " +
                    "scale=($scaleX, $scaleY) " +
                    "pills=${pills.size} " +
                    "exifOrient=$exifOrient " +
                    "rotationApplied=$rotationApplied")

            // Show image even if no pills (so user sees their photo)
            ivResultImage.setImageBitmap(mutable)

            if (pills.isEmpty()) return

            // ── Step 5: Draw boxes ────────────────────────────────────────────
            val strokeWidth = (mutable.width * 0.006f).coerceAtLeast(4f)
            val textSize    = (mutable.width * 0.035f).coerceAtLeast(24f)

            val greenPaint = Paint().apply {
                color = Color.parseColor("#4CAF50")
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                isAntiAlias = true
            }
            val redPaint = Paint().apply {
                color = Color.parseColor("#F44336")
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                isAntiAlias = true
            }
            val bgPaint = Paint().apply {
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val textPaint = Paint().apply {
                color = Color.WHITE
                this.textSize = textSize
                isFakeBoldText = true
                isAntiAlias = true
            }

            pills.forEach { pill ->
                val isCorrect = prescribedDrugs.isEmpty() || prescribedDrugs.any { prescribed ->
                    pill.className.contains(prescribed, ignoreCase = true) ||
                            prescribed.contains(pill.className, ignoreCase = true)
                }
                val boxPaint   = if (isCorrect) greenPaint else redPaint
                val labelColor = if (isCorrect) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")

                // pill.x/y are CENTER coords in roboflowW×roboflowH space
                val left   = (pill.x - pill.width  / 2f) * scaleX
                val top    = (pill.y - pill.height / 2f) * scaleY
                val right  = (pill.x + pill.width  / 2f) * scaleX
                val bottom = (pill.y + pill.height / 2f) * scaleY

                Log.d("BBoxDebug", "pill=${pill.className} center=(${pill.x},${pill.y}) " +
                        "size=${pill.width}x${pill.height} → " +
                        "box=($left,$top,$right,$bottom)")

                canvas.drawRect(RectF(left, top, right, bottom), boxPaint)

                val label   = "${pill.className} ${(pill.confidence * 100).toInt()}%"
                val textW   = textPaint.measureText(label)
                val padding = textSize * 0.3f
                val labelTop = (top - textSize - padding * 2).coerceAtLeast(0f)

                bgPaint.color = labelColor
                canvas.drawRect(RectF(left, labelTop, left + textW + padding * 2, top), bgPaint)
                canvas.drawText(label, left + padding, top - padding, textPaint)
            }

            // Redraw with boxes
            ivResultImage.setImageBitmap(mutable)

        } catch (e: Exception) {
            Log.e("BBoxDebug", "drawBoundingBoxes crashed: ${e.message}", e)
            try { ivResultImage.setImageURI(Uri.parse(imageUri)) } catch (_: Exception) {}
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
        speak(
            "No pills detected. Please place the medicine clearly and try again",
            "Koi da-waa detect nahi hui. do-ba-ra koshish karein"
        )

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
        speak(
            "All medications are correct. You can take them now",
            "Tamaam da-waa theek hain. Aap ab yay le sak-tay ho"
        )

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

        // 🔹 Count detected pills
        val detectedCountMap = mutableMapOf<String, Int>()
        allDetected.forEach { name ->
            val key = name.lowercase()
            detectedCountMap[key] = (detectedCountMap[key] ?: 0) + 1
        }

        lifecycleScope.launch {
            val allPrescriptions = db.prescriptionDao().getAllPrescriptions().first()
            val scheduledMeds = allPrescriptions.filter { it.id in prescriptionIds }

            // 🔹 Expected quantities from DB
            val expectedMap = mutableMapOf<String, Int>()
            scheduledMeds.forEach {
                val name = it.drugName.lowercase()
                val qty = Regex("\\d+").find(it.dosageQuantity)?.value?.toIntOrNull() ?: 1
                expectedMap[name] = (expectedMap[name] ?: 0) + qty
            }

            val missingList = mutableListOf<String>()
            val extraList   = mutableListOf<String>()

            // Step 1 — Check if expected medicines are present in correct quantity
            for ((med, expectedQty) in expectedMap) {
                val detectedQty = detectedCountMap.entries
                    .filter { (detectedName, _) ->
                        detectedName.contains(med, ignoreCase = true) ||
                                med.contains(detectedName, ignoreCase = true)
                    }
                    .sumOf { it.value }

                when {
                    detectedQty == 0 ->
                        missingList.add("$med (missing)")
                    detectedQty < expectedQty ->
                        missingList.add("$med (missing ${expectedQty - detectedQty} more)")
                    detectedQty > expectedQty ->
                        extraList.add(
                            "$med: found $detectedQty but only $expectedQty required " +
                                    "(remove ${detectedQty - expectedQty})"
                        )
                }
            }

            // Step 2 — Check if detected medicines are NOT in prescription (unexpected pills)
            for ((detectedName, detectedQty) in detectedCountMap) {
                val isExpected = expectedMap.keys.any { expectedMed ->
                    detectedName.contains(expectedMed, ignoreCase = true) ||
                            expectedMed.contains(detectedName, ignoreCase = true)
                }
                if (!isExpected) {
                    extraList.add("$detectedName (not in prescription)")
                }
            }

            if (allDetected.isEmpty()) {
                speak(
                    "No pills found in the image",
                    "Tas-veer mein koi da-waa nahi mili"
                )
            }

            when {
                // ── All correct, right quantities ─────────────────────────────
                missingList.isEmpty() && extraList.isEmpty() -> {
                    showValidResult(pillName, confidence, pillCount)
                }

                // ── Correct medicines present but extra pills also detected ───
                missingList.isEmpty() && extraList.isNotEmpty() -> {
                    btnFinish.text = "Finish"
                    ivStatusIcon.setImageResource(R.drawable.ic_success)
                    ivStatusIcon.setColorFilter(Color.parseColor("#FF9800")) // orange warning
                    tvStatusTitle.text = "Extra pills detected! ⚠️"
                    tvStatusTitle.setTextColor(Color.parseColor("#FF9800"))

                    val extraStr = extraList.joinToString(", ")
                    tvStatusDescription.text =
                        "Your required medicines are all present ✅\n\n" +
                                "Extra detected: $extraStr\n\n" +
                                "❌ Red boxes = extra/unexpected pills — do NOT take these\n\n" +
                                "Remove the extra pills and then tap Finish."

                    speak(
                        "Your required medicines are correct. However, you have extra pills. " +
                                "Extra detected: $extraStr. " +
                                "Do not take the medicines shown in red boxes.",
                        "Aap ki zaroori da-waa theek hain. Lekin kuch extra da-waa bhi detect hui hain. " +
                                "Extra dawaein: $extraStr. " +
                                "Laal box wali da-waa na lo."
                    )

                    llAddMissingSection.visibility      = View.GONE
                    llRemoveIncorrectSection.visibility = View.GONE
                    // Allow finish since required medicines ARE present
                    setFinishEnabled(true)
                }

                // ── Missing medicines ─────────────────────────────────────────
                missingList.isNotEmpty() && extraList.isEmpty() -> {
                    btnFinish.text = "Finish"
                    ivStatusIcon.setImageResource(R.drawable.ic_error)
                    ivStatusIcon.setColorFilter(Color.parseColor("#F44336"))
                    tvStatusTitle.text = "Missing medicines! ❌"
                    tvStatusTitle.setTextColor(Color.parseColor("#F44336"))

                    val friendlyMissing = missingList.joinToString(", ") { getTtsFriendlyMedicineName(it) }
                    tvStatusDescription.text =
                        "Missing: $friendlyMissing\n\n" +
                                "Please add the missing medicines and retake the photo."

                    speak(
                        "Some medicines are missing: $friendlyMissing. " +
                                "Please add the missing medicines and retake the photo.",
                        "Kuch da-waa missing hain: $friendlyMissing " +
                                " da-waa rakh kar do-ba-ra tasveer lo"
                    )

                    llAddMissingSection.visibility = View.VISIBLE
                    llMissingPillsList.removeAllViews()
                    missingList.forEach { item ->
                        llMissingPillsList.addView(createMissingPillItem(item, ""))
                    }
                    llRemoveIncorrectSection.visibility = View.GONE
                    setFinishEnabled(false)
                }

                // ── Both missing and extra ────────────────────────────────────
                missingList.isNotEmpty() && extraList.isNotEmpty() -> {
                    btnFinish.text = "Finish"
                    ivStatusIcon.setImageResource(R.drawable.ic_error)
                    ivStatusIcon.setColorFilter(Color.parseColor("#F44336"))
                    tvStatusTitle.text = "Medication issue detected! ❌"
                    tvStatusTitle.setTextColor(Color.parseColor("#F44336"))

                    val friendlyMissing = missingList.joinToString(", ") { getTtsFriendlyMedicineName(it) }
                    val friendlyExtra   = extraList.joinToString(", ")   { getTtsFriendlyMedicineName(it) }
                    tvStatusDescription.text =
                        "Missing: $friendlyMissing\n\n" +
                                "Extra detected: $friendlyExtra\n\n" +
                                "❌ Red boxes = wrong/extra pills — do NOT take these\n\n" +
                                "Please fix your medication and retake the photo."

                    speak(
                        "Some medicines are missing: $friendlyMissing. " +
                                "Also, extra medicines were detected. " +
                                "Do not take the medicines shown in red boxes.",
                        "Kuch da-waa missing hain: $friendlyExtra." +
                                "Aur kuch extra da-waa bhi detect hui hain. " +
                                "Laal box wali da-waa bil-kul na lo"
                    )

                    llAddMissingSection.visibility = View.VISIBLE
                    llMissingPillsList.removeAllViews()
                    missingList.forEach { item ->
                        llMissingPillsList.addView(createMissingPillItem(item, ""))
                    }
                    llRemoveIncorrectSection.visibility = View.GONE
                    setFinishEnabled(false)
                }
            }
        }
    }


    private fun setFinishEnabled(enabled: Boolean) {
        btnFinish.isEnabled = enabled
        btnFinish.backgroundTintList = ContextCompat.getColorStateList(
            this, if (enabled) R.color.purple_700 else android.R.color.darker_gray
        )
    }

    private fun speak(messageEn: String, messageUr: String) {
        tts.speakMessage("$messageEn. $messageUr")
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

    fun getTtsFriendlyMedicineName(name: String): String {
        return name.lowercase().replace("panadol", "Pana-do-l")
            .replace("risek", "Ra-e-sik")
            .replace("myteka", "My-tee-kaa")
            .replace("ventolin", "Ven-to-lin")
        // Add more rules if needed
    }


    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
    }

}