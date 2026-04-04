package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import javax.inject.Inject

object DefaultTargetProfile {
    val WAYFARER_00ZS = TargetDeviceProfile(
        friendlyName = "Wayfarer 00ZS",
        modelHint = "Wayfarer",
        brandHint = "Meta",
        category = DeviceCategory.SMART_GLASSES
    )
}

/**
 * PURPOSE: Scores each ObservedDevice against a TargetDeviceProfile.
 *
 * Scoring (additive):
 *   +6  exact advertised name match ("Wayfarer 00ZS")
 *   +4  Bluetooth device name match (cached/bonded)
 *   +3  partial name match containing modelHint ("Wayfarer")
 *   +3  Meta manufacturer match (matchedRuleId contains "meta" or "rayban")
 *   +3  SMART_GLASSES classification
 *   +3  CAMERA_CAPABLE_WEARABLE classification
 *   +2  service UUID pattern match (any UUID in serviceUuids)
 *   +1  RSSI bonus (NEARBY or closer)
 *   +1  persistence bonus (>= 30 seconds)
 *
 * Confidence: HIGH >= 9 | MEDIUM >= 6 | LOW >= 3 | NONE < 3
 * isTopCandidate: set for single highest-scoring device with confidence != NONE.
 *
 * NOTES: Never claims certainty. "isTopCandidate" means "best available candidate",
 *   not "confirmed match". BLE data is incomplete by nature.
 */
class MatchTargetDeviceUseCase @Inject constructor() {

    operator fun invoke(
        devices: List<ObservedDevice>,
        profile: TargetDeviceProfile
    ): Map<String, TargetMatchResult> {
        if (devices.isEmpty()) return emptyMap()

        val rawResults = devices.associate { it.id to scoreDevice(it, profile) }

        val topId = rawResults.values
            .filter { it.confidence != MatchConfidence.NONE }
            .maxByOrNull { it.score }
            ?.deviceId

        return rawResults.mapValues { (id, result) ->
            result.copy(isTopCandidate = id == topId && topId != null)
        }
    }

    private fun scoreDevice(device: ObservedDevice, profile: TargetDeviceProfile): TargetMatchResult {
        var score = 0
        val signals = mutableListOf<String>()

        // Exact advertised name match (+9)
        if (device.advertisedName?.equals(profile.friendlyName, ignoreCase = true) == true) {
            score += 9
            signals += "Exact name match: \"${device.advertisedName}\""
        } else if (device.advertisedName?.contains(profile.modelHint, ignoreCase = true) == true) {
            // Partial name match — modelHint (+6)
            score += 6
            signals += "Partial name match (model): \"${device.advertisedName}\""
        }

        // Fingerprint normalizedName (covers bluetoothDeviceName via FingerprintBuilder)
        val normalizedFpName = device.fingerprint?.normalizedName
        if (normalizedFpName != null &&
            device.advertisedName?.equals(profile.friendlyName, ignoreCase = true) != true) {
            when {
                normalizedFpName.equals(profile.friendlyName, ignoreCase = true) -> {
                    score += 4
                    signals += "Bluetooth device name match: \"$normalizedFpName\""
                }
                normalizedFpName.contains(profile.modelHint, ignoreCase = true) -> {
                    score += 2
                    signals += "Bluetooth device name partial match: \"$normalizedFpName\""
                }
            }
        }

        // SMART_GLASSES classification (+3)
        if (device.classification.category == DeviceCategory.SMART_GLASSES) {
            score += 3
            signals += "Classification: SMART_GLASSES"
        } else if (device.classification.category == DeviceCategory.CAMERA_CAPABLE_WEARABLE) {
            // CAMERA_CAPABLE_WEARABLE (+3)
            score += 3
            signals += "Classification: CAMERA_CAPABLE_WEARABLE"
        }

        // Meta manufacturer match (+3)
        val ruleId = device.classification.matchedRuleId?.lowercase() ?: ""
        if (ruleId.contains("meta") || ruleId.contains("rayban")) {
            score += 3
            signals += "Manufacturer match: Meta/Ray-Ban rule (${device.classification.matchedRuleId})"
        }

        // Service UUID pattern match (+2) — any UUID present is a signal
        if (device.fingerprint?.serviceUuids?.isNotEmpty() == true) {
            score += 2
            signals += "Service UUIDs present: ${device.fingerprint.serviceUuids.size} UUID(s)"
        }

        // RSSI bonus (+1 if NEARBY or closer)
        if (device.proximityLabel == ProximityLabel.NEARBY ||
            device.proximityLabel == ProximityLabel.STRONG ||
            device.proximityLabel == ProximityLabel.VERY_CLOSE) {
            score += 1
            signals += "Signal: ${device.proximityLabel.name} (${device.averagedRssi} dBm)"
        }

        // Persistence bonus (+1 if present >= 30s)
        if (device.seenDurationMs >= 30_000L) {
            score += 1
            signals += "Persistent: present for ${device.seenDurationMs / 1000}s"
        }

        val confidence = when {
            score >= 9 -> MatchConfidence.HIGH
            score >= 6 -> MatchConfidence.MEDIUM
            score >= 3 -> MatchConfidence.LOW
            else       -> MatchConfidence.NONE
        }

        return TargetMatchResult(
            deviceId = device.id,
            score = score,
            confidence = confidence,
            matchedSignals = signals,
            isTopCandidate = false
        )
    }
}
