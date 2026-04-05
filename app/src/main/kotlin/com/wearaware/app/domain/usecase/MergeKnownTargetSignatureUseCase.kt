package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.CapturedDevice
import com.wearaware.app.domain.model.KnownBehaviorProfile
import com.wearaware.app.domain.model.KnownTargetSignature
import com.wearaware.app.domain.repository.KnownTargetRepository
import javax.inject.Inject

/**
 * Merges a new capture observation into an existing KnownTargetSignature rather than overwriting it.
 *
 * Merge rules:
 * - Manufacturer IDs: union (deduplicated)
 * - Manufacturer data prefixes: union (deduplicated)
 * - Service UUIDs: union (deduplicated)
 * - GATT service UUIDs: unchanged (only populated via full Pair & Learn GATT flow)
 * - Characteristic value prefixes: unchanged (only populated via full Pair & Learn GATT flow)
 * - Behavior profile: takes the closer (higher) RSSI and higher seenCount across observations
 * - learnCount: incremented by 1
 *
 * Threading: calls [KnownTargetRepository.save] which issues SharedPreferences.commit().
 * Callers must dispatch to [kotlinx.coroutines.Dispatchers.IO].
 */
class MergeKnownTargetSignatureUseCase @Inject constructor(
    private val repository: KnownTargetRepository
) {
    operator fun invoke(
        existing: KnownTargetSignature,
        newDevice: CapturedDevice
    ): KnownTargetSignature {
        val newPrefixes = extractPrefixesFromSummary(newDevice.manufacturerDataSummary)

        val merged = existing.copy(
            savedAt = System.currentTimeMillis(),
            manufacturerIds = (existing.manufacturerIds + newDevice.manufacturerIds).distinct(),
            manufacturerDataPrefixes = (existing.manufacturerDataPrefixes + newPrefixes).distinct(),
            serviceUuids = (existing.serviceUuids + newDevice.serviceUuids).distinct(),
            behaviorProfile = mergedBehaviorProfile(existing, newDevice),
            learnCount = existing.learnCount + 1
        )
        repository.save(merged)
        return merged
    }

    private fun mergedBehaviorProfile(
        existing: KnownTargetSignature,
        newDevice: CapturedDevice
    ): KnownBehaviorProfile {
        val current = existing.behaviorProfile
        return KnownBehaviorProfile(
            // Higher value = closer signal = better anchor
            typicalRssiAtClose = maxOf(current?.typicalRssiAtClose ?: newDevice.averageRssi, newDevice.averageRssi),
            // Higher seenCount = more confident persistence baseline
            minSeenCount = maxOf(current?.minSeenCount ?: newDevice.seenCount, newDevice.seenCount)
        )
    }
}
