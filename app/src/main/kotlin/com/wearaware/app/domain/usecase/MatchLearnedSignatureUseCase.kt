package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import javax.inject.Inject

/**
 * Scores a candidate device against a saved LearnedDeviceSignature.
 *
 * SCORING:
 *   +5 per manufacturer data prefix match ("companyIdHex:first4bytesHex")
 *   +4 if any manufacturerId overlaps (flat bonus)
 *   +3 if fingerprintId exactly matches (bonus — never a gate)
 *   +2 per service UUID overlap
 *   +2 if averageRssi >= -60 dBm
 *   +2 if seenCount >= 50
 *   +1 if visibleAtStop = true
 *
 * THRESHOLDS: STRONG >= 8 | POSSIBLE >= 4 | NONE < 4
 */
class MatchLearnedSignatureUseCase @Inject constructor() {

    operator fun invoke(
        input: LearnedMatchInput,
        signature: LearnedDeviceSignature
    ): LearnedMatchResult {
        var score = 0
        val signals = mutableListOf<String>()

        // +5 per manufacturer data prefix match
        val matchedPrefixes = input.manufacturerDataPrefixes
            .intersect(signature.manufacturerDataPrefixes.toSet())
        for (prefix in matchedPrefixes) {
            score += 5
            signals += "Manufacturer data prefix match: $prefix"
        }

        // +4 flat bonus for any manufacturerId overlap
        val sharedIds = input.manufacturerIds.intersect(signature.manufacturerIds.toSet())
        if (sharedIds.isNotEmpty()) {
            score += 4
            val hex = sharedIds.joinToString { "0x${it.toString(16).uppercase().padStart(4, '0')}" }
            signals += "Manufacturer ID overlap: $hex"
        }

        // +3 fingerprintId exact match (bonus only)
        if (input.fingerprintId == signature.fingerprintId) {
            score += 3
            signals += "FingerprintId exact match (bonus)"
        }

        // +2 per service UUID overlap
        val sharedUuids = input.serviceUuids.intersect(signature.serviceUuids.toSet())
        for (uuid in sharedUuids) {
            score += 2
            signals += "Service UUID match: $uuid"
        }

        // Behavior signals — computed fresh, not stored in signature
        if (input.averageRssi >= -60) {
            score += 2
            signals += "Close proximity: ${input.averageRssi} dBm"
        }
        if (input.seenCount >= 50) {
            score += 2
            signals += "High persistence: ${input.seenCount} observations"
        }
        if (input.visibleAtStop) {
            score += 1
            signals += "Still visible at capture stop"
        }

        val confidence = when {
            score >= 8 -> LearnedConfidence.STRONG
            score >= 4 -> LearnedConfidence.POSSIBLE
            else       -> LearnedConfidence.NONE
        }

        return LearnedMatchResult(
            signature = signature,
            score = score,
            confidence = confidence,
            matchedSignals = signals,
            labelOverrideActive = confidence != LearnedConfidence.NONE
        )
    }
}

// --- Extraction helpers ---
// These convert raw device data into the prefix format used by LearnedDeviceSignature.

/**
 * Extracts manufacturer data prefixes from a CapturedDevice.manufacturerDataSummary string.
 * Format of summary: "01ab:deadbeef01234567,004c:aabbccddee"
 * Prefix format returned: "01ab:deadbeef" (company ID hex + colon + first 8 hex chars of data = 4 bytes)
 */
fun extractPrefixesFromSummary(manufacturerDataSummary: String?): List<String> {
    if (manufacturerDataSummary.isNullOrBlank()) return emptyList()
    return manufacturerDataSummary.split(",").mapNotNull { entry ->
        val colonIdx = entry.indexOf(':')
        if (colonIdx == -1) return@mapNotNull null
        val companyId = entry.substring(0, colonIdx).trim()
        val dataHex = entry.substring(colonIdx + 1).trim().take(8)
        if (dataHex.length >= 4) "$companyId:$dataHex" else null
    }
}

/**
 * Extracts manufacturer data prefixes from an ObservedDevice.fingerprint?.manufacturerDataHex map.
 * Map key: Int Company ID. Map value: full hex string of data.
 * Prefix format returned: "01ab:deadbeef" (same format as extractPrefixesFromSummary)
 */
fun extractPrefixesFromFingerprintMap(map: Map<Int, String>?): List<String> {
    if (map.isNullOrEmpty()) return emptyList()
    return map.entries.mapNotNull { (id, hex) ->
        val idHex = id.toString(16).padStart(4, '0')
        val prefix = hex.take(8)
        if (prefix.length >= 4) "$idHex:$prefix" else null
    }
}
