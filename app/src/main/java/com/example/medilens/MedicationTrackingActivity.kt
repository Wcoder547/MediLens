package com.example.medilens

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class MedicationTrackingActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var tvTaskTitle: TextView
    private lateinit var llMedicationsList: LinearLayout
    private lateinit var btnLetsStart: MaterialButton
    private lateinit var db: AppDatabase

    companion object {
        const val EXTRA_SCHEDULE_TITLE = "schedule_title"
        const val EXTRA_SCHEDULE_TIME = "schedule_time"
        const val EXTRA_PRESCRIPTION_IDS = "prescription_ids"


    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medication_tracking)

        db = AppDatabase.getDatabase(this)

        // Initialize views
        toolbar = findViewById(R.id.toolbar)
        tvTaskTitle = findViewById(R.id.tvTaskTitle)
        llMedicationsList = findViewById(R.id.llMedicationsList)
        btnLetsStart = findViewById(R.id.btnLetsStart)

        // Setup toolbar
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // Get data from intent
        val scheduleTitle = intent.getStringExtra(EXTRA_SCHEDULE_TITLE) ?: "Taking medication"
        val prescriptionIds = intent.getLongArrayExtra(EXTRA_PRESCRIPTION_IDS)?.toList() ?: emptyList()

        // Set title
        tvTaskTitle.text = "Taking ${extractLabel(scheduleTitle)}"

        // Load medications
        loadMedications(prescriptionIds)

        // Setup button - Navigate to Photo Capture
        btnLetsStart.setOnClickListener {
            val intent = Intent(this, PhotoCaptureActivity::class.java).apply {
                putExtra(PhotoCaptureActivity.EXTRA_SCHEDULE_TITLE, tvTaskTitle.text.toString())
                putExtra(PhotoCaptureActivity.EXTRA_SCHEDULE_TIME, intent.getStringExtra(EXTRA_SCHEDULE_TIME))
                putExtra(PhotoCaptureActivity.EXTRA_PRESCRIPTION_IDS, intent.getLongArrayExtra(EXTRA_PRESCRIPTION_IDS))
            }
            startActivity(intent)
        }

    }

    private fun extractLabel(fullTitle: String): String {
        // Extract "Morning medicine" from "Morning medicine - 12:00 PM"
        return fullTitle.split(" - ").firstOrNull() ?: fullTitle
    }

    private fun loadMedications(prescriptionIds: List<Long>) {
        lifecycleScope.launch {
            db.prescriptionDao().getAllPrescriptions().collect { allPrescriptions ->
                val medications = allPrescriptions.filter { it.id in prescriptionIds }
                displayMedications(medications)
            }
        }
    }

    private fun displayMedications(medications: List<PrescriptionEntity>) {
        llMedicationsList.removeAllViews()

        medications.forEach { prescription ->
            val medicationItemView = createMedicationItemView(prescription)
            llMedicationsList.addView(medicationItemView)
        }
    }

    private fun createMedicationItemView(prescription: PrescriptionEntity): View {
        val itemView = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 20
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // Icon
        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(28, 28).apply {
                marginEnd = 16
            }
            setImageResource(R.drawable.ic_pill)
            setColorFilter(Color.parseColor("#6200EE"))
        }

        // Drug name only (no dosage below)
        val drugNameText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            text = prescription.drugName
            setTextColor(Color.parseColor("#1A1A1A"))
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        // Quantity
        val quantityText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(100, LinearLayout.LayoutParams.WRAP_CONTENT)
            text = extractQuantity(prescription.dosageQuantity)
            setTextColor(Color.parseColor("#1A1A1A"))
            textSize = 18f
            gravity = android.view.Gravity.END
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        itemView.addView(icon)
        itemView.addView(drugNameText)
        itemView.addView(quantityText)

        return itemView
    }

    private fun extractQuantity(dosageQuantity: String): String {
        // Extract number from strings like "1 pill", "2 pills", "1 tablet"
        val regex = Regex("(\\d+)")
        val match = regex.find(dosageQuantity)
        return match?.value ?: "1"
    }
}
