package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: DiagnosticHistoryEntity)

    @Query("SELECT * FROM diagnostic_history ORDER BY timestamp DESC LIMIT 5")
    fun getRecentReports(): Flow<List<DiagnosticHistoryEntity>>

    @Query("DELETE FROM diagnostic_history WHERE id NOT IN (SELECT id FROM diagnostic_history ORDER BY timestamp DESC LIMIT 5)")
    suspend fun trimOldReports()
}
