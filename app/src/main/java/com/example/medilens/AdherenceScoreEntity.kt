package com.example.medilens

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "adherence_scores")
data class AdherenceScoreEntity(
    @PrimaryKey
    val prescriptionId: Long,
    val score: Int = 100,
    val streak: Int = 0,
    val missCountLast7: Int = 0,
    val consecutiveMisses: Int = 0,
    val driftMinutes: Int = 0,
    val lastUpdated: Long = 0L
)