package com.wearaware.app.domain.usecase

import android.util.Log
import com.wearaware.app.domain.model.KnownBehaviorProfile
import com.wearaware.app.domain.model.KnownTargetSignature
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.repository.KnownTargetRepository
import javax.inject.Inject

private const val RTAG = "WearAware.Training"

/**
 * Refines an existing [KnownTargetSignature] using a live-scan [ObservedDevice].
 * Counterpart to [MergeKnownTargetSignatureUseCase] which takes a [CapturedDevice].
 *
 * Applies the same merge rules:
 * - Manufacturer IDs / data prefixes / service UUIDs: union
 * - Prefix frequency tracked; top-5 kept in [KnownTargetSignature.manufacturerDataPrefixes]
 * - Behavior profile: moving average RSSI, seenCount range, visibleAtStop count
 *
 * Threading: calls [KnownTargetRepository.save] (blocking). Dispatch to Dispatchers.IO.
 */
class RefineKnownTargetFromObservationUseCase @Inject constructor(
    private val repository: KnownTargetRepository
) {
    private val maxPrefixes = 5

    operator fun invoke(
        existing: KnownTargetSignature,
        device: ObservedDevice,
        visibleAtStop: Boolean = false
    ): KnownTargetSignature {
        val newPrefixes = extractPrefixesFromFingerprintMap(device.fingerprint?.manufacturerDataHex)
        val updatedFrequency = mergeFrequency(existing.manufacturerDataPrefixFrequency, newPrefixes)
        val topPrefixes = updatedFrequency.entries
            .sortedByDescending { it.value }
            .take(maxPrefixes)
            .map { it.key }

        val now = System.currentTimeMillis()
        val merged = existing.copy(
            lastUpdatedAt = now,
            savedAt = now,
            manufacturerIds = (existing.manufacturerIds + (device.fingerprint?.manufacturerIds ?: emptyList())).distinct(),
            manufacturerDataPrefixes = topPrefixes,
            manufacturerDataPrefixFrequency = updatedFrequency,
            serviceUuids = (existing.serviceUuids + (device.fingerprint?.serviceUuids ?: emptyList())).distinct(),
            behaviorProfile = mergedBehaviorProfile(existing, device, visibleAtStop),
            learnCount = existing.learnCount + 1
        )

        Log.d(RTAG, "Signature refined from live scan: learnCount=${merged.learnCount} " +
            "avgRssi=${merged.behaviorProfile?.typicalRssiAtClose} " +
            "prefixes=${merged.manufacturerDataPrefixes}")

        repository.save(merged)
        return merged
    }

    private fun mergeFrequency(existing: Map<String, Int>, newPrefixes: List<String>): Map<String, Int> {
        val updated = existing.toMutableMap()
        newPrefixes.forEach { prefix -> updated[prefix] = (updated[prefix] ?: 0) + 1 }
        return updated
    }

    private fun mergedBehaviorProfile(
        existing: KnownTargetSignature,
        device: ObservedDevice,
        visibleAtStop: Boolean
    ): KnownBehaviorProfile {
        val current = existing.behaviorProfile
        val sampleCount = (current?.rssiSampleCount ?: 0).coerceAtLeast(1)
        val currentAvg = current?.typicalRssiAtClose ?: device.averagedRssi
        val newAvg = ((currentAvg.toLong() * sampleCount) + device.averagedRssi) / (sampleCount + 1)

        return KnownBehaviorProfile(
            typicalRssiAtClose = newAvg.toInt(),
            rssiSampleCount = sampleCount + 1,
            minSeenCount = minOf(current?.minSeenCount ?: device.seenCount, device.seenCount),
            maxSeenCount = maxOf(current?.maxSeenCount ?: device.seenCount, device.seenCount),
            visibleAtStopCount = (current?.visibleAtStopCount ?: 0) + if (visibleAtStop) 1 else 0,
            totalObservations = (current?.totalObservations ?: 0) + 1
        )
    }
}
