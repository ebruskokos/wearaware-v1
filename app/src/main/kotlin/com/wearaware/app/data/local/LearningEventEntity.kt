package com.wearaware.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learning_event")
data class LearningEventEntity(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0,
    val sessionId: String,
    val eventType: String,
    val occurredAt: Long,
    val detail: String?
)
