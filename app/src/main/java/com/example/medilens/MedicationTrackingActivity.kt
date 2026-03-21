package com.example.medilens

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.util.Locale

class MedicationTrackingActivity : AppCompatActivity() {

    private lateinit var toolbar:           MaterialToolbar
    private lateinit var tvTaskTitle:       TextView
    private lateinit var llMedicationsList: LinearLayout
    private lateinit var btnLetsStart:      MaterialButton
    private lateinit var db:               AppDatabase

    private var tts: TextToSpeech? = null
    private val TAG = "MedicationTracking"

    companion object {
        const val EXTRA_SCHEDULE_TITLE   = "schedule_title"
        const val EXTRA_SCHEDULE_TIME    = "schedule_time"
        const val EXTRA_PRESCRIPTION_IDS = "prescription_ids"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medication_tracking)

        db = AppDatabase.getDatabase(this)

        toolbar           = findViewById(R.id.toolbar)
        tvTaskTitle       = findViewById(R.id.tvTaskTitle)
        llMedicationsList = findViewById(R.id.llMedicationsList)
        btnLetsStart      = findViewById(R.id.btnLetsStart)

        toolbar.setNavigationOnClickListener { finish() }

        val scheduleTitle   = intent.getStringExtra(EXTRA_SCHEDULE_TITLE) ?: "Taking medication"
        val prescriptionIds = intent.getLongArrayExtra(EXTRA_PRESCRIPTION_IDS)?.toList() ?: emptyList()

        tvTaskTitle.text = "Taking ${extractLabel(scheduleTitle)}"

        loadMedicationsAndSpeak(prescriptionIds)

        btnLetsStart.setOnClickListener {
            // Stop TTS when user proceeds to camera
            tts?.stop()
            val intent = Intent(this, PhotoCaptureActivity::class.java).apply {
                putExtra(PhotoCaptureActivity.EXTRA_SCHEDULE_TITLE, tvTaskTitle.text.toString())
                putExtra(PhotoCaptureActivity.EXTRA_SCHEDULE_TIME,  intent.getStringExtra(EXTRA_SCHEDULE_TIME))
                putExtra(PhotoCaptureActivity.EXTRA_PRESCRIPTION_IDS, intent.getLongArrayExtra(EXTRA_PRESCRIPTION_IDS))
            }
            startActivity(intent)
        }
    }

    private fun extractLabel(fullTitle: String): String {
        return fullTitle.split(" - ").firstOrNull() ?: fullTitle
    }

    private fun loadMedicationsAndSpeak(prescriptionIds: List<Long>) {
        lifecycleScope.launch {
            db.prescriptionDao().getAllPrescriptions().collect { allPrescriptions ->
                val medications = allPrescriptions.filter { it.id in prescriptionIds }
                displayMedications(medications)

                // Build and speak the medicine list
                if (medications.isNotEmpty()) {
                    speakMedicineList(medications)
                }
            }
        }
    }

    // ── Speak medicine list when screen opens ─────────────────────────────
    private fun speakMedicineList(medications: List<PrescriptionEntity>) {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {

                // Build English message
                val medicineListEnglish = medications.joinToString(" and ") { prescription ->
                    "${prescription.dosageQuantity} of ${prescription.drugName}"
                }
                val englishMsg = "Please place the following medicines for verification. $medicineListEnglish."

                // Build Urdu message
                val medicineListUrdu = medications.joinToString(" aur ") { prescription ->
                    "${prescription.dosageQuantity} ${prescription.drugName}"
                }
                val urduMsg = "Abhi aapne ye dawaiyaa laini hai. $medicineListUrdu."

                // Speak English first
                tts?.setLanguage(Locale.ENGLISH)
                tts?.speak(englishMsg, TextToSpeech.QUEUE_FLUSH, null, "tracking_eng")
                Log.d(TAG, "Speaking: $englishMsg")

                // Then Urdu
                val urResult = tts?.setLanguage(Locale("ur"))
                if (urResult == TextToSpeech.LANG_MISSING_DATA ||
                    urResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.ENGLISH)
                }
                tts?.speak(urduMsg, TextToSpeech.QUEUE_ADD, null, "tracking_urdu")
                Log.d(TAG, "Speaking Urdu: $urduMsg")

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onError(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == "tracking_urdu") {
                            tts?.shutdown()
                            tts = null
                            Log.d(TAG, "TTS completed")
                        }
                    }
                })

            } else {
                Log.e(TAG, "TTS init failed: $status")
                tts?.shutdown()
                tts = null
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private fun displayMedications(medications: List<PrescriptionEntity>) {
        llMedicationsList.removeAllViews()
        medications.forEach { prescription ->
            llMedicationsList.addView(createMedicationItemView(prescription))
        }
    }

    private fun createMedicationItemView(prescription: PrescriptionEntity): View {
        val itemView = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }
            orientation = LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER_VERTICAL
        }

        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(28, 28).apply { marginEnd = 16 }
            setImageResource(R.drawable.ic_pill)
            setColorFilter(Color.parseColor("#6200EE"))
        }

        val drugNameText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            text = prescription.drugName
            setTextColor(Color.parseColor("#1A1A1A"))
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val quantityText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                100, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text    = extractQuantity(prescription.dosageQuantity)
            setTextColor(Color.parseColor("#1A1A1A"))
            textSize = 18f
            gravity  = android.view.Gravity.END
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        itemView.addView(icon)
        itemView.addView(drugNameText)
        itemView.addView(quantityText)
        return itemView
    }

    private fun extractQuantity(dosageQuantity: String): String {
        val regex = Regex("(\\d+)")
        return regex.find(dosageQuantity)?.value ?: "1"
    }
}