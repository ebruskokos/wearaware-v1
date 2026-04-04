package com.wearaware.app.data.rules.dto

import com.google.gson.annotations.SerializedName

/**
 * PURPOSE: Gson DTO for a single rule entry in fingerprint_rules.json.
 * NOTES: manufacturer_ids are stored as hex strings (e.g. "0x0075") in JSON.
 *   Parsed to Int in RulesDtoMapper.
 */
data class FingerprintRuleDto(
    @SerializedName("rule_id") val ruleId: String,
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("priority") val priority: Int,
    @SerializedName("min_score") val minScore: Int,
    @SerializedName("manufacturer_ids") val manufacturerIds: List<String>,
    @SerializedName("name_patterns") val namePatterns: List<String>,
    @SerializedName("service_uuids") val serviceUuids: List<String>,
    @SerializedName("device_type_hint") val deviceTypeHint: String?,
    @SerializedName("category") val category: String,
    @SerializedName("display_label") val displayLabel: String,
    @SerializedName("confidence") val confidence: String,
    @SerializedName("is_wearable_candidate") val isWearableCandidate: Boolean
)
