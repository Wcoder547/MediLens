package com.example.medilens

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PrescriptionEntity::class,
        MedicationLogEntity::class,
        AdherenceScoreEntity::class      // ← NEW
    ],
    version = 4,                         // ← bumped from 3
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun prescriptionDao(): PrescriptionDao
    abstract fun medicationLogDao(): MedicationLogDao
    abstract fun adherenceScoreDao(): AdherenceScoreDao   // ← NEW

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // ── Migration 2 → 3 (existing) ────────────────────────────────────
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

        // ── Migration 3 → 4 (NEW) ─────────────────────────────────────────
        // Creates adherence_scores table.
        // Existing prescriptions start with score=100 (no history yet).
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS adherence_scores (
                        prescriptionId INTEGER NOT NULL PRIMARY KEY,
                        score INTEGER NOT NULL DEFAULT 100,
                        streak INTEGER NOT NULL DEFAULT 0,
                        missCountLast7 INTEGER NOT NULL DEFAULT 0,
                        consecutiveMisses INTEGER NOT NULL DEFAULT 0,
                        driftMinutes INTEGER NOT NULL DEFAULT 0,
                        lastUpdated INTEGER NOT NULL DEFAULT 0
                    )
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
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)  // ← both migrations
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}