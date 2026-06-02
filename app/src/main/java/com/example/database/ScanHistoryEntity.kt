package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val imagePath: String,
    val whiteflyCount: Int,
    val riskLevel: String,
    val district: String,
    val syncState: Int = 0,

    // Phase 3: real values from the ML pipeline (CONTRACTS.md §1.1)
    val confidenceScore: Float = 0f,
    val inferenceTimeMs: Long = 0L,
    val imageStoragePath: String? = null,   // bare object key in leaf-images bucket
    val latitude: Double? = null,           // null when GPS unavailable — never 0.0
    val longitude: Double? = null
)
