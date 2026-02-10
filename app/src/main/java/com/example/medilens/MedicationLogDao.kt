package com.example.medilens

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationLogDao {

    @Insert
    suspend fun insertLog(log: MedicationLogEntity)

    @Query("SELECT * FROM medication_logs WHERE completedDate = :date")
    fun getLogsForDate(date: String): Flow<List<MedicationLogEntity>>

    @Query("SELECT * FROM medication_logs WHERE prescriptionId = :prescriptionId AND scheduledTime = :scheduledTime AND completedDate = :date")
    suspend fun isTaskCompleted(prescriptionId: Long, scheduledTime: String, date: String): MedicationLogEntity?

    @Query("DELETE FROM medication_logs WHERE completedDate < :date")
    suspend fun deleteOldLogs(date: String)
}
