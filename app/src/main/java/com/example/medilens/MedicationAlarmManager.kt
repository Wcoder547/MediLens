package com.example.medilens

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

object MedicationAlarmManager {

    private const val TAG = "MedicationAlarmManager"

    fun scheduleAlarms(context: Context, prescription: PrescriptionEntity) {
        val times = listOfNotNull(
            prescription.time1,
            prescription.time2,
            prescription.time3
        )

        times.forEach { time ->
            scheduleReminderAndAlarm(context, prescription, time)
        }
    }

    private fun scheduleReminderAndAlarm(context: Context, prescription: PrescriptionEntity, time: String) {
        val calendar = parseTime(time)

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            // If time has passed today, schedule for tomorrow
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        // Schedule reminder (5 minutes before)
        val reminderCalendar = calendar.clone() as Calendar
        reminderCalendar.add(Calendar.MINUTE, -5)
        scheduleNotification(context, prescription, time, reminderCalendar.timeInMillis, false)

        // Schedule alarm (exact time)
        scheduleNotification(context, prescription, time, calendar.timeInMillis, true)

        Log.d(TAG, "Scheduled alarms for ${prescription.drugName} at $time")
    }

    private fun scheduleNotification(
        context: Context,
        prescription: PrescriptionEntity,
        time: String,
        triggerTime: Long,
        isAlarm: Boolean
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_NAME, prescription.drugName)
            putExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_TIME, time)
            putExtra(MedicationAlarmReceiver.EXTRA_IS_ALARM, isAlarm)
        }

        val requestCode = (prescription.id.toString() + time + isAlarm.toString()).hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Use exact alarm for precise timing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }

        val type = if (isAlarm) "Alarm" else "Reminder"
        Log.d(TAG, "$type scheduled for ${prescription.drugName} at ${Date(triggerTime)}")
    }

    private fun parseTime(timeString: String): Calendar {
        return try {
            val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val date = format.parse(timeString)

            Calendar.getInstance().apply {
                time = date ?: Date()
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                // Set to today's date but keep the parsed time
                val now = Calendar.getInstance()
                set(Calendar.YEAR, now.get(Calendar.YEAR))
                set(Calendar.MONTH, now.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing time: $timeString", e)
            Calendar.getInstance()
        }
    }

    fun cancelAlarms(context: Context, prescription: PrescriptionEntity) {
        val times = listOfNotNull(
            prescription.time1,
            prescription.time2,
            prescription.time3
        )

        times.forEach { time ->
            cancelNotification(context, prescription, time, false)
            cancelNotification(context, prescription, time, true)
        }
    }

    private fun cancelNotification(
        context: Context,
        prescription: PrescriptionEntity,
        time: String,
        isAlarm: Boolean
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MedicationAlarmReceiver::class.java)

        val requestCode = (prescription.id.toString() + time + isAlarm.toString()).hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )

        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }
}
