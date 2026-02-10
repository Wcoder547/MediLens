package com.example.medilens

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
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

    private var isValid = false
    private var scheduleTime = ""
    private var prescriptionIds: List<Long> = emptyList()

    companion object {
        const val EXTRA_SCHEDULE_TITLE = "schedule_title"
        const val EXTRA_SCHEDULE_TIME = "schedule_time"
        const val EXTRA_PRESCRIPTION_IDS = "prescription_ids"
        const val EXTRA_IMAGE_URI = "image_uri"
        const val EXTRA_IS_VALID = "is_valid"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification_results)

        // Initialize database
        db = AppDatabase.getDatabase(this)

        // Initialize views
        toolbar = findViewById(R.id.toolbar)
        tvTitle = findViewById(R.id.tvTitle)
        llStatusMessage = findViewById(R.id.llStatusMessage)
        ivStatusIcon = findViewById(R.id.ivStatusIcon)
        tvStatusTitle = findViewById(R.id.tvStatusTitle)
        tvStatusDescription = findViewById(R.id.tvStatusDescription)
        llAddMissingSection = findViewById(R.id.llAddMissingSection)
        llMissingPillsList = findViewById(R.id.llMissingPillsList)
        llRemoveIncorrectSection = findViewById(R.id.llRemoveIncorrectSection)
        rvIncorrectPills = findViewById(R.id.rvIncorrectPills)
        cvResultImage = findViewById(R.id.cvResultImage)
        ivResultImage = findViewById(R.id.ivResultImage)
        btnPrevious = findViewById(R.id.btnPrevious)
        btnFinish = findViewById(R.id.btnFinish)

        // Setup toolbar
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // Get data from intent
        val scheduleTitle = intent.getStringExtra(EXTRA_SCHEDULE_TITLE) ?: "Taking medication"
        scheduleTime = intent.getStringExtra(EXTRA_SCHEDULE_TIME) ?: ""
        prescriptionIds = intent.getLongArrayExtra(EXTRA_PRESCRIPTION_IDS)?.toList() ?: emptyList()
        val imageUri = intent.getStringExtra(EXTRA_IMAGE_URI)
        isValid = intent.getBooleanExtra(EXTRA_IS_VALID, false)

        tvTitle.text = extractLabel(scheduleTitle)

        // Load result image
        imageUri?.let {
            ivResultImage.setImageURI(android.net.Uri.parse(it))
        }

        // Show dummy verification results
        if (isValid) {
            showValidResult()
        } else {
            showInvalidResult()
        }

        // Setup buttons
        btnPrevious.setOnClickListener {
            finish()
        }

        btnFinish.setOnClickListener {
            if (isValid) {
                // Mark task as completed
                markTaskAsCompleted()

                Toast.makeText(this, "Medication verified successfully!", Toast.LENGTH_SHORT).show()

                // Navigate back to home and clear the stack
                val intent = Intent(this, HomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Please correct the errors first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun extractLabel(fullTitle: String): String {
        return fullTitle.split(" - ").firstOrNull() ?: fullTitle
    }

    private fun markTaskAsCompleted() {
        lifecycleScope.launch {
            val currentDate = getCurrentDate()

            prescriptionIds.forEach { prescriptionId ->
                val log = MedicationLogEntity(
                    prescriptionId = prescriptionId,
                    scheduledTime = scheduleTime,
                    completedAt = System.currentTimeMillis(),
                    completedDate = currentDate
                )
                db.medicationLogDao().insertLog(log)
            }
        }
    }

    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }

    private fun showValidResult() {
        // Show success message
        llStatusMessage.visibility = View.VISIBLE
        ivStatusIcon.setImageResource(R.drawable.ic_success)
        ivStatusIcon.setColorFilter(Color.parseColor("#4CAF50"))
        tvStatusTitle.text = "Medication valid!"
        tvStatusTitle.setTextColor(Color.parseColor("#4CAF50"))
        tvStatusDescription.text = "All medications are correctly placed. You can proceed to take them."

        // Hide error sections
        llAddMissingSection.visibility = View.GONE
        llRemoveIncorrectSection.visibility = View.GONE

        // Enable finish button
        btnFinish.isEnabled = true
        btnFinish.backgroundTintList = ContextCompat.getColorStateList(this, R.color.purple_700)
    }

    private fun showInvalidResult() {
        // Show error message
        llStatusMessage.visibility = View.VISIBLE
        ivStatusIcon.setImageResource(R.drawable.ic_error)
        ivStatusIcon.setColorFilter(Color.parseColor("#F44336"))
        tvStatusTitle.text = "Medication invalid."
        tvStatusTitle.setTextColor(Color.parseColor("#F44336"))
        tvStatusDescription.text = "Please correct the following to take your prescription. Valid pills are outlined in green while invalid pills are outlined in red."

        // Show dummy missing pills
        llAddMissingSection.visibility = View.VISIBLE
        addDummyMissingPills()

        // Show dummy incorrect pills
        llRemoveIncorrectSection.visibility = View.VISIBLE
        setupIncorrectPillsGrid()

        // Disable finish button (user needs to correct)
        btnFinish.isEnabled = false
        btnFinish.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.darker_gray)
    }

    private fun addDummyMissingPills() {
        // Dummy data - replace with actual missing pills from YOLO detection
        val missingPills = listOf(
            Pair("Stalevo, 125MG", "1"),
            Pair("Teva-Cloxacillin, 250mg", "1")
        )

        llMissingPillsList.removeAllViews()

        missingPills.forEach { (name, quantity) ->
            val itemView = createMissingPillItem(name, quantity)
            llMissingPillsList.addView(itemView)
        }
    }

    private fun createMissingPillItem(name: String, quantity: String): View {
        val itemView = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // Icon
        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(24, 24).apply {
                marginEnd = 12
            }
            setImageResource(R.drawable.ic_pill)
            setColorFilter(Color.parseColor("#6200EE"))
        }

        // Drug name
        val nameText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            text = name
            setTextColor(Color.parseColor("#1A1A1A"))
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        // Quantity
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

    private fun setupIncorrectPillsGrid() {
        // Dummy incorrect pills images - replace with actual detected incorrect pills
        val incorrectPillsImages = listOf(
            R.drawable.ic_pill, // Replace with actual pill images
            R.drawable.ic_pill,
            R.drawable.ic_pill
        )

        rvIncorrectPills.layoutManager = GridLayoutManager(this, 2)
        // TODO: Create adapter for incorrect pills grid
        // For now, just set visibility
        rvIncorrectPills.visibility = View.VISIBLE
    }
}
