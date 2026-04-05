package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.DeviceTemporalState
import com.wearaware.app.domain.model.KnownBehaviorProfile
import com.wearaware.app.domain.model.KnownTargetSignature
import com.wearaware.app.domain.model.ObservedDevice
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Adaptively refines a [KnownTargetSignature] from a live [ObservedDevice] observation
 * while guarding against data drift and signal noise.
 *
 * **Conditions for refinement:**
 * - RSSI variance (σ) < [VARIANCE_THRESHOLD] — signal must be stable
 * - RSSI deviation from learned average ≤ [MAX_RSSI_DRIFT] — no drastic environment shift
 * - Device must have at least one manufacturer ID already in the signature (structural anchor)
 *
 * **Drift protection (what is NOT updated):**
 * - Manufacturer IDs: no new IDs ever added (drift protection)
 * - Service UUIDs / GATT UUIDs / characteristic values: unchanged (only full learn sessions)
 * - Manufacturer data prefixes: only prefixes whose company ID is already in manufacturerIds
 * - RSSI bounds (rssiMin/rssiMax): clamped to [MAX_RSSI_RANGE] from the learned average
 *
 * **What IS updated on each accepted observation:**
 * - Manufacturer data prefix frequencies (filtered to known IDs)
 * - Moving average RSSI (Welford)
 * - rssiMin / rssiMax rolling bounds
 * - seenCount range (min/max)
 * - observationCount (incremented)
 * - version (incremented)
 * - lastUpdatedAt
 *
 * Pure function — no I/O. The caller (ScanViewModel) is responsible for calling
 * [com.wearaware.app.domain.repository.KnownTargetRepository.save].
 */
class AdaptiveSignatureRefineUseCase @Inject constructor() {

    companion object {
        /** Max RSSI standard deviation (dBm) allowed during a refinement tick. */
        const val VARIANCE_THRESHOLD = 6.0
        /** Max acceptable RSSI deviation from learned average before skipping refinement. */
        const val MAX_RSSI_DRIFT = 15
        /** Max span between rssiMin and rssiMax to prevent runaway bounds. */
        const val MAX_RSSI_RANGE = 40
        private const val MAX_PREFIXES = 5
    }

    data class AdaptiveRefineResult(
        /**
         * Updated signature when [wasRefined] is true; null when refinement was skipped.
         * Callers should fall back to the original signature when this is null.
         */
        val signature: KnownTargetSignature?,
        /** True when the signature was actually updated. */
        val wasRefined: Boolean,
        /** Human-readable reason refinement was skipped. Null when wasRefined = true. */
        val skippedReason: String? = null,
        /** Short description of what changed. Null when wasRefined = false. */
        val deltaDescription: String? = null
    )

