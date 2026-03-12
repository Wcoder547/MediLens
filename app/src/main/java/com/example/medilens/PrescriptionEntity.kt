package com.example.medilens

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prescriptions")
data class PrescriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val prescriptionName: String,
    val drugName: String,
    val dosageQuantity: String,
    val totalDrugQuantity: String,
    val frequency: String,
    val time1: String?,
    val time2: String?,
    val time3: String?
)