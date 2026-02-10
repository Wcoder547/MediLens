package com.example.medilens

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class VerificationLoadingActivity : AppCompatActivity() {

    private lateinit var btnClose: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var btnPrevious: MaterialButton
    private lateinit var btnFinish: MaterialButton

    companion object {
        const val EXTRA_SCHEDULE_TITLE = "schedule_title"
        const val EXTRA_SCHEDULE_TIME = "schedule_time"
        const val EXTRA_PRESCRIPTION_IDS = "prescription_ids"
        const val EXTRA_IMAGE_URI = "image_uri"
        private const val LOADING_DURATION = 3000L
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification_loading)

        // Initialize views
        btnClose = findViewById(R.id.btnClose)
        tvTitle = findViewById(R.id.tvTitle)
        btnPrevious = findViewById(R.id.btnPrevious)
        btnFinish = findViewById(R.id.btnFinish)

        // Get title from intent
        val scheduleTitle = intent.getStringExtra(EXTRA_SCHEDULE_TITLE) ?: "Taking medication"
        tvTitle.text = extractLabel(scheduleTitle)

        // Setup close button
        btnClose.setOnClickListener {
            finish()
        }

        // Simulate verification process
        Handler(Looper.getMainLooper()).postDelayed({
            onVerificationComplete()
        }, LOADING_DURATION)
    }

    private fun extractLabel(fullTitle: String): String {
        return fullTitle.split(" - ").firstOrNull() ?: fullTitle
    }

    private fun onVerificationComplete() {
        val isValid = (0..1).random() == 1

        val intent = Intent(this, VerificationResultsActivity::class.java).apply {
            putExtra(VerificationResultsActivity.EXTRA_SCHEDULE_TITLE, tvTitle.text.toString())
            putExtra(VerificationResultsActivity.EXTRA_SCHEDULE_TIME, intent.getStringExtra(EXTRA_SCHEDULE_TIME))
            putExtra(VerificationResultsActivity.EXTRA_PRESCRIPTION_IDS, intent.getLongArrayExtra(EXTRA_PRESCRIPTION_IDS))
            putExtra(VerificationResultsActivity.EXTRA_IMAGE_URI, intent.getStringExtra(EXTRA_IMAGE_URI))
            putExtra(VerificationResultsActivity.EXTRA_IS_VALID, isValid)
        }
        startActivity(intent)
        finish()
    }


}
