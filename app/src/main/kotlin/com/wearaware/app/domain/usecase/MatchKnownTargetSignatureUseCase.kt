package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import javax.inject.Inject

/**
 * Scores a candidate device against a saved KnownTargetSignature.
 *
 * BONUSES:
 *   +6 any manufacturerId overlap (flat)
 *   +5 per manufacturer data prefix match
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

        // +6 flat bonus for any manufacturerId overlap
        val sharedIds = input.manufacturerIds.intersect(signature.manufacturerIds.toSet())
        if (sharedIds.isNotEmpty()) {
            score += 6
            val hex = sharedIds.joinToString { "0x${it.toString(16).uppercase().padStart(4, '0')}" }
            signals += "Manufacturer ID overlap: $hex (+6)"
        }

        // +5 per manufacturer data prefix match
        val matchedPrefixes = input.manufacturerDataPrefixes.intersect(signature.manufacturerDataPrefixes.toSet())
        for (prefix in matchedPrefixes) {
            score += 5
            signals += "Manufacturer data prefix match: $prefix (+5)"
        }

        // +4 per service UUID overlap
        val sharedUuids = input.serviceUuids.intersect(signature.serviceUuids.toSet())
        for (uuid in sharedUuids) {
            score += 4
            signals += "Service UUID match: $uuid (+4)"
        }

        // +3 per GATT service UUID overlap
        val sharedGattUuids = input.gattServiceUuids.intersect(signature.gattServiceUuids.toSet())
        for (uuid in sharedGattUuids) {
            score += 3
            signals += "GATT service match: $uuid (+3)"
        }

        // +3 if fingerprintId matches exactly
        if (input.fingerprintId == signature.fingerprintId) {
            score += 3
            signals += "FingerprintId exact match (+3)"
        }

        // +3 if close proximity
        if (input.averageRssi >= -60) {
            score += 3
            signals += "Close proximity: ${input.averageRssi} dBm (+3)"
        }

        // +3 if high persistence
        if (input.seenCount >= 50) {
            score += 3
            signals += "High persistence: ${input.seenCount} observations (+3)"
        }

        // +2 if visible at stop
        if (input.visibleAtStop) {
            score += 2
            signals += "Still visible at capture stop (+2)"
        }

        // +2 if connectable
        if (input.connectable) {
            score += 2
            signals += "Device is connectable (+2)"
        }

        // --- Penalties ---

        // -5 Apple-only noise filter
        if (input.manufacturerIds.isNotEmpty() && input.manufacturerIds.all { it == 0x004C }) {
            score -= 5
            signals += "Apple-only manufacturer ID — noise filter (-5)"
        }

        // -4 very weak signal
        if (input.averageRssi < -80) {
            score -= 4
            signals += "Very weak signal: ${input.averageRssi} dBm (-4)"
        }

        // -3 very low persistence
        if (input.seenCount < 5) {
            score -= 3
            signals += "Very low persistence: ${input.seenCount} observations (-3)"
        }

        val confidence = when {
            score >= 8  -> KnownMatchConfidence.STRONG
            score >= 4  -> KnownMatchConfidence.POSSIBLE
            score >= 2  -> KnownMatchConfidence.WEAK
            else        -> KnownMatchConfidence.NONE
        }

        return KnownTargetMatchResult(
            signature = signature,
            score = score,
            confidence = confidence,
            matchedSignals = signals,
            labelOverrideActive = confidence == KnownMatchConfidence.STRONG || confidence == KnownMatchConfidence.POSSIBLE
        )
    }
}
