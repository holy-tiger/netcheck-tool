package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diagnostic_history")
data class DiagnosticHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trackingId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // "SUCCESS" or "FAILED"
    val summary: String,
    val reportUrl: String,
    val reportJson: String
)
