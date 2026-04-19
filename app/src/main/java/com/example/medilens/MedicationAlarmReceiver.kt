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
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

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
    }

    override fun onReceive(context: Context, intent: Intent) {
        // ── GUARD: skip if no user is logged in ───────────────────────────────
        if (FirebaseAuth.getInstance().currentUser == null) {
            Log.d(TAG, "No user logged in — ignoring alarm")
            return
        }
        // ─────────────────────────────────────────────────────────────────────
        val medicationName   = intent.getStringExtra(EXTRA_MEDICATION_NAME)   ?: "your medication"
        val medicationTime   = intent.getStringExtra(EXTRA_MEDICATION_TIME)   ?: ""
        val medicationDosage = intent.getStringExtra(EXTRA_MEDICATION_DOSAGE) ?: "1 tablet"
        val isAlarm          = intent.getBooleanExtra(EXTRA_IS_ALARM, false)
        val prescriptionId   = intent.getLongExtra(EXTRA_PRESCRIPTION_ID, -1L)

        Log.d(TAG, "onReceive: name=$medicationName time=$medicationTime isAlarm=$isAlarm")

        // 1. Show notification
        if (isAlarm) {
            showAlarmNotification(context, medicationName, medicationDosage, medicationTime)
        } else {
            showReminderNotification(context, medicationName, medicationDosage, medicationTime)
        }

        // 2. Reschedule for TOMORROW — only once, not repeatedly
        rescheduleForTomorrow(
            context, medicationName, medicationDosage,
            medicationTime, isAlarm, prescriptionId
        )

        // 3. Speak voice only if app is NOT in foreground
        if (!isAppInForeground(context)) {
            val ttsHelper = MediLensTTS(context)
            ttsHelper.speak(medicationName, medicationDosage, medicationTime, isAlarm)
        } else {
            Log.d(TAG, "App in foreground — skipping voice")
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

        // Start from scratch — parse the base time fresh
        val tomorrowCalendar = MedicationAlarmManager.parseTime(time)

        // Add exactly 1 day to get tomorrow's same time
        tomorrowCalendar.add(Calendar.DAY_OF_MONTH, 1)

        // If reminder, subtract 5 minutes from tomorrow's alarm time
        if (!isAlarm) {
            tomorrowCalendar.add(Calendar.MINUTE, -5)
        }

        val newIntent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra(EXTRA_MEDICATION_NAME,   medicationName)
            putExtra(EXTRA_MEDICATION_TIME,   time)
            putExtra(EXTRA_MEDICATION_DOSAGE, medicationDosage)
            putExtra(EXTRA_IS_ALARM,          isAlarm)
            putExtra(EXTRA_PRESCRIPTION_ID,   prescriptionId)
        }

        val requestCode = if (isAlarm) {
            (prescriptionId.toString() + time + "alarm").hashCode()
        } else {
            (prescriptionId.toString() + time + "reminder").hashCode()
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, newIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val alarmManager   = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmClockInfo = AlarmManager.AlarmClockInfo(
            tomorrowCalendar.timeInMillis, pendingIntent
        )
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)

        Log.d(TAG, "Rescheduled ${if (isAlarm) "ALARM" else "REMINDER"} " +
                "for $medicationName tomorrow at ${Date(tomorrowCalendar.timeInMillis)}")
    }

    // ── Foreground check ──────────────────────────────────────────────────
    private fun isAppInForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE)
                as android.app.ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        return appProcesses.any { process ->
            process.importance ==
                    android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                    process.processName == context.packageName
        }
    }

    // ── Voice Alert ───────────────────────────────────────────────────────
    private fun speakVoiceAlert(
        context: Context,
        medicationName: String,
        dosage: String,
        time: String,
        isAlarm: Boolean
    ) {
        Handler(Looper.getMainLooper()).post {
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {

                    val englishMsg = if (isAlarm) {
                        "It is time to take your medication. " +
                                "Please take $dosage of $medicationName now."
                    } else {
                        "Reminder. You need to take $dosage of " +
                                "$medicationName in 5 minutes."
                    }

                    val urduMsg = if (isAlarm) {
                        "Dawai lainay ka waqt aa gaya hai. " +
                                "Abhi $dosage $medicationName lain."
                    } else {
                        "Yaad dahaani. Panch minute mein " +
                                "$dosage $medicationName lena hai."
                    }

                    tts?.setLanguage(Locale.ENGLISH)
                    tts?.speak(
                        englishMsg, TextToSpeech.QUEUE_FLUSH, null, "eng_msg"
                    )

                    val urResult = tts?.setLanguage(Locale("ur"))
                    if (urResult == TextToSpeech.LANG_MISSING_DATA ||
                        urResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.ENGLISH)
                    }
                    tts?.speak(
                        urduMsg, TextToSpeech.QUEUE_ADD, null, "urdu_msg"
                    )

                    tts?.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onError(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) {
                                if (utteranceId == "urdu_msg") {
                                    tts?.shutdown()
                                    Log.d(TAG, "TTS completed and shut down")
                                }
                            }
                        }
                    )

                } else {
                    Log.e(TAG, "TTS init failed: $status")
                    tts?.shutdown()
                }
            }
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────
    private fun showReminderNotification(
        context: Context,
        medicationName: String,
        dosage: String,
        time: String
    ) {
        createNotificationChannel(
            context, CHANNEL_ID_REMINDER,
            "Medication Reminders", NotificationManager.IMPORTANCE_HIGH
        )

        val intent = Intent(context, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("from_reminder",            true)
            putExtra("reminder_medication_name", medicationName)
            putExtra("reminder_medication_time", time)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, medicationName.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val largeIcon = BitmapFactory.decodeResource(context.resources, R.mipmap.logo)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_REMINDER)
            .setSmallIcon(R.drawable.ic_pill)
            .setLargeIcon(largeIcon)
            .setContentTitle("MediLens — Upcoming Medication")
            .setContentText("In 5 minutes: Take $dosage of $medicationName")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "In 5 minutes you need to take:\n" +
                            "$dosage of $medicationName\n" +
                            "Scheduled at $time"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFF6200EE.toInt())
            .build()

        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context)
                .notify(medicationName.hashCode(), notification)
        }
    }

    private fun showAlarmNotification(
        context: Context,
        medicationName: String,
        dosage: String,
        time: String
    ) {
        createNotificationChannel(
            context, CHANNEL_ID_ALARM,
            "Medication Alarms", NotificationManager.IMPORTANCE_HIGH, true
        )

        val intent = Intent(context, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("from_reminder",            false)
            putExtra("reminder_medication_name", medicationName)
            putExtra("reminder_medication_time", time)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, medicationName.hashCode() + 1000, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val largeIcon  = BitmapFactory.decodeResource(context.resources, R.mipmap.logo)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALARM)
            .setSmallIcon(R.drawable.ic_pill)
            .setLargeIcon(largeIcon)
            .setContentTitle("⏰ Time to take $medicationName!")
            .setContentText("Take $dosage of $medicationName now")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "⏰ It is time to take your medication!\n" +
                            "Take $dosage of $medicationName\n" +
                            "Scheduled at $time"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setColor(0xFF6200EE.toInt())
            .build()

        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context)
                .notify(medicationName.hashCode() + 1000, notification)
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
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        audioAttributes
                    )
                }
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}