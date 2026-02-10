package com.example.medilens

import android.Manifest
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
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class MedicationAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_MEDICATION_NAME = "medication_name"
        const val EXTRA_MEDICATION_TIME = "medication_time"
        const val EXTRA_IS_ALARM = "is_alarm"
        const val CHANNEL_ID_REMINDER = "medication_reminder"
        const val CHANNEL_ID_ALARM = "medication_alarm"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val medicationName = intent.getStringExtra(EXTRA_MEDICATION_NAME) ?: "Your medication"
        val medicationTime = intent.getStringExtra(EXTRA_MEDICATION_TIME) ?: ""
        val isAlarm = intent.getBooleanExtra(EXTRA_IS_ALARM, false)

        if (isAlarm) {
            showAlarmNotification(context, medicationName, medicationTime)
        } else {
            showReminderNotification(context, medicationName, medicationTime)
        }
    }

    private fun showReminderNotification(context: Context, medicationName: String, time: String) {
        createNotificationChannel(context, CHANNEL_ID_REMINDER, "Medication Reminders", NotificationManager.IMPORTANCE_HIGH)

        val intent = Intent(context, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Get app logo
        val largeIcon = BitmapFactory.decodeResource(context.resources, R.mipmap.logo)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_REMINDER)
            .setSmallIcon(R.drawable.ic_pill) // Small icon (must be white/transparent)
            .setLargeIcon(largeIcon) // Large icon (your app logo)
            .setContentTitle("MediLens")
            .setContentText("Time to take your $time")
            .setSubText(medicationName)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Time to take your $time\n$medicationName"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFF6200EE.toInt()) // Purple color for MediLens brand
            .build()

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(medicationName.hashCode(), notification)
        }
    }

    private fun showAlarmNotification(context: Context, medicationName: String, time: String) {
        createNotificationChannel(context, CHANNEL_ID_ALARM, "Medication Alarms", NotificationManager.IMPORTANCE_HIGH, true)

        val intent = Intent(context, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        // Get app logo
        val largeIcon = BitmapFactory.decodeResource(context.resources, R.mipmap.logo)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALARM)
            .setSmallIcon(R.drawable.ic_pill) // Small icon (must be white/transparent)
            .setLargeIcon(largeIcon) // Large icon (your app logo)
            .setContentTitle("⏰ Medication Time!")
            .setContentText("Take your $time now")
            .setSubText(medicationName)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("⏰ It's time to take your medication!\n$time\n$medicationName"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setColor(0xFF6200EE.toInt()) // Purple color for MediLens brand
            .build()

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
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
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), audioAttributes)
                }
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
