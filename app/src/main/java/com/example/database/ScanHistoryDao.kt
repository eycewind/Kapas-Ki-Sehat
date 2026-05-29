package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanHistoryEntity): Long

    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<ScanHistoryEntity>>

    @Query("SELECT * FROM scan_history WHERE syncState = 0 ORDER BY timestamp ASC")
    fun getPendingSyncScans(): Flow<List<ScanHistoryEntity>>

    @Query("UPDATE scan_history SET syncState = :status WHERE id = :scanId")
    suspend fun updateSyncStatus(scanId: Long, status: Int): Int
}
