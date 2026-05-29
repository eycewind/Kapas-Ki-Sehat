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
    val syncState: Int = 0
)
