package com.example.medilens

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EscalationReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_PRESCRIPTION_ID = "esc_prescription_id"
        const val EXTRA_DRUG_NAME       = "esc_drug_name"
        const val EXTRA_DOSAGE          = "esc_dosage"
        const val EXTRA_SCHEDULED_TIME  = "esc_scheduled_time"
        const val EXTRA_STEP            = "esc_step"
        // step 1 = 8:20 reminder, step 2 = 8:40 reminder, step 3 = window closed (missed)

        const val STEP_FIRST_ESCALATION  = 1
        const val STEP_SECOND_ESCALATION = 2
        const val STEP_WINDOW_CLOSED     = 3

        private const val TAG            = "EscalationReceiver"
        private const val CHANNEL_ID     = "medication_escalation"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (FirebaseAuth.getInstance().currentUser == null) return

        val prescriptionId = intent.getLongExtra(EXTRA_PRESCRIPTION_ID, -1L)
        val drugName       = intent.getStringExtra(EXTRA_DRUG_NAME)      ?: "your medication"
        val dosage         = intent.getStringExtra(EXTRA_DOSAGE)         ?: "1 tablet"
        val scheduledTime  = intent.getStringExtra(EXTRA_SCHEDULED_TIME) ?: ""
        val step           = intent.getIntExtra(EXTRA_STEP, STEP_FIRST_ESCALATION)

        Log.d(TAG, "Escalation step=$step for $drugName (id=$prescriptionId)")

        // Check if this dose was already taken — if yes, do nothing
        CoroutineScope(Dispatchers.IO).launch {
            val db          = AppDatabase.getDatabase(context)
            val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date())

            val alreadyTaken = db.medicationLogDao().isTaskCompleted(
                prescriptionId, scheduledTime, currentDate
            ) != null

            if (alreadyTaken) {
                Log.d(TAG, "Dose already taken for $drugName — skipping escalation step=$step")
                return@launch
            }

            when (step) {
                STEP_FIRST_ESCALATION -> {
                    showEscalationNotification(
                        context, prescriptionId, drugName, dosage, scheduledTime,
                        title   = "Still not taken: $drugName",
                        message = "You missed your $scheduledTime dose. Take $dosage of $drugName now.",
                        notifId = "$prescriptionId$scheduledTime-esc1".hashCode()
                    )
                    speakEscalation(context, drugName, dosage, step)
                }

                STEP_SECOND_ESCALATION -> {
                    showEscalationNotification(
                        context, prescriptionId, drugName, dosage, scheduledTime,
                        title   = "⚠️ Last chance: $drugName",
                        message = "Final reminder — take $dosage of $drugName now or it will be marked missed.",
                        notifId = "$prescriptionId$scheduledTime-esc2".hashCode()
                    )
                    speakEscalation(context, drugName, dosage, step)
                }

                STEP_WINDOW_CLOSED -> {
                    val repo = AdherenceRepository(context)
                    repo.recordMissed(prescriptionId)
                    val updatedScore = repo.getScore(prescriptionId)
                    updatedScore?.let {
                        // scheduledTime ab pass ho raha hai
                        CaregiverAlertManager.checkAndAlert(context, it, drugName, scheduledTime)
                    }
                    Log.d(TAG, "Window closed for $drugName — marked MISSED")
                }
            }
        }
    }

    private fun showEscalationNotification(
        context: Context,
        prescriptionId: Long,
        drugName: String,
        dosage: String,
        scheduledTime: String,
        title: String,
        message: String,
        notifId: Int
    ) {
        // Create notification channel
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID, "Escalation Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Follow-up reminders for missed doses"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val tapIntent = Intent(context, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("from_reminder", true)
            putExtra("reminder_medication_name", drugName)
            putExtra("reminder_medication_time", scheduledTime)
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context, notifId, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val largeIcon = BitmapFactory.decodeResource(context.resources, R.mipmap.logo)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pill)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setContentIntent(tapPendingIntent)
            .setColor(0xFFE53935.toInt())
            .build()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }
    }

    private fun speakEscalation(context: Context, drugName: String, dosage: String, step: Int) {
        val tts = MediLensTTS(context)
        val urduMessage = if (step == STEP_FIRST_ESCALATION) {
            "توجہ کریں۔ آپ نے ابھی تک $drugName نہیں لی۔ $dosage ابھی لے لیں۔"
        } else {
            "آخری یاد دہانی۔ $drugName لینا ضروری ہے۔ ابھی لے لیں، ورنہ دوائی چھوٹ جائے گی۔"
        }
        tts.speakBoth(
            "Attention. Please take $dosage of $drugName now.",
            urduMessage
        )
    }
}