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
    val time3: String?,
    // ── Added in DB version 3 (migration 2→3) ──────────────────────────
    // Default is ENROLLMENT_PENDING so all existing rows get a safe value.
    val verificationStatus: String = VerificationStatus.ENROLLMENT_PENDING.name
)
