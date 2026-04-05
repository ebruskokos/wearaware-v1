package com.wearaware.app.domain.repository

import com.wearaware.app.domain.model.LearningEventType
import com.wearaware.app.domain.model.LearningSessionStatus
import com.wearaware.app.domain.model.PairedLearningEvent
import com.wearaware.app.domain.model.PairedLearningSession

interface LearningSessionRepository {
    suspend fun createSession(sessionId: String, startedAt: Long)
    suspend fun updateStatus(sessionId: String, status: LearningSessionStatus)
    suspend fun updateDeviceAddress(sessionId: String, address: String)
    suspend fun updateFingerprintId(sessionId: String, fingerprintId: String)
    suspend fun getAllSessions(): List<PairedLearningSession>
    suspend fun getSession(sessionId: String): PairedLearningSession?
    suspend fun appendEvent(
        sessionId: String,
        eventType: LearningEventType,
        occurredAt: Long,
        detail: String?
    ): PairedLearningEvent
    suspend fun getEventsForSession(sessionId: String): List<PairedLearningEvent>
}
