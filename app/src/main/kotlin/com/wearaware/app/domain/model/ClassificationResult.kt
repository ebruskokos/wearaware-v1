package com.wearaware.app.domain.model

/**
 * PURPOSE: Output of the fingerprint rule engine for a single scanned device.
 * LIMITATIONS: Reflects the state of fingerprint_rules.json at rule_set_version.
 *   May change if rules are updated in a future app release.
 * NOTES: matchedRuleId + ruleVersion together uniquely identify the rule snapshot
 *   used for classification — critical for audit traceability.
 */
data class ClassificationResult(
    /** ID of the matched rule from fingerprint_rules.json. Null if no rule matched. */
    val matchedRuleId: String?,
    /** rule_set_version from fingerprint_rules.json at time of classification. */
    val ruleVersion: String?,
    /** Assigned device category. UNKNOWN_BLE_DEVICE if no rule matched. */
    val category: DeviceCategory,
    /** Human-readable label for display in the UI. */
    val displayLabel: String,
    /** Confidence of the classification. LOW matches are suppressed from alerts. */
    val confidence: ConfidenceLevel,
    /**
     * Whether this device is a wearable candidate eligible for persistence alerts.
     * False for UNKNOWN_BLE_DEVICE and LOW confidence matches.
     */
    val isWearableCandidate: Boolean,
    /**
     * Human-readable trace of which fields matched.
     * Example: "manufacturerId matched; name pattern matched"
     * Stored in session log for debugging and future rule tuning.
     */
    val evaluationNotes: String?
)