    operator fun invoke(
        existing: KnownTargetSignature,
        device: ObservedDevice,
        temporalState: DeviceTemporalState,
        /**
         * When true (Relearn Mode), variance and drift guards are bypassed to allow faster
         * signature updates. The structural anchor guard is always enforced.
         */
        relearnMode: Boolean = false
    ): AdaptiveRefineResult {

        // Guard 1: RSSI variance check — skip in relearn mode
        if (!relearnMode) {
            val rssiHistory = temporalState.rssiHistory
            val variance = if (rssiHistory.size >= 3) computeVariance(rssiHistory) else Double.MAX_VALUE
            val stdDev = sqrt(variance)
            if (stdDev >= VARIANCE_THRESHOLD) {
                return skip("High RSSI variance σ=${stdDev.fmt()} dBm — waiting for stable signal")
            }
        }

        // Guard 2: RSSI drift check — skip in relearn mode
        val learnedAvg = existing.behaviorProfile?.typicalRssiAtClose ?: device.averagedRssi
        if (!relearnMode) {
            val rssiDrift = abs(device.averagedRssi - learnedAvg)
            if (rssiDrift > MAX_RSSI_DRIFT) {
                return skip("RSSI drift ${rssiDrift} dBm exceeds limit ${MAX_RSSI_DRIFT} dBm")
            }
        }

        // Guard 3: structural anchor — device must share at least one manufacturer ID
        val deviceIds = device.fingerprint?.manufacturerIds ?: emptyList()
        val hasAnchor = deviceIds.any { it in existing.manufacturerIds }
        if (!hasAnchor && existing.manufacturerIds.isNotEmpty()) {
            return skip("No manufacturer ID overlap — structural anchor required")
        }

        // Apply refinement
        val deltas = mutableListOf<String>()

        // Prefix frequency update — only prefixes whose company ID is in existing manufacturerIds
        val rawPrefixes = extractPrefixesFromFingerprintMap(device.fingerprint?.manufacturerDataHex)
        val filteredPrefixes = rawPrefixes.filter { prefix ->
            val companyHex = prefix.substringBefore(':')
            val companyId = companyHex.toIntOrNull(16)
            companyId != null && companyId in existing.manufacturerIds
        }
        val updatedFrequency = existing.manufacturerDataPrefixFrequency.toMutableMap()
        filteredPrefixes.forEach { prefix -> updatedFrequency[prefix] = (updatedFrequency[prefix] ?: 0) + 1 }
        val topPrefixes = updatedFrequency.entries
            .sortedByDescending { it.value }
            .take(MAX_PREFIXES)
            .map { it.key }
        if (topPrefixes != existing.manufacturerDataPrefixes) {
            deltas += "prefixes updated (${filteredPrefixes.size} new observations)"
        }

        // Behavior profile — Welford RSSI average + bounds + seenCount range
        val updatedProfile = updateBehaviorProfile(existing, device, deltas)

        val newVersion = existing.version.coerceAtLeast(1) + 1
        val newObservationCount = existing.observationCount + 1

        val refined = existing.copy(
            lastUpdatedAt = System.currentTimeMillis(),
            manufacturerDataPrefixes = topPrefixes,
            manufacturerDataPrefixFrequency = updatedFrequency,
            behaviorProfile = updatedProfile,
            version = newVersion,
            observationCount = newObservationCount
        )

        val delta = if (deltas.isEmpty()) "RSSI avg updated" else deltas.joinToString("; ")
        return AdaptiveRefineResult(
            signature = refined,
            wasRefined = true,
            deltaDescription = "v$newVersion obs#$newObservationCount — $delta"
        )
    }

    private fun updateBehaviorProfile(
        existing: KnownTargetSignature,
        device: ObservedDevice,
        deltas: MutableList<String>
    ): KnownBehaviorProfile {
        val current = existing.behaviorProfile
        val sampleCount = (current?.rssiSampleCount ?: 0).coerceAtLeast(1)
        val currentAvg = current?.typicalRssiAtClose ?: device.averagedRssi
        val newAvg = ((currentAvg.toLong() * sampleCount) + device.averagedRssi) / (sampleCount + 1)

        // RSSI bounds — clamped to prevent runaway range
        val learnedAvg = currentAvg
        val clampedRssi = device.averagedRssi.coerceIn(learnedAvg - MAX_RSSI_DRIFT, learnedAvg + MAX_RSSI_DRIFT)
        val existingMin = current?.rssiMin ?: currentAvg
        val existingMax = current?.rssiMax ?: currentAvg
        var newMin = minOf(existingMin, clampedRssi)
        var newMax = maxOf(existingMax, clampedRssi)
        // Clamp range span
        if (newMax - newMin > MAX_RSSI_RANGE) {
            val mid = (newMax + newMin) / 2
            newMin = mid - MAX_RSSI_RANGE / 2
            newMax = mid + MAX_RSSI_RANGE / 2
        }

        if (newAvg.toInt() != currentAvg) {
            deltas += "avgRSSI ${currentAvg}→${newAvg.toInt()} dBm"
        }

        return KnownBehaviorProfile(
            typicalRssiAtClose = newAvg.toInt(),
            rssiSampleCount = sampleCount + 1,
            minSeenCount = minOf(current?.minSeenCount ?: device.seenCount, device.seenCount),
            maxSeenCount = maxOf(current?.maxSeenCount ?: device.seenCount, device.seenCount),
            visibleAtStopCount = current?.visibleAtStopCount ?: 0,
            totalObservations = (current?.totalObservations ?: 0) + 1,
            rssiMin = newMin,
            rssiMax = newMax
        )
    }

    private fun computeVariance(values: List<Int>): Double {
        val mean = values.average()
        return values.map { v -> (v - mean).let { it * it } }.average()
    }

    private fun skip(reason: String) = AdaptiveRefineResult(
        signature = null,
        wasRefined = false,
        skippedReason = reason
    )

    private fun Double.fmt() = "%.1f".format(this)
}
