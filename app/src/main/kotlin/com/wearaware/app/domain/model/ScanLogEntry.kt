package com.wearaware.app.domain.model

data class ScanLogEntry(
    val id: Long = 0,
    val timestamp: Long,
    val deviceId: String,
    val advertisedName: String?,
    val rawRssi: Int,
    val averagedRssi: Int,
    val proximityLabel: String,
    val visibilityState: String,
    val matchedRuleId: String?,
    val ruleVersion: String?,
    val category: String,
    val confidence: String,
    val evaluationNotes: String?,
    val fingerprintId: String? = null,
    val manufacturerIds: String? = null,       // comma-separated hex e.g. "0075,004c"
    val targetMatchScore: Int? = null,
    val targetMatchReason: String? = null,
    val isTopCandidate: Boolean = false,
    val manufacturerDataHex: String? = null,   // "companyId:hex,companyId:hex" e.g. "0075:deadbeef"
    val serviceUuids: String? = null,          // comma-separated UUID list
    val txPower: Int? = null,
    val connectable: Boolean = false,
    val rawScanBytesHex: String? = null        // first 32 bytes as hex (truncated for storage)
)
