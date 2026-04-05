package com.wearaware.app.domain.model

enum class LearningSessionStatus { IN_PROGRESS, COMPLETED, FAILED, ABANDONED }

data class PairedLearningSession(
    val sessionId: String,
    val startedAt: Long,
    val completedAt: Long?,
    val status: LearningSessionStatus,
    val deviceAddress: String?,
    val fingerprintId: String?,
    val eventCount: Int
)
