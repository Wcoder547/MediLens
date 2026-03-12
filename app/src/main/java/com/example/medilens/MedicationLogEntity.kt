package com.example.medilens

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medication_logs")
data class MedicationLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val prescriptionId: Long,
    val scheduledTime: String,
    val completedAt: Long,
    val completedDate: String
)