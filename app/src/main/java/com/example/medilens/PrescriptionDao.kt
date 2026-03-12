package com.example.medilens

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PrescriptionDao {

    @Query("SELECT * FROM prescriptions ORDER BY id DESC")
    fun getAllPrescriptions(): Flow<List<PrescriptionEntity>>

    @Insert
    suspend fun insert(prescription: PrescriptionEntity): Long

    @Update
    suspend fun update(prescription: PrescriptionEntity)

    @Delete
    suspend fun delete(prescription: PrescriptionEntity)

    @Query("DELETE FROM prescriptions")
    suspend fun deleteAll()
}