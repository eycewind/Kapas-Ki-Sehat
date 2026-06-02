package com.example.database

import androidx.room.Database
import androidx.room.RoomDatabase

// Version bumped to 2: ScanHistoryEntity gained confidenceScore, inferenceTimeMs,
// imageStoragePath, latitude, longitude (Phase 3 real-value fields).
// fallbackToDestructiveMigration() is set in CottonAceApplication — local scan
// history is expendable during development.
@Database(entities = [ScanHistoryEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanHistoryDao(): ScanHistoryDao
}
