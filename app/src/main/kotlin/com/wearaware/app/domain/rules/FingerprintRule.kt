package com.wearaware.app.domain.rules

import com.wearaware.app.domain.model.ConfidenceLevel
import com.wearaware.app.domain.model.DeviceCategory

/**
 * PURPOSE: Domain model for a single device classification rule loaded from
 *   fingerprint_rules.json. Used exclusively by FingerprintClassifier.
 * LIMITATIONS: Rules are evaluated at classification time — not pre-indexed.
 *   For large rule sets (100+), consider an indexed data structure.
 * NOTES: manufacturerIds are stored as normalized Int values (hex strings parsed
 *   during JSON loading, not here). namePatterns are stored as-is; matching is
 *   case-insensitive in FingerprintClassifier.
 */
data class FingerprintRule(
    val ruleId: String,
    val enabled: Boolean,
    val priority: Int,
    /** Minimum score required for this rule to produce a match. Per-rule tuning. */
    val minScore: Int,
    /** Company IDs as integers (0x0075 = 117). Compared against manufacturer data keys. */
    val manufacturerIds: List<Int>,
    /** Name substrings to match against advertised device name (case-insensitive). */
    val namePatterns: List<String>,
    /** Service UUIDs to match. Compared after normalization to lowercase. */
    val serviceUuids: List<String>,
    /** Optional hint for future UI icon selection. Not used in v1.0 classification logic. */
    val deviceTypeHint: String?,
    val category: DeviceCategory,
    val displayLabel: String,
    val confidence: ConfidenceLevel,
    val isWearableCandidate: Boolean
)

/**
 * Metadata about the rule set file — version and integrity hash.
 * Recorded in session log entries alongside classification results.
 */
data class RuleSetMetadata(
    val version: String,
    val hash: String
)
