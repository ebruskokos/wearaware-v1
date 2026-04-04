package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.repository.ScanLogRepository
import javax.inject.Inject

/**
 * PURPOSE: Deletes all entries from the local session log.
 *   Called when user taps "Clear session log" in SessionLogScreen.
 * NOTES: Irreversible. No confirmation logic here — confirmation dialog is in the UI layer.
 */
class ClearSessionLogUseCase @Inject constructor(
    private val scanLogRepository: ScanLogRepository
) {
    suspend operator fun invoke() {
        scanLogRepository.clearAll()
    }
}
