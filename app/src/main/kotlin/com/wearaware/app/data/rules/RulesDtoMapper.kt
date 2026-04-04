package com.wearaware.app.data.rules

import com.wearaware.app.data.rules.dto.FingerprintRuleDto
import com.wearaware.app.data.rules.dto.RulesDataDto
import com.wearaware.app.domain.model.ConfidenceLevel
import com.wearaware.app.domain.model.DeviceCategory
import com.wearaware.app.domain.repository.RulesData
import com.wearaware.app.domain.rules.FingerprintRule
import com.wearaware.app.domain.rules.RuleSetMetadata

/**
 * PURPOSE: Maps JSON DTOs to domain models. All type parsing and normalization
 *   happens here — keeping domain models clean and DTO classes ignorant of domain.
 * NOTES: Hex manufacturer IDs ("0x0075") are parsed to Int here.
 *   Unknown category/confidence values fall back to safe defaults.
 */
fun RulesDataDto.toDomain(): RulesData = RulesData(
    metadata = RuleSetMetadata(version = ruleSetVersion, hash = ruleSetHash),
    rules = rules.map { it.toDomain() }
)

private fun FingerprintRuleDto.toDomain(): FingerprintRule = FingerprintRule(
    ruleId = ruleId,
    enabled = enabled,
    priority = priority,
    minScore = minScore,
    manufacturerIds = manufacturerIds.map { it.parseManufacturerId() },
    namePatterns = namePatterns,
    serviceUuids = serviceUuids,
    deviceTypeHint = deviceTypeHint,
    category = DeviceCategory.entries.firstOrNull { it.name == category }
        ?: DeviceCategory.UNKNOWN_BLE_DEVICE,
    displayLabel = displayLabel,
    confidence = ConfidenceLevel.entries.firstOrNull { it.name == confidence }
        ?: ConfidenceLevel.LOW,
    isWearableCandidate = isWearableCandidate
)

/**
 * Parses a manufacturer ID string to Int.
 * Handles hex format ("0x0075" → 117) and decimal format ("117" → 117).
 */
private fun String.parseManufacturerId(): Int =
    if (startsWith("0x", ignoreCase = true)) {
        removePrefix("0x").removePrefix("0X").toInt(16)
    } else {
        toInt()
    }
