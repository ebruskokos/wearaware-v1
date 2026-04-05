package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import javax.inject.Inject

private const val META_COMPANY_ID = 0x0075
private const val APPLE_COMPANY_ID = 0x004C
private const val RSSI_DELTA_THRESHOLD = 10
private const val RSSI_CLOSE_THRESHOLD = -60        // close proximity: >= -60 dBm
private const val RSSI_VERY_CLOSE_THRESHOLD = -50   // very close proximity: >= -50 dBm
private const val PERSISTENCE_MEDIUM_THRESHOLD = 50  // high persistence tier 1
private const val PERSISTENCE_HIGH_THRESHOLD = 100   // high persistence tier 2

/**
 * PURPOSE: Compares a baseline and target CaptureSession to rank which devices
 *   most likely appeared because the target device (e.g. Meta glasses) was powered on.
 *
 * DESIGN RATIONALE: Meta Ray-Ban glasses do not reliably expose manufacturer ID (0x0075)
 *   or an advertised name in all BLE scan environments. The scorer therefore weights
 *   behavioral signals (RSSI strength, persistence, proximity+persistence combo) heavily
 *   enough that an unknown device can reach HIGH confidence without any identity signal.
 *   Identity signals (Meta ID, name, classification) remain strong boosters but are not
 *   gating conditions for HIGH confidence.
 *
 * SCORING (additive):
 *   +8  only in target — gated: requires at least one identity signal
 *   +3  only in target — ungated: no identity signal (Apple-only devices suppressed)
 *   +4  very close proximity: averageRssi >= -50 dBm
 *   +4  RSSI delta >= 10 dBm vs baseline
 *   +4  exact target name match (profile.friendlyName)
 *   +3  close proximity: averageRssi >= -60 dBm
 *   +3  Meta manufacturer match (manufacturerIds contains 0x0075) — CANONICAL FIELD
 *   +3  SMART_GLASSES classification
 *   +3  close proximity + persistence combo (RSSI >= -60 AND seenCount >= 50)
 *   +3  high persistence: seenCount >= 100
 *   +2  CAMERA_CAPABLE_WEARABLE classification
 *   +2  partial profile hint (modelHint or brandHint in advertisedName)
 *   +2  high persistence: seenCount >= 50
 *   +2  visibleAtStop: still advertising when capture was stopped
 *   +1  persistence: seenCount >= 5
 *
 * CONFIDENCE: HIGH >= 9 | MEDIUM >= 6 | LOW >= 3 | NONE < 3 (excluded)
 *
 * BEHAVIORAL PATH TO HIGH (no identity signal required):
 *   An unknown device can reach HIGH via:
 *   +3 (only-in-target ungated) + +3 (RSSI >= -60) + +3 (seenCount >= 100) +
 *   +2 (visibleAtStop) + +3 (combo) = 14 → HIGH
 *
 *   Minimum behavioral HIGH (seenCount >= 50, RSSI >= -60, visibleAtStop):
 *   +3 + +3 + +2 + +2 + +3 = 13 → HIGH
 *
 * TOP CANDIDATE: only MEDIUM or HIGH may be highlighted as top candidate in UI.
 *
 * SORT ORDER: score desc → exact name match → Meta manufacturer → RSSI desc → fingerprintId asc
 *
 * NOTES: manufacturerIds is canonical for all logic. companyNames is display-only.
 *   When baseline is null, "only in target" signals never fire (hasBaseline = false).
 *   Apple suppression: Apple-only devices (0x004C, no identity signal) are suppressed
 *   from the ungated "only in target" bonus — they are pervasive environmental noise.
 *   They can still score via RSSI delta if present in both captures.
 */
class CompareCapturesUseCase @Inject constructor() {

    operator fun invoke(
        baseline: CaptureSession?,
        target: CaptureSession,
        profile: TargetDeviceProfile
    ): List<CompareMatchResult> {
        val baselineMap = baseline?.devices?.associateBy { it.fingerprintId } ?: emptyMap()
        val hasBaseline = baseline != null

        return target.devices
            .mapNotNull { targetDevice ->
                val baselineDevice = baselineMap[targetDevice.fingerprintId]
                scoreDevice(targetDevice, baselineDevice, profile, hasBaseline)
                    .takeIf { it.confidence != CompareConfidence.NONE }
            }
            .sortedWith(
                compareByDescending<CompareMatchResult> { it.score }
                    .thenByDescending { hasExactNameSignal(it) }
                    .thenByDescending { hasMetaSignal(it) }
                    .thenByDescending { it.capturedDevice.averageRssi }
                    .thenBy { it.capturedDevice.fingerprintId }
            )
    }

    private fun hasExactNameSignal(result: CompareMatchResult): Boolean =
        result.comparisonSignals.any { it.startsWith("Exact target name match") }

    private fun hasMetaSignal(result: CompareMatchResult): Boolean =
        result.comparisonSignals.any { it.startsWith("Meta manufacturer match") }

