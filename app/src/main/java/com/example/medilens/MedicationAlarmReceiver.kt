package com.example.medilens

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

class MedicationAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_MEDICATION_NAME   = "medication_name"
        const val EXTRA_MEDICATION_TIME   = "medication_time"
        const val EXTRA_MEDICATION_DOSAGE = "medication_dosage"
        const val EXTRA_IS_ALARM          = "is_alarm"
        const val EXTRA_PRESCRIPTION_ID   = "prescription_id"
        const val CHANNEL_ID_REMINDER     = "medication_reminder"
        const val CHANNEL_ID_ALARM        = "medication_alarm"
        private const val TAG             = "MedicationAlarmReceiver"

        // Escalation timing
        private const val ESCALATION_1_DELAY_MS = 20 * 60 * 1000L   // +20 min
        private const val ESCALATION_2_DELAY_MS = 40 * 60 * 1000L   // +40 min
        private const val WINDOW_CLOSE_DELAY_MS  = 60 * 60 * 1000L  // +60 min
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (FirebaseAuth.getInstance().currentUser == null) {
            Log.d(TAG, "No user logged in — ignoring alarm")
            return
        }

        val medicationName   = intent.getStringExtra(EXTRA_MEDICATION_NAME)   ?: "your medication"
        val medicationTime   = intent.getStringExtra(EXTRA_MEDICATION_TIME)   ?: ""
        val medicationDosage = intent.getStringExtra(EXTRA_MEDICATION_DOSAGE) ?: "1 tablet"
        val isAlarm          = intent.getBooleanExtra(EXTRA_IS_ALARM, false)
        val prescriptionId   = intent.getLongExtra(EXTRA_PRESCRIPTION_ID, -1L)

        Log.d(TAG, "onReceive: name=$medicationName time=$medicationTime isAlarm=$isAlarm")

        if (isAlarm && prescriptionId != -1L) {
            // Score-based: decide SOFT vs HARD for the exact-time alarm
            CoroutineScope(Dispatchers.IO).launch {
                val repo     = AdherenceRepository(context)
                val score    = repo.getScore(prescriptionId)
                val decision = ReminderDecisionEngine.decide(score)

                if (decision == ReminderDecisionEngine.ReminderType.HARD) {
                    Log.d(TAG, "HARD reminder — score=${score?.score}")
                    showAlarmNotification(context, medicationName, medicationDosage, medicationTime, prescriptionId)
                } else {
                    Log.d(TAG, "SOFT reminder — score=${score?.score}")
                    showReminderNotification(context, medicationName, medicationDosage, medicationTime, prescriptionId)
                }

                // Schedule escalation chain ONLY for exact-time alarms
                scheduleEscalationChain(context, prescriptionId, medicationName, medicationDosage, medicationTime)
            }
        } else {
            // -5 min pre-reminder — always soft, no escalation
            showReminderNotification(context, medicationName, medicationDosage, medicationTime, prescriptionId)
        }

        // Reschedule for tomorrow
        rescheduleForTomorrow(context, medicationName, medicationDosage, medicationTime, isAlarm, prescriptionId)

        // TTS — only when not in foreground
        if (!isAppInForeground(context)) {
            val ttsHelper = MediLensTTS(context)
            ttsHelper.speak(medicationName, medicationDosage, medicationTime, isAlarm)
        } else {
            Log.d(TAG, "App in foreground — skipping TTS")
        }
    }

    // ── Escalation chain scheduling ───────────────────────────────────────
    private fun scheduleEscalationChain(
        context: Context,
        prescriptionId: Long,
        drugName: String,
        dosage: String,
        scheduledTime: String
    ) {
        val now          = System.currentTimeMillis()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        listOf(
            Triple(ESCALATION_1_DELAY_MS,  EscalationReceiver.STEP_FIRST_ESCALATION,  "esc1"),
            Triple(ESCALATION_2_DELAY_MS,  EscalationReceiver.STEP_SECOND_ESCALATION, "esc2"),
            Triple(WINDOW_CLOSE_DELAY_MS,  EscalationReceiver.STEP_WINDOW_CLOSED,     "esc3")
        ).forEach { (delayMs, step, tag) ->
            val triggerAt = now + delayMs
            val escIntent = Intent(context, EscalationReceiver::class.java).apply {
                putExtra(EscalationReceiver.EXTRA_PRESCRIPTION_ID, prescriptionId)
                putExtra(EscalationReceiver.EXTRA_DRUG_NAME,       drugName)
                putExtra(EscalationReceiver.EXTRA_DOSAGE,          dosage)
                putExtra(EscalationReceiver.EXTRA_SCHEDULED_TIME,  scheduledTime)
                putExtra(EscalationReceiver.EXTRA_STEP,            step)
            }
            val requestCode = (prescriptionId.toString() + scheduledTime + tag).hashCode()
            val pi = PendingIntent.getBroadcast(
                context, requestCode, escIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, pi), pi)
            Log.d(TAG, "Escalation step=$step scheduled for $drugName at ${Date(triggerAt)}")
        }
    }

    // ── Cancel escalation chain (call this when user takes the dose) ──────
    fun cancelEscalationChain(
        context: Context,
        prescriptionId: Long,
        scheduledTime: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        listOf("esc1", "esc2", "esc3").forEach { tag ->
            val escIntent   = Intent(context, EscalationReceiver::class.java)
            val requestCode = (prescriptionId.toString() + scheduledTime + tag).hashCode()
            val pi = PendingIntent.getBroadcast(
                context, requestCode, escIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            pi?.let {
                alarmManager.cancel(it)
                it.cancel()
                Log.d(TAG, "Cancelled escalation $tag for prescriptionId=$prescriptionId")
            }
        }
    }

    // ── Reschedule for tomorrow ───────────────────────────────────────────
    private fun rescheduleForTomorrow(
        context: Context,
        medicationName: String,
        medicationDosage: String,
        time: String,
        isAlarm: Boolean,
        prescriptionId: Long
    ) {
        if (prescriptionId == -1L) return

        val tomorrowCalendar = MedicationAlarmManager.parseTime(time)
        tomorrowCalendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
        if (!isAlarm) tomorrowCalendar.add(java.util.Calendar.MINUTE, -5)

        val newIntent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra(EXTRA_MEDICATION_NAME,   medicationName)
            putExtra(EXTRA_MEDICATION_TIME,   time)
            putExtra(EXTRA_MEDICATION_DOSAGE, medicationDosage)
            putExtra(EXTRA_IS_ALARM,          isAlarm)
            putExtra(EXTRA_PRESCRIPTION_ID,   prescriptionId)
        }

        val requestCode = if (isAlarm)
            (prescriptionId.toString() + time + "alarm").hashCode()
        else
            (prescriptionId.toString() + time + "reminder").hashCode()

        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, newIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(tomorrowCalendar.timeInMillis, pendingIntent),
            pendingIntent
        )
        Log.d(TAG, "Rescheduled ${if (isAlarm) "ALARM" else "REMINDER"} for $medicationName tomorrow")
    }

    // ── Foreground check ──────────────────────────────────────────────────
    private fun isAppInForeground(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return am.runningAppProcesses?.any {
            it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                    it.processName == context.packageName
        } ?: false
    }

    // ── Notifications ─────────────────────────────────────────────────────
    private fun showReminderNotification(
        context: Context,
        medicationName: String,
        dosage: String,
        time: String,
        prescriptionId: Long = -1L
    ) {
        createNotificationChannel(context, CHANNEL_ID_REMINDER, "Medication Reminders",
            NotificationManager.IMPORTANCE_HIGH)

        val tapIntent = Intent(context, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("from_reminder", true)
            putExtra("reminder_medication_name", medicationName)
            putExtra("reminder_medication_time", time)
        }
        val tapPi = PendingIntent.getActivity(
            context, medicationName.hashCode(), tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_REMINDER)
            .setSmallIcon(R.drawable.ic_pill)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.logo))
            .setContentTitle("MediLens Upcoming Medication")
            .setContentText("In 5 minutes: Take $dosage of $medicationName")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "In 5 minutes you need to take:\n$dosage of $medicationName\nScheduled at $time"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(tapPi)
            .setColor(0xFF6200EE.toInt())
            .build()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(medicationName.hashCode(), notification)
        }
    }

    private fun showAlarmNotification(
        context: Context,
        medicationName: String,
        dosage: String,
        time: String,
        prescriptionId: Long = -1L
    ) {
        createNotificationChannel(context, CHANNEL_ID_ALARM, "Medication Alarms",
            NotificationManager.IMPORTANCE_HIGH, withSound = true)

        val tapIntent = Intent(context, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("from_reminder", false)
            putExtra("reminder_medication_name", medicationName)
            putExtra("reminder_medication_time", time)
        }
        val tapPi = PendingIntent.getActivity(
            context, medicationName.hashCode() + 1000, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALARM)
            .setSmallIcon(R.drawable.ic_pill)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.logo))
            .setContentTitle("⏰ Time to take $medicationName!")
            .setContentText("Take $dosage of $medicationName now")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "⏰ It is time to take your medication!\nTake $dosage of $medicationName\nScheduled at $time"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            .setContentIntent(tapPi)
            .setFullScreenIntent(tapPi, true)
            .setColor(0xFF6200EE.toInt())
            .build()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(medicationName.hashCode() + 1000, notification)
        }
    }

    private fun createNotificationChannel(
        context: Context,
        channelId: String,
        channelName: String,
        importance: Int,
        withSound: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Notifications for medication reminders and alarms"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                if (withSound) {
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}