package com.wearaware.app.data.rules.dto

import com.google.gson.annotations.SerializedName

/**
 * PURPOSE: Gson DTO for the top-level structure of fingerprint_rules.json.
 * NOTES: Kept in data layer — domain never sees these classes.
 */
data class RulesDataDto(
    @SerializedName("rule_set_version") val ruleSetVersion: String,
    @SerializedName("rule_set_hash") val ruleSetHash: String,
    @SerializedName("rules") val rules: List<FingerprintRuleDto>
)
