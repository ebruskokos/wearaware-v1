package com.wearaware.app.data.repository

import com.wearaware.app.data.local.LearningEventEntity
import com.wearaware.app.data.local.LearningSessionDao
import com.wearaware.app.data.local.LearningSessionEntity
import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.LearningSessionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LearningSessionRepositoryImpl @Inject constructor(
    private val dao: LearningSessionDao
) : LearningSessionRepository {

    override suspend fun createSession(sessionId: String, startedAt: Long) {
        dao.insertSession(
            LearningSessionEntity(
                sessionId = sessionId,
                startedAt = startedAt,
                completedAt = null,
                status = LearningSessionStatus.IN_PROGRESS.name,
                deviceAddress = null,
                fingerprintId = null,
                eventCount = 0
            )
        )
    }

    override suspend fun updateStatus(sessionId: String, status: LearningSessionStatus) {
        val completedAt = if (status != LearningSessionStatus.IN_PROGRESS) System.currentTimeMillis() else null
        dao.updateStatus(sessionId, status.name, completedAt)
    }

    override suspend fun updateDeviceAddress(sessionId: String, address: String) {
        dao.updateDeviceAddress(sessionId, address)
    }

    override suspend fun updateFingerprintId(sessionId: String, fingerprintId: String) {
        dao.updateFingerprintId(sessionId, fingerprintId)
    }

    override suspend fun getAllSessions(): List<PairedLearningSession> =
        dao.getAllSessions().map { it.toDomain() }

    override suspend fun getSession(sessionId: String): PairedLearningSession? =
        dao.getSession(sessionId)?.toDomain()

    override suspend fun appendEvent(
        sessionId: String,
        eventType: LearningEventType,
        occurredAt: Long,
        detail: String?
    ): PairedLearningEvent {
        val id = dao.insertEventAndIncrement(
            LearningEventEntity(
                sessionId = sessionId,
                eventType = eventType.name,
                occurredAt = occurredAt,
                detail = detail
            ),
            sessionId
        )
        return PairedLearningEvent(
            eventId = id,
            sessionId = sessionId,
            eventType = eventType,
            occurredAt = occurredAt,
            detail = detail
        )
    }

    override suspend fun getEventsForSession(sessionId: String): List<PairedLearningEvent> =
        dao.getEventsForSession(sessionId).map { it.toDomain() }

    private fun LearningSessionEntity.toDomain() = PairedLearningSession(
        sessionId = sessionId,
        startedAt = startedAt,
        completedAt = completedAt,
        status = LearningSessionStatus.entries.firstOrNull { it.name == status } ?: LearningSessionStatus.FAILED,
        deviceAddress = deviceAddress,
        fingerprintId = fingerprintId,
        eventCount = eventCount
    )

    private fun LearningEventEntity.toDomain() = PairedLearningEvent(
        eventId = eventId,
        sessionId = sessionId,
        eventType = LearningEventType.entries.firstOrNull { it.name == eventType } ?: LearningEventType.GATT_FAILED,
        occurredAt = occurredAt,
        detail = detail
    )
}
