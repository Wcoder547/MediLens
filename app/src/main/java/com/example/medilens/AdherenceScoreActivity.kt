package com.example.medilens

import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdherenceScoreActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_adherence_score)

        val prescriptionId = intent.getLongExtra("prescription_id", -1L)
        if (prescriptionId == -1L) { finish(); return }

        loadScore(prescriptionId)
    }

    private fun loadScore(prescriptionId: Long) {
        lifecycleScope.launch {
            val score = withContext(Dispatchers.IO) {
                AdherenceRepository(applicationContext).getScore(prescriptionId)
            }
            score?.let { updateUI(it) }
        }
    }

    private fun updateUI(score: AdherenceScoreEntity) {
        val tvScore     = findViewById<TextView>(R.id.tvScoreNumber)
        val tvGrade     = findViewById<TextView>(R.id.tvScoreGrade)
        val progressBar = findViewById<ProgressBar>(R.id.scoreProgressBar)
        val tvStreak    = findViewById<TextView>(R.id.tvStreak)
        val tvMisses    = findViewById<TextView>(R.id.tvMisses)
        val tvAdvice    = findViewById<TextView>(R.id.tvAdvice)

        tvScore.text     = "${score.score}/100"
        progressBar.progress = score.score.coerceIn(0, 100)

        val (grade, color, advice) = when {
            score.score >= 80 -> Triple("Excellent ⭐", 0xFF2E7D32.toInt(),
                "Good! you are taking medicine on time.")
            score.score >= 60 -> Triple("Good 👍",     0xFF1565C0.toInt(),
                "Okay, but some doses are getting missed.")
            score.score >= 40 -> Triple("Fair ⚠️",     0xFFF57F17.toInt(),
                "Doses are being missed. Consult a doctor..")
            else              -> Triple("Poor 🚨",     0xFFB71C1C.toInt(),
                "Too many doses are being missed! Contact a doctor immediately.")
        }

        tvGrade.text = grade
        tvGrade.setTextColor(color)
        tvScore.setTextColor(color)
        tvAdvice.text  = advice
        tvStreak.text  = "🔥 Streak: ${score.streak} din"
        tvMisses.text  = "❌ Missed in past 7 days: ${score.missCountLast7}"
    }
}