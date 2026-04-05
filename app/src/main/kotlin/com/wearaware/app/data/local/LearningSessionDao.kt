package com.wearaware.app.data.local

import androidx.room.*

@Dao
interface LearningSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: LearningSessionEntity)

    @Query("UPDATE learning_session SET status = :status, completedAt = :completedAt WHERE sessionId = :sessionId")
    suspend fun updateStatus(sessionId: String, status: String, completedAt: Long?)

    @Query("UPDATE learning_session SET deviceAddress = :address WHERE sessionId = :sessionId")
    suspend fun updateDeviceAddress(sessionId: String, address: String)

    @Query("UPDATE learning_session SET fingerprintId = :fingerprintId WHERE sessionId = :sessionId")
    suspend fun updateFingerprintId(sessionId: String, fingerprintId: String)

    @Query("UPDATE learning_session SET eventCount = eventCount + 1 WHERE sessionId = :sessionId")
    suspend fun incrementEventCount(sessionId: String)

    @Query("SELECT * FROM learning_session ORDER BY startedAt DESC")
    suspend fun getAllSessions(): List<LearningSessionEntity>

    @Query("SELECT * FROM learning_session WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: String): LearningSessionEntity?

    @Insert
    suspend fun insertEvent(event: LearningEventEntity): Long

    @Query("SELECT * FROM learning_event WHERE sessionId = :sessionId ORDER BY occurredAt ASC")
    suspend fun getEventsForSession(sessionId: String): List<LearningEventEntity>
}
