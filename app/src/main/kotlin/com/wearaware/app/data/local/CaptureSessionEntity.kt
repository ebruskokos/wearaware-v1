package com.wearaware.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "capture_session")
data class CaptureSessionEntity(
    @PrimaryKey val id: String,
    val type: String,           // CaptureType.name: "BASELINE" or "TARGET"
    val startedAt: Long,
    val stoppedAt: Long
)
