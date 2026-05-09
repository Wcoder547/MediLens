package com.example.medilens

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AdherenceScoreDao {

    @Query("SELECT * FROM adherence_scores WHERE prescriptionId = :id")
    suspend fun getByPrescriptionId(id: Long): AdherenceScoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(score: AdherenceScoreEntity)

    @Query("SELECT * FROM adherence_scores")
    suspend fun getAll(): List<AdherenceScoreEntity>
}