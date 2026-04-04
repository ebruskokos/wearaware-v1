package com.wearaware.app.data.repository

import com.wearaware.app.data.local.ScanLogDao
import com.wearaware.app.data.mapper.toScanLogEntity
import com.wearaware.app.data.mapper.toDomain
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.ScanLogEntry
import com.wearaware.app.domain.model.TargetMatchResult
import com.wearaware.app.domain.repository.ScanLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PURPOSE: Implements ScanLogRepository using Room DAO.
 * NOTES: observeAll() returns a Flow — Room emits updates automatically when the
 *   table changes. Callers do not need to manually refresh.
 */
@Singleton
class ScanLogRepositoryImpl @Inject constructor(
    private val dao: ScanLogDao
) : ScanLogRepository {

    /** Logs a detection event by mapping ObservedDevice to a Room entity and inserting it. */
    override suspend fun log(device: ObservedDevice, matchResult: TargetMatchResult?) {
        dao.insert(device.toScanLogEntity(matchResult))
    }

    /** Returns a live stream of all session log entries, newest first. */
    override fun observeAll(): Flow<List<ScanLogEntry>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    /** Deletes all session log entries from the database. */
    override suspend fun clearAll() {
        dao.deleteAll()
    }
}
