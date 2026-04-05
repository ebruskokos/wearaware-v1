package com.wearaware.app.domain.usecase

/**
 * Extraction helpers — convert raw device data into the prefix format used by LearnedDeviceSignature.
 */

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
