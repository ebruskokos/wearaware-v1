package com.wearaware.app.domain.usecase

import android.util.Log
import com.wearaware.app.domain.model.CapturedDevice
import com.wearaware.app.domain.model.KnownBehaviorProfile
import com.wearaware.app.domain.model.KnownTargetSignature
import com.wearaware.app.domain.repository.KnownTargetRepository
import javax.inject.Inject

private const val TAG = "WearAware.Training"
private const val MAX_PREFIXES = 5

/**
 * Merges a new capture observation into an existing [KnownTargetSignature].
 *
 * Merge rules:
 * - Manufacturer IDs: union (deduplicated)
 * - Manufacturer data prefixes: frequency-ranked — each observed prefix increments its count;
 *   only the top [MAX_PREFIXES] by frequency are kept in [KnownTargetSignature.manufacturerDataPrefixes].
 * - Service UUIDs: union (deduplicated)
 * - GATT service UUIDs: unchanged (only from full Pair & Learn GATT flow)
 * - Characteristic value prefixes: unchanged (only from full Pair & Learn GATT flow)
 * - Behavior profile: moving average RSSI, seenCount range (min/max), visibleAtStop count
 * - learnCount: incremented
 * - lastUpdatedAt: set to now
 *
 * Threading: calls [KnownTargetRepository.save] (SharedPreferences.commit — blocking I/O).
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
        val updatedFrequency = mergeFrequency(existing.manufacturerDataPrefixFrequency, newPrefixes)
        val topPrefixes = topPrefixesByFrequency(updatedFrequency)
        val now = System.currentTimeMillis()

        val merged = existing.copy(
            lastUpdatedAt = now,
            savedAt = now,
            manufacturerIds = (existing.manufacturerIds + newDevice.manufacturerIds).distinct(),
            manufacturerDataPrefixes = topPrefixes,
            manufacturerDataPrefixFrequency = updatedFrequency,
            serviceUuids = (existing.serviceUuids + newDevice.serviceUuids).distinct(),
            behaviorProfile = mergedBehaviorProfile(existing, newDevice),
            learnCount = existing.learnCount + 1
        )

        Log.d(TAG, "Signature merged: learnCount=${merged.learnCount} " +
            "prefixes=${merged.manufacturerDataPrefixes} " +
            "avgRssi=${merged.behaviorProfile?.typicalRssiAtClose} " +
            "mfIds=${merged.manufacturerIds.map { "0x${it.toString(16).uppercase()}" }}")

        repository.save(merged)
        return merged
    }

    private fun mergeFrequency(
        existing: Map<String, Int>,
        newPrefixes: List<String>
    ): Map<String, Int> {
        val updated = existing.toMutableMap()
        newPrefixes.forEach { prefix ->
            updated[prefix] = (updated[prefix] ?: 0) + 1
        }
        return updated
    }

    private fun topPrefixesByFrequency(frequency: Map<String, Int>): List<String> =
        frequency.entries
            .sortedByDescending { it.value }
            .take(MAX_PREFIXES)
            .map { it.key }

    private fun mergedBehaviorProfile(
        existing: KnownTargetSignature,
        newDevice: CapturedDevice
    ): KnownBehaviorProfile {
        val current = existing.behaviorProfile
        val sampleCount = (current?.rssiSampleCount ?: 0).coerceAtLeast(1)
        val currentAvg = current?.typicalRssiAtClose ?: newDevice.averageRssi

        // Welford-style incremental moving average
        val newAvg = ((currentAvg.toLong() * sampleCount) + newDevice.averageRssi) / (sampleCount + 1)

        val newProfile = KnownBehaviorProfile(
            typicalRssiAtClose = newAvg.toInt(),
            rssiSampleCount = sampleCount + 1,
            minSeenCount = minOf(current?.minSeenCount ?: newDevice.seenCount, newDevice.seenCount),
            maxSeenCount = maxOf(current?.maxSeenCount ?: newDevice.seenCount, newDevice.seenCount),
            visibleAtStopCount = (current?.visibleAtStopCount ?: 0) + if (newDevice.visibleAtStop) 1 else 0,
            totalObservations = (current?.totalObservations ?: 0) + 1
        )

        Log.d(TAG, "Behavior profile updated: avgRssi=${newProfile.typicalRssiAtClose} " +
            "samples=${newProfile.rssiSampleCount} " +
            "seenRange=[${newProfile.minSeenCount}–${newProfile.maxSeenCount}] " +
            "visibleAtStopCount=${newProfile.visibleAtStopCount}")

        return newProfile
    }
}
