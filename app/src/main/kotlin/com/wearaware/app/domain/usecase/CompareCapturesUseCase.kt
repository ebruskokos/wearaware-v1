package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import javax.inject.Inject

private const val META_COMPANY_ID = 0x0075
private const val RSSI_DELTA_THRESHOLD = 10

/**
 * PURPOSE: Compares a baseline and target CaptureSession to rank which devices
 *   most likely appeared because the target device (e.g. Meta glasses) was powered on.
 *
 * SCORING (additive):
 *   +8  only in target — gated: requires at least one identity signal
 *   +3  only in target — ungated: no identity signal (caps at LOW alone)
 *   +4  RSSI increased >= 10 dBm vs baseline
 *   +4  exact target name match (profile.friendlyName)
 *   +3  Meta manufacturer match (manufacturerIds contains 0x0075) — CANONICAL FIELD
 *   +3  SMART_GLASSES classification
 *   +2  CAMERA_CAPABLE_WEARABLE classification
 *   +2  partial profile hint (modelHint or brandHint in advertisedName)
 *   +1  seenCount >= 5 during target capture
 *
 * CONFIDENCE: HIGH >= 9 | MEDIUM >= 6 | LOW >= 3 | NONE < 3 (excluded)
 *
 * TOP CANDIDATE: only MEDIUM or HIGH may be highlighted as top candidate in UI.
 *
 * SORT ORDER: score desc → exact name match → Meta manufacturer → RSSI desc → fingerprintId asc
 *
 * NOTES: manufacturerIds is canonical for all logic. companyNames is display-only.
 *   When baseline is null, "only in target" signals never fire (hasBaseline = false).
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
            if (hasIdentitySignal) {
                score += 8
                signals += "Only appeared when target was powered on"
            } else {
                score += 3
                signals += "New device: appeared during target capture (no identity signal)"
            }
        }

        // RSSI delta
        if (baselineDevice != null) {
            val delta = device.averageRssi - baselineDevice.averageRssi
            if (delta >= RSSI_DELTA_THRESHOLD) {
                score += 4
                signals += "Signal strength increased by +${delta} dBm in target capture"
            }
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

        // Persistence bonus
        if (device.seenCount >= 5) {
            score += 1
            signals += "Persistent: observed ${device.seenCount} times during target capture"
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
