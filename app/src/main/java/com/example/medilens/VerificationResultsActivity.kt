package com.example.medilens

import android.content.Intent
import android.graphics.Color
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
import java.text.SimpleDateFormat
import java.util.*

class VerificationResultsActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var tvTitle: TextView
    private lateinit var llStatusMessage: LinearLayout
    private lateinit var ivStatusIcon: ImageView
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvStatusDescription: TextView
    private lateinit var llAddMissingSection: LinearLayout
    private lateinit var llMissingPillsList: LinearLayout
    private lateinit var llRemoveIncorrectSection: LinearLayout
    private lateinit var rvIncorrectPills: RecyclerView
    private lateinit var cvResultImage: MaterialCardView
    private lateinit var ivResultImage: ImageView
    private lateinit var btnPrevious: MaterialButton
    private lateinit var btnFinish: MaterialButton
    private lateinit var db: AppDatabase

    private var scheduleTime = ""
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

        toolbar.setNavigationOnClickListener { finish() }

        // Read all intent data
        val scheduleTitle   = intent.getStringExtra(EXTRA_SCHEDULE_TITLE) ?: "Taking medication"
        scheduleTime        = intent.getStringExtra(EXTRA_SCHEDULE_TIME) ?: ""
        prescriptionIds     = intent.getLongArrayExtra(EXTRA_PRESCRIPTION_IDS)?.toList() ?: emptyList()
        val imageUri        = intent.getStringExtra(EXTRA_IMAGE_URI)

        // Read AI results
        val pillName    = intent.getStringExtra(VerificationLoadingActivity.EXTRA_PILL_NAME) ?: ""
        val confidence  = intent.getFloatExtra(VerificationLoadingActivity.EXTRA_CONFIDENCE, 0f)
        val pillCount   = intent.getIntExtra(VerificationLoadingActivity.EXTRA_PILL_COUNT, 0)
        val allDetected = intent.getStringArrayExtra(VerificationLoadingActivity.EXTRA_ALL_DETECTED)?.toList() ?: emptyList()
        val noDetection = intent.getBooleanExtra(VerificationLoadingActivity.EXTRA_NO_DETECTION, false)

        tvTitle.text = cleanTitle(scheduleTitle)

        // Show captured image
        imageUri?.let { ivResultImage.setImageURI(Uri.parse(it)) }

        // Start in disabled state
        setFinishEnabled(false)

        lifecycleScope.launch {
            val allPrescriptions = db.prescriptionDao().getAllPrescriptions().first()
            val scheduledMeds    = allPrescriptions.filter { it.id in prescriptionIds }
            val prescribedDrugs  = scheduledMeds.map { it.drugName }

            when {
                noDetection -> showNoDetection()

                // Safety: if no prescriptions found, still treat detected pill as valid
                prescribedDrugs.isEmpty() -> showValidResult(
                    pillName, confidence, pillCount,
                    "No prescription linked — pill detected and accepted."
                )

                else -> buildResult(pillName, confidence, pillCount, allDetected, prescribedDrugs)
            }
        }

        btnPrevious.setOnClickListener { finish() }

        btnFinish.setOnClickListener {
            markTaskAsCompleted()
            Toast.makeText(this, "✅ Medication logged!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }
    }

    private fun cleanTitle(title: String): String {
        val base = title.split(" - ").firstOrNull() ?: title
        return if (base.startsWith("Taking Taking ", ignoreCase = true))
            base.replaceFirst("Taking Taking ", "Taking ", ignoreCase = true)
        else base
    }

    // ── No Detection ──────────────────────────────────────────────────────
    private fun showNoDetection() {
        llStatusMessage.visibility          = View.VISIBLE
        llAddMissingSection.visibility      = View.GONE
        llRemoveIncorrectSection.visibility = View.GONE

        ivStatusIcon.setImageResource(R.drawable.ic_error)
        ivStatusIcon.setColorFilter(Color.parseColor("#FF9800")) // orange warning, not hard red
        tvStatusTitle.text = "No pills detected."
        tvStatusTitle.setTextColor(Color.parseColor("#FF9800"))
        tvStatusDescription.text =
            "We couldn't clearly identify a pill in the photo.\n\n" +
                    "Tip: Place the pill flat on a white surface in good lighting.\n\n" +
                    "If you've already taken your medication correctly, tap " +
                    "\"Log Manually\" to record this dose."

        // ✅ KEY FIX: Allow user to manually log even when detection fails
        btnFinish.text = "Log Manually"
        setFinishEnabled(true)
    }

    // ── Valid Result ──────────────────────────────────────────────────────
    private fun showValidResult(pillName: String, confidence: Float, pillCount: Int, note: String = "") {
        llStatusMessage.visibility          = View.VISIBLE
        llAddMissingSection.visibility      = View.GONE
        llRemoveIncorrectSection.visibility = View.GONE

        // Reset button label in case user retried after a no-detection
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

    // ── Build Result from AI + Prescription comparison ────────────────────
    private fun buildResult(
        pillName: String,
        confidence: Float,
        pillCount: Int,
        allDetected: List<String>,
        prescribedDrugs: List<String>
    ) {
        llStatusMessage.visibility = View.VISIBLE

        // Case-insensitive partial match: "panadol" matches "Panadol 500mg" etc.
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
            tvStatusTitle.text = "Medication invalid."
            tvStatusTitle.setTextColor(Color.parseColor("#F44336"))
            tvStatusDescription.text =
                "Detected: ${if (allDetected.isEmpty()) "unknown" else allDetected.joinToString(", ")}\n" +
                        "Expected: ${prescribedDrugs.joinToString(", ")}\n\n" +
                        "Please check your medication and retake the photo."

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

    // ── Enable / disable Finish button ────────────────────────────────────
    private fun setFinishEnabled(enabled: Boolean) {
        btnFinish.isEnabled = enabled
        btnFinish.backgroundTintList = ContextCompat.getColorStateList(
            this,
            if (enabled) R.color.purple_700 else android.R.color.darker_gray
        )
    }

    // ── Save log to Room + SharedPrefs so HomeTab can hide button today ───
    private fun markTaskAsCompleted() {
        lifecycleScope.launch {
            val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            // 1. Write Room log
            prescriptionIds.forEach { prescriptionId ->
                db.medicationLogDao().insertLog(
                    MedicationLogEntity(
                        prescriptionId = prescriptionId,
                        scheduledTime  = scheduleTime,
                        completedAt    = System.currentTimeMillis(),
                        completedDate  = currentDate
                    )
                )
            }

            // 2. Mark in SharedPreferences so HomeActivity hides this task today
            //    Key format: "done_<date>_<scheduleTime>"  e.g. "done_2026-03-09_08:00"
            val prefs = getSharedPreferences("medilens_prefs", MODE_PRIVATE)
            val key   = "done_${currentDate}_${scheduleTime}"
            prefs.edit().putBoolean(key, true).apply()
        }
    }

    private fun createMissingPillItem(name: String, quantity: String): View {
        val itemView = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
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