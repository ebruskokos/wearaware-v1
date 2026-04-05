package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import javax.inject.Inject

/**
 * Scores a candidate device against a saved KnownTargetSignature.
 *
 * BONUSES:
 *   +6 any manufacturerId overlap (flat)
 *   +5 per manufacturer data prefix match (first match)
 *   +1 per additional prefix match beyond the first (consistency bonus, max +3)
 *   +4 per service UUID overlap
 *   +3 per GATT service UUID overlap
 *   +3 if fingerprintId exactly matches
 *   +3 if averageRssi >= -60 dBm
 *   +3 if seenCount >= 50
 *   +2 if visibleAtStop = true
 *   +2 if connectable = true
 *
 * PENALTIES:
 *   -5 if ALL manufacturerIds == Apple (0x004C) — Apple noise filter
 *   -4 if averageRssi < -80 dBm
 *   -3 if seenCount < 5
 *
 * THRESHOLDS: STRONG >= 8 | POSSIBLE >= 4 | WEAK >= 2 | NONE < 2
 * labelOverrideActive = true for STRONG and POSSIBLE
 */
class MatchKnownTargetSignatureUseCase @Inject constructor() {

    operator fun invoke(
        input: KnownTargetMatchInput,
        signature: KnownTargetSignature
    ): KnownTargetMatchResult {
        var score = 0
        val signals = mutableListOf<String>()
        val breakdown = mutableListOf<ScoreBreakdownItem>()

        // +6 flat bonus for any manufacturerId overlap
        val sharedIds = input.manufacturerIds.intersect(signature.manufacturerIds.toSet())
        if (sharedIds.isNotEmpty()) {
            score += 6
            val hex = sharedIds.joinToString { "0x${it.toString(16).uppercase().padStart(4, '0')}" }
            signals += "Manufacturer ID overlap: $hex (+6)"
            breakdown += ScoreBreakdownItem(ScoreCategory.MANUFACTURER_ID, 6, "ID overlap: $hex")
        }

        // +5 for first manufacturer data prefix match; +1 per additional (consistency, max +3)
        val matchedPrefixes = input.manufacturerDataPrefixes.intersect(signature.manufacturerDataPrefixes.toSet())
        matchedPrefixes.forEachIndexed { index, prefix ->
            val points = if (index == 0) 5 else minOf(1, 3 - index + 1).coerceAtLeast(0)
            if (points > 0) {
                score += points
                val label = if (index == 0) "+5" else "+1 consistency"
                signals += "Manufacturer data prefix match: $prefix ($label)"
                breakdown += ScoreBreakdownItem(
                    ScoreCategory.PREFIX_MATCH, points,
                    if (index == 0) "Prefix: $prefix" else "Prefix consistency: $prefix"
                )
            }
        }

        // +4 per service UUID overlap
        val sharedUuids = input.serviceUuids.intersect(signature.serviceUuids.toSet())
        for (uuid in sharedUuids) {
            score += 4
            signals += "Service UUID match: $uuid (+4)"
            breakdown += ScoreBreakdownItem(ScoreCategory.SERVICE_UUID, 4, "UUID: $uuid")
        }

        // +3 per GATT service UUID overlap
        val sharedGattUuids = input.gattServiceUuids.intersect(signature.gattServiceUuids.toSet())
        for (uuid in sharedGattUuids) {
            score += 3
            signals += "GATT service match: $uuid (+3)"
            breakdown += ScoreBreakdownItem(ScoreCategory.GATT_UUID, 3, "GATT: $uuid")
        }

        // +3 if fingerprintId matches exactly
        if (input.fingerprintId == signature.fingerprintId) {
            score += 3
            signals += "FingerprintId exact match (+3)"
            breakdown += ScoreBreakdownItem(ScoreCategory.FINGERPRINT_ID, 3, "Fingerprint ID exact match")
        }

        // +3 if close proximity
        if (input.averageRssi >= -60) {
            score += 3
            signals += "Close proximity: ${input.averageRssi} dBm (+3)"
            breakdown += ScoreBreakdownItem(ScoreCategory.PROXIMITY, 3, "Close proximity: ${input.averageRssi} dBm")
        }

        // +3 if high persistence
        if (input.seenCount >= 50) {
            score += 3
            signals += "High persistence: ${input.seenCount} observations (+3)"
            breakdown += ScoreBreakdownItem(ScoreCategory.PERSISTENCE, 3, "High persistence: ${input.seenCount} obs")
        }

        // +2 if visible at stop
        if (input.visibleAtStop) {
            score += 2
            signals += "Still visible at capture stop (+2)"
            breakdown += ScoreBreakdownItem(ScoreCategory.VISIBLE_AT_STOP, 2, "Visible at capture stop")
        }

        // +2 if connectable
        if (input.connectable) {
            score += 2
            signals += "Device is connectable (+2)"
            breakdown += ScoreBreakdownItem(ScoreCategory.CONNECTABLE, 2, "Device is connectable")
        }

        // --- Penalties ---

        val inputIsAppleOnly = input.manufacturerIds.isNotEmpty() &&
            input.manufacturerIds.all { it == 0x004C }
        val signatureHasNonApple = signature.manufacturerIds.any { it != 0x004C }

        if (inputIsAppleOnly) {
            if (signatureHasNonApple) {
                // Learned device is non-Apple; Apple-only candidate is definitely wrong device
                score -= 12
                signals += "Apple-only device vs non-Apple signature — hard disqualification (-12)"
                breakdown += ScoreBreakdownItem(ScoreCategory.APPLE_PENALTY, -12, "Apple-only vs non-Apple signature")
            } else {
                // Learned device might legitimately be Apple; standard noise penalty
                score -= 5
                signals += "Apple-only manufacturer ID — noise filter (-5)"
                breakdown += ScoreBreakdownItem(ScoreCategory.APPLE_PENALTY, -5, "Apple-only noise filter")
            }
        }

        // -4 very weak signal
        if (input.averageRssi < -80) {
            score -= 4
            signals += "Very weak signal: ${input.averageRssi} dBm (-4)"
            breakdown += ScoreBreakdownItem(ScoreCategory.WEAK_SIGNAL, -4, "Very weak signal: ${input.averageRssi} dBm")
        }

        // -3 very low persistence
        if (input.seenCount < 5) {
            score -= 3
            signals += "Very low persistence: ${input.seenCount} observations (-3)"
            breakdown += ScoreBreakdownItem(ScoreCategory.LOW_PERSISTENCE, -3, "Very low persistence: ${input.seenCount} obs")
        }

        // Structural signals: fingerprint-based evidence (not purely proximity/persistence).
        // STRONG/POSSIBLE require at least one structural signal to avoid false positives
        // from any nearby persistent device (e.g. always-nearby phone).
        val hasStructuralSignal = sharedIds.isNotEmpty() ||
            matchedPrefixes.isNotEmpty() ||
            sharedUuids.isNotEmpty() ||
            sharedGattUuids.isNotEmpty() ||
            input.fingerprintId == signature.fingerprintId

        val confidence = when {
            score >= 8 && hasStructuralSignal -> KnownMatchConfidence.STRONG
            score >= 4 && hasStructuralSignal -> KnownMatchConfidence.POSSIBLE
            score >= 2 -> KnownMatchConfidence.WEAK
            else -> KnownMatchConfidence.NONE
        }

        return KnownTargetMatchResult(
            signature = signature,
            score = score,
            confidence = confidence,
            matchedSignals = signals,
            labelOverrideActive = confidence == KnownMatchConfidence.STRONG || confidence == KnownMatchConfidence.POSSIBLE,
            hasStructuralSignal = hasStructuralSignal,
            scoreBreakdown = breakdown
        )
    }
}
