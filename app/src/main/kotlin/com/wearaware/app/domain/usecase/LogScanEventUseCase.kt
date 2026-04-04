package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.repository.ScanLogRepository
import javax.inject.Inject

/**
 * PURPOSE: Logs a single detection event to the local session log.
 * NOTES: Must be called from a coroutine (suspend function).
 *   Classification metadata (matchedRuleId, ruleVersion) is logged with the event
 *   to enable audit traceability.
 */
class LogScanEventUseCase @Inject constructor(
    private val scanLogRepository: ScanLogRepository
) {
    suspend operator fun invoke(device: ObservedDevice) {
        scanLogRepository.log(device)
    }
}
