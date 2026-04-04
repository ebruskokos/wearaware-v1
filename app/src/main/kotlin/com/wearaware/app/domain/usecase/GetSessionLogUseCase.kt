package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.ScanLogEntry
import com.wearaware.app.domain.repository.ScanLogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * PURPOSE: Provides a live stream of all session log entries for display in SessionLogScreen.
 * NOTES: Returns a Flow — Room emits updates automatically when new entries are logged.
 */
class GetSessionLogUseCase @Inject constructor(
    private val scanLogRepository: ScanLogRepository
) {
    operator fun invoke(): Flow<List<ScanLogEntry>> = scanLogRepository.observeAll()
}
