package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.KnownTargetRepository
import javax.inject.Inject

/**
 * Quick-learn path: builds a KnownTargetSignature from a CapturedDevice (no GATT data).
 * Replaces SaveLearnedSignatureUseCase. gattServiceUuids is empty on this path.
 *
 * Threading: [invoke] calls [KnownTargetRepository.save] which issues SharedPreferences.commit()
 * (blocking disk I/O). Callers must dispatch to [kotlinx.coroutines.Dispatchers.IO].
 */
class SaveKnownTargetFromCaptureUseCase @Inject constructor(
    private val repository: KnownTargetRepository
) {
    operator fun invoke(device: CapturedDevice): KnownTargetSignature {
        val prefixes = extractPrefixesFromSummary(device.manufacturerDataSummary)
        val signature = KnownTargetSignature(
            displayName = "My Meta Glasses",
            savedAt = System.currentTimeMillis(),
            fingerprintId = device.fingerprintId,
            manufacturerIds = device.manufacturerIds,
            manufacturerDataPrefixes = prefixes,
            serviceUuids = device.serviceUuids,
            gattServiceUuids = emptyList(),
            behaviorProfile = KnownBehaviorProfile(
                typicalRssiAtClose = device.averageRssi,
                minSeenCount = device.seenCount
            )
        )
        repository.save(signature)
        return signature
    }
}
