package com.example.medilens

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdherenceRepository(context: Context) {

    private val db  = AppDatabase.getDatabase(context)
    private val dao = db.adherenceScoreDao()
    private val TAG = "AdherenceRepository"

    suspend fun getScore(prescriptionId: Long): AdherenceScoreEntity? =
        withContext(Dispatchers.IO) {
            dao.getByPrescriptionId(prescriptionId)
        }

    suspend fun recordTaken(prescriptionId: Long, scheduledTimeMillis: Long) {
        withContext(Dispatchers.IO) {
            val lateMins = ((System.currentTimeMillis() - scheduledTimeMillis) / 60_000L)
                .toInt().coerceAtLeast(0)
            val existing = dao.getByPrescriptionId(prescriptionId)
            val updated  = ReminderDecisionEngine.computeAfterTaken(existing, prescriptionId, lateMins)
            dao.upsert(updated)
            Log.d(TAG, "TAKEN: prescriptionId=$prescriptionId late=${lateMins}min score=${updated.score}")
        }
    }

    suspend fun recordMissed(prescriptionId: Long) {
        withContext(Dispatchers.IO) {
            val existing = dao.getByPrescriptionId(prescriptionId)
            val updated  = ReminderDecisionEngine.computeAfterMissed(existing, prescriptionId)
            dao.upsert(updated)
            Log.d(TAG, "MISSED: prescriptionId=$prescriptionId score=${updated.score} consecutive=${updated.consecutiveMisses}")
        }
    }

    suspend fun getDriftMinutes(prescriptionId: Long): Int =
        withContext(Dispatchers.IO) {
            dao.getByPrescriptionId(prescriptionId)?.driftMinutes ?: 0
        }
}