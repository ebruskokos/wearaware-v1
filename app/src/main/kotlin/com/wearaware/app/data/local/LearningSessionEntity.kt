package com.wearaware.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learning_session")
data class LearningSessionEntity(
    @PrimaryKey val sessionId: String,
    val startedAt: Long,
    val completedAt: Long?,
    val status: String,
    val deviceAddress: String?,
    val fingerprintId: String?,
    val eventCount: Int
)
