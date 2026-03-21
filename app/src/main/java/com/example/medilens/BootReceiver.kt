package com.example.medilens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "Boot completed — rescheduling all medication alarms")

        // Use coroutine to read from Room DB
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db            = AppDatabase.getDatabase(context)
                val prescriptions = db.prescriptionDao().getAllPrescriptions().first()

                prescriptions.forEach { prescription ->
                    MedicationAlarmManager.scheduleAlarms(context, prescription)
                    Log.d(TAG, "Rescheduled alarms for ${prescription.drugName}")
                }

                Log.d(TAG, "All alarms rescheduled after boot — ${prescriptions.size} prescriptions")
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling alarms after boot", e)
            }
        }
    }
}