    private fun scoreDevice(
        device: CapturedDevice,
        baselineDevice: CapturedDevice?,
        profile: TargetDeviceProfile,
        hasBaseline: Boolean
    ): CompareMatchResult {
        var score = 0
        val signals = mutableListOf<String>()

        // Pre-compute identity signals (needed for "only in target" gate)
        val hasMeta = device.manufacturerIds.contains(META_COMPANY_ID)
        val hasSmartGlasses = device.category == DeviceCategory.SMART_GLASSES
        val hasCameraCapable = device.category == DeviceCategory.CAMERA_CAPABLE_WEARABLE
        val hasExactName = device.advertisedName?.equals(profile.friendlyName, ignoreCase = true) == true
        val hasPartialHint = !hasExactName && (
            device.advertisedName?.contains(profile.modelHint, ignoreCase = true) == true ||
            device.advertisedName?.contains(profile.brandHint, ignoreCase = true) == true
        )
        val hasIdentitySignal = hasMeta || hasSmartGlasses || hasCameraCapable || hasExactName || hasPartialHint

        // "Only in target" signals — require hasBaseline to avoid false positives
        if (baselineDevice == null && hasBaseline) {
            // Apple-only: suppress the ungated bonus. Apple devices are pervasive environmental
            // noise — their appearance in any target scan is not evidence of being the target.
            // They can still score via RSSI delta if present in both captures.
            val isAppleOnly = device.manufacturerIds.contains(APPLE_COMPANY_ID) && !hasIdentitySignal
            if (hasIdentitySignal) {
                score += 8
                signals += "Only appeared when target was powered on"
            } else if (!isAppleOnly) {
                score += 3
                signals += "New device: appeared during target capture (no identity signal)"
            }
        }

        // RSSI delta vs baseline
        if (baselineDevice != null) {
            val delta = device.averageRssi - baselineDevice.averageRssi
            if (delta >= RSSI_DELTA_THRESHOLD) {
                score += 4
                signals += "Signal strength increased by +${delta} dBm in target capture"
            }
        }

        // Strong RSSI — behavioral proximity evidence
        // These fire regardless of whether device was in baseline, rewarding consistent
        // nearby presence. They stack with delta but serve a different purpose.
        if (device.averageRssi >= RSSI_VERY_CLOSE_THRESHOLD) {
            score += 4
            signals += "Very close proximity: ${device.averageRssi} dBm (≥ $RSSI_VERY_CLOSE_THRESHOLD dBm)"
        } else if (device.averageRssi >= RSSI_CLOSE_THRESHOLD) {
            score += 3
            signals += "Close proximity: ${device.averageRssi} dBm (≥ $RSSI_CLOSE_THRESHOLD dBm)"
        }

        // Exact target name match
        if (hasExactName) {
            score += 4
            signals += "Exact target name match: \"${device.advertisedName}\""
        } else if (hasPartialHint) {
            score += 2
            signals += "Partial target profile hint: \"${device.advertisedName}\""
        }

        // Meta manufacturer — uses manufacturerIds (canonical), never companyNames
        if (hasMeta) {
            score += 3
            signals += "Meta manufacturer match (0x0075)"
        }

        // Classification
        if (hasSmartGlasses) {
            score += 3
            signals += "Classification: SMART_GLASSES"
        } else if (hasCameraCapable) {
            score += 2
            signals += "Classification: CAMERA_CAPABLE_WEARABLE"
        }

        // Persistence — tiered: more observations = stronger behavioral evidence
        when {
            device.seenCount >= PERSISTENCE_HIGH_THRESHOLD -> {
                score += 3
                signals += "High persistence: observed ${device.seenCount} times during target capture"
            }
            device.seenCount >= PERSISTENCE_MEDIUM_THRESHOLD -> {
                score += 2
                signals += "High persistence: observed ${device.seenCount} times during target capture"
            }
            device.seenCount >= 5 -> {
                score += 1
                signals += "Persistent: observed ${device.seenCount} times during target capture"
            }
        }

        // visibleAtStop bonus — still advertising when capture stopped (not a brief pass-by)
        if (device.visibleAtStop) {
            score += 2
            signals += "Still advertising when capture stopped"
        }

        // Close proximity + persistence combo — a device that is both nearby and persistent
        // is a strong behavioral candidate even without identity signals
        if (device.averageRssi >= RSSI_CLOSE_THRESHOLD && device.seenCount >= PERSISTENCE_MEDIUM_THRESHOLD) {
            score += 3
            signals += "Close proximity + persistence: strong signal with ${device.seenCount} observations"
        }

        val confidence = when {
            score >= 9 -> CompareConfidence.HIGH
            score >= 6 -> CompareConfidence.MEDIUM
            score >= 3 -> CompareConfidence.LOW
            else       -> CompareConfidence.NONE
        }

        val rssiDelta = if (baselineDevice != null) device.averageRssi - baselineDevice.averageRssi else null

        return CompareMatchResult(
            capturedDevice = device,
            score = score,
            confidence = confidence,
            comparisonSignals = signals,
            seenInBaseline = baselineDevice != null,
            seenInTarget = true,
            baselineAverageRssi = baselineDevice?.averageRssi,
            rssiDelta = rssiDelta,
            hasBaseline = hasBaseline
        )
    }
}
