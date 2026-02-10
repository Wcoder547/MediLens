package com.example.medilens

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import androidx.room.Entity
import androidx.room.PrimaryKey

// MedicationLog Entity
@Entity(tableName = "medication_logs")
data class MedicationLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val prescriptionId: Long,
    val scheduledTime: String,
    val completedAt: Long, // timestamp
    val completedDate: String // format: "2026-02-10"
)

@Database(
    entities = [PrescriptionEntity::class, MedicationLogEntity::class],
    version = 2, // Incremented version
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun prescriptionDao(): PrescriptionDao
    abstract fun medicationLogDao(): MedicationLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medilens_prescriptions.db"
                ).fallbackToDestructiveMigration()  // For dev: wipes on schema change
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
