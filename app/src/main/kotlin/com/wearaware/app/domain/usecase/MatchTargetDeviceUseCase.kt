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
 * ELIGIBILITY GATE: A device must have at least one identity signal before any scoring occurs.
 *   Identity signals: name match, Meta manufacturer match, SMART_GLASSES or CAMERA_CAPABLE_WEARABLE.
 *   Devices with no identity signal are forced to NONE and excluded from top-candidate selection.
 *   This prevents generic BLE devices from winning via bonus-only accumulation.
 *
 * Scoring (additive, only for eligible devices):
 *   +9  exact advertised name match ("Wayfarer 00ZS")
 *   +6  partial name match containing modelHint ("Wayfarer")
 *   +4  Bluetooth device name exact match (cached/bonded)
 *   +2  Bluetooth device name partial match
 *   +3  SMART_GLASSES classification
 *   +3  CAMERA_CAPABLE_WEARABLE classification
 *   +3  Meta manufacturer match (matchedRuleId or companyNames contains "Meta"/"Ray-Ban")
 *   +1  service UUID weak bonus (bonus only — cannot alone satisfy eligibility)
 *   +1  RSSI bonus (NEARBY or closer)
 *   +1  persistence bonus (>= 30 seconds)
 *
 * Confidence: HIGH >= 9 | MEDIUM >= 6 | LOW >= 3 | NONE < 3 (or ineligible)
 * isTopCandidate: set only for the single highest-scoring MEDIUM or HIGH device.
 *
 * NOTES: Never claims certainty. "isTopCandidate" means "best current candidate",
 *   not "confirmed match". BLE advertising data is incomplete by nature.
 */
class MatchTargetDeviceUseCase @Inject constructor() {

    operator fun invoke(
        devices: List<ObservedDevice>,
        profile: TargetDeviceProfile
    ): Map<String, TargetMatchResult> {
        if (devices.isEmpty()) return emptyMap()

        val rawResults = devices.associate { it.id to scoreDevice(it, profile) }

        // Only MEDIUM or HIGH confidence devices can become top candidate
        val topId = rawResults.values
            .filter { it.confidence == MatchConfidence.HIGH || it.confidence == MatchConfidence.MEDIUM }
            .maxByOrNull { it.score }
            ?.deviceId

        return rawResults.mapValues { (id, result) ->
            result.copy(isTopCandidate = topId != null && id == topId)
        }
    }

    /**
     * Returns true if the device has at least one identity signal for the given profile.
     * Persistence, UUID presence, and RSSI are NOT identity signals — they are bonus-only.
     */
    private fun hasIdentitySignal(device: ObservedDevice, profile: TargetDeviceProfile): Boolean {
        // Name signals (advertised or cached BT device name)
        if (device.advertisedName?.contains(profile.modelHint, ignoreCase = true) == true) return true
        if (device.fingerprint?.normalizedName?.contains(profile.modelHint, ignoreCase = true) == true) return true

        // Meta manufacturer match via fingerprint classifier rule
        val ruleId = device.classification.matchedRuleId?.lowercase() ?: ""
        if (ruleId.contains("meta") || ruleId.contains("rayban")) return true

        // Meta manufacturer match via BLE company ID (0x0075 → "Meta" in CompanyIdMap)
        if (device.companyNames.any { it.contains("Meta", ignoreCase = true) }) return true

        // Smart-glasses or camera-capable classification
        if (device.classification.category == DeviceCategory.SMART_GLASSES) return true
        if (device.classification.category == DeviceCategory.CAMERA_CAPABLE_WEARABLE) return true

        return false
    }

    private fun scoreDevice(device: ObservedDevice, profile: TargetDeviceProfile): TargetMatchResult {
        // Eligibility gate: no identity signal → force NONE, score 0
        if (!hasIdentitySignal(device, profile)) {
            return TargetMatchResult(
                deviceId = device.id,
                score = 0,
                confidence = MatchConfidence.NONE,
                matchedSignals = emptyList(),
                isTopCandidate = false
            )
        }

        var score = 0
        val signals = mutableListOf<String>()

        // Exact advertised name match (+9)
        if (device.advertisedName?.equals(profile.friendlyName, ignoreCase = true) == true) {
            score += 9
            signals += "Exact name match: \"${device.advertisedName}\""
        } else if (device.advertisedName?.contains(profile.modelHint, ignoreCase = true) == true) {
            score += 6
            signals += "Partial name match (model): \"${device.advertisedName}\""
        }

        // Fingerprint normalizedName / cached BT device name
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
            score += 3
            signals += "Classification: CAMERA_CAPABLE_WEARABLE"
        }

        // Meta manufacturer match (+3) — rule ID takes precedence over company name to avoid double-counting
        val ruleId = device.classification.matchedRuleId?.lowercase() ?: ""
        when {
            ruleId.contains("meta") || ruleId.contains("rayban") -> {
                score += 3
                signals += "Manufacturer match: Meta/Ray-Ban rule (${device.classification.matchedRuleId})"
            }
            device.companyNames.any { it.contains("Meta", ignoreCase = true) } -> {
                score += 3
                signals += "Manufacturer match: Meta (company ID)"
            }
        }

        // Service UUID weak bonus (+1) — bonus only, cannot satisfy eligibility alone
        if (device.fingerprint?.serviceUuids?.isNotEmpty() == true) {
            score += 1
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
