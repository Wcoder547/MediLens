package com.example.medilens

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PrescriptionEntity::class, MedicationLogEntity::class],
    version = 3,           // ← bumped from 2
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun prescriptionDao(): PrescriptionDao
    abstract fun medicationLogDao(): MedicationLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // ── Migration 2 → 3 ───────────────────────────────────────────────
        // Adds verificationStatus column to existing prescriptions table.
        // All existing rows get the safe default 'ENROLLMENT_PENDING'.
        // No data is wiped.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    ALTER TABLE prescriptions
                    ADD COLUMN verificationStatus TEXT NOT NULL DEFAULT 'ENROLLMENT_PENDING'
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medilens_prescriptions.db"
                )
                    // ── Proper migrations — no data wipe ──────────────────
                    .addMigrations(MIGRATION_2_3)
                    // NOTE: fallbackToDestructiveMigration() deliberately removed.
                    // If you add future phases (Phase 2 embeddings), add MIGRATION_3_4 here.
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
