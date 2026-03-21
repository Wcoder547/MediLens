package com.example.medilens

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

    private fun scheduleReminderAndAlarm(
        context: Context,
        prescription: PrescriptionEntity,
        time: String
    ) {
        // Parse exact alarm time
        val alarmCalendar = parseTime(time)
        if (alarmCalendar.timeInMillis <= System.currentTimeMillis()) {
            alarmCalendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        // Parse reminder time — completely separate calendar instance
        val reminderCalendar = parseTime(time)
        if (reminderCalendar.timeInMillis <= System.currentTimeMillis()) {
            reminderCalendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        reminderCalendar.add(Calendar.MINUTE, -5)

        // If reminder time has already passed, schedule for tomorrow
        if (reminderCalendar.timeInMillis <= System.currentTimeMillis()) {
            reminderCalendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        // Schedule reminder with its own calendar
        scheduleNotification(
            context, prescription, time,
            reminderCalendar.timeInMillis, false
        )

        // Schedule exact alarm with its own calendar
        scheduleNotification(
            context, prescription, time,
            alarmCalendar.timeInMillis, true
        )

        Log.d(TAG, "Scheduled REMINDER for ${prescription.drugName} at ${Date(reminderCalendar.timeInMillis)}")
        Log.d(TAG, "Scheduled ALARM    for ${prescription.drugName} at ${Date(alarmCalendar.timeInMillis)}")
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
            putExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_NAME,   prescription.drugName)
            putExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_TIME,   time)
            putExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_DOSAGE, prescription.dosageQuantity)
            putExtra(MedicationAlarmReceiver.EXTRA_IS_ALARM,          isAlarm)
            putExtra(MedicationAlarmReceiver.EXTRA_PRESCRIPTION_ID,   prescription.id)
        }

        // Completely separate request codes for reminder and alarm
        val requestCode = if (isAlarm) {
            (prescription.id.toString() + time + "alarm").hashCode()
        } else {
            (prescription.id.toString() + time + "reminder").hashCode()
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)

        val type = if (isAlarm) "ALARM" else "REMINDER"
        Log.d(TAG, "$type scheduled for ${prescription.drugName} → ${Date(triggerTime)}")
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

    fun cancelExactAlarm(context: Context, prescription: PrescriptionEntity, time: String) {
        cancelNotification(context, prescription, time, true)
        Log.d(TAG, "Cancelled exact alarm for ${prescription.drugName} at $time")
    }

    private fun cancelNotification(
        context: Context,
        prescription: PrescriptionEntity,
        time: String,
        isAlarm: Boolean
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent       = Intent(context, MedicationAlarmReceiver::class.java)

        val requestCode = if (isAlarm) {
            (prescription.id.toString() + time + "alarm").hashCode()
        } else {
            (prescription.id.toString() + time + "reminder").hashCode()
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )

        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
            Log.d(TAG, "Cancelled ${if (isAlarm) "alarm" else "reminder"} for $time")
        }
    }

    fun parseTime(timeString: String): Calendar {
        return try {
            val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val date   = format.parse(timeString)
            Calendar.getInstance().apply {
                time = date ?: Date()
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                val now = Calendar.getInstance()
                set(Calendar.YEAR,         now.get(Calendar.YEAR))
                set(Calendar.MONTH,        now.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing time: $timeString", e)
            Calendar.getInstance()
        }
    }
}