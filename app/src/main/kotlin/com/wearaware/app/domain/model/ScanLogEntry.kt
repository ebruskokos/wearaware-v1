package com.wearaware.app.domain.model

/**
 * PURPOSE: Domain representation of a single session log entry. Mirrors ScanLogEntity
 *   but without Room annotations, keeping domain layer free of Android dependencies.
 * LIMITATIONS: Enum fields are stored as String names (not ordinals) for readability
 *   and forward compatibility.
 * NOTES: evaluationNotes is stored to enable future rule tuning and debugging analysis.
 */
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
    val evaluationNotes: String?
)
