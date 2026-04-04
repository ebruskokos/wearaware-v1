package com.wearaware.app.domain.repository

import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.ScanLogEntry
import com.wearaware.app.domain.model.TargetMatchResult
import kotlinx.coroutines.flow.Flow

/**
 * PURPOSE: Defines the contract for the local session scan log.
 * NOTES: Implemented by ScanLogRepositoryImpl using Room.
 *   All storage is on-device only — no network operations.
 */
interface ScanLogRepository {
    /** Logs a detection event for an observed device, optionally with its match result. */
    suspend fun log(device: ObservedDevice, matchResult: TargetMatchResult? = null)

    /** Returns a live stream of all session log entries, newest first. */
    fun observeAll(): Flow<List<ScanLogEntry>>

    /** Deletes all session log entries. Irreversible within the session. */
    suspend fun clearAll()
}
