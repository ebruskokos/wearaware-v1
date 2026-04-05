package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.KnownTargetRepository
import javax.inject.Inject

/**
 * Quick-learn path: builds or merges a KnownTargetSignature from a CapturedDevice (no GATT data).
 * If [existing] is provided, merges new observations into it rather than overwriting.
 * gattServiceUuids is empty on this path (only populated via full Pair & Learn GATT flow).
 *
 * Threading: [invoke] calls [KnownTargetRepository.save] which issues SharedPreferences.commit()
 * (blocking disk I/O). Callers must dispatch to [kotlinx.coroutines.Dispatchers.IO].
 */
class SaveKnownTargetFromCaptureUseCase @Inject constructor(
    private val repository: KnownTargetRepository,
    private val merge: MergeKnownTargetSignatureUseCase
) {
    operator fun invoke(device: CapturedDevice, existing: KnownTargetSignature? = null): KnownTargetSignature {
        if (existing != null) {
            return merge(existing, device)
        }
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
