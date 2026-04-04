package com.wearaware.app.domain.rules

import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.RulesData

/**
 * PURPOSE: Classifies a RawScanResult against the loaded fingerprint rules.
 *   Returns a ClassificationResult with the best matching rule's metadata,
 *   or UNKNOWN_BLE_DEVICE if no rule meets its minimum score.
 * LIMITATIONS: Point-based scoring; no probabilistic model. Classification quality
 *   depends entirely on the completeness of fingerprint_rules.json.
 * NOTES: Classification is DETERMINISTIC: same input + same rule set version → same output.
 *   This is a requirement for audit reproducibility.
 *   Scoring: manufacturer ID match +4, name pattern match +2, service UUID match +2.
 *   Ties resolved by rule priority (descending).
 */
class FingerprintClassifier(
    private val rulesData: RulesData
) {
    companion object {
        private const val MANUFACTURER_SCORE = 4
        private const val NAME_SCORE = 2
        private const val UUID_SCORE = 2
        private const val FALLBACK_LABEL = "Unknown BLE device"
    }

    private data class ScoredRule(
        val rule: FingerprintRule,
        val score: Int,
        val matchDetails: MatchDetails
    )

    /**
     * Evaluates all enabled rules against the scan result and returns the best match.
     * Input is normalized before matching (lowercase, trimmed).
     * Returns UNKNOWN_BLE_DEVICE classification if no rule meets its min_score.
     */
    fun classify(scan: RawScanResult): ClassificationResult {
        val normalizedName = scan.advertisedName?.lowercase()?.trim()
        val normalizedUuids = scan.serviceUuids.map { it.lowercase() }
        val manufacturerIds = scan.manufacturerData.keys

        val candidates: List<ScoredRule> = rulesData.rules
            .filter { it.enabled }
            .sortedByDescending { it.priority }
            .mapNotNull { rule ->
                val mfrMatch = rule.manufacturerIds.any { it in manufacturerIds }
                val nameMatch = normalizedName != null &&
                    rule.namePatterns.any { pattern ->
                        normalizedName.contains(pattern.lowercase())
                    }
                val uuidMatch = rule.serviceUuids.any { uuid ->
                    uuid.lowercase() in normalizedUuids
                }

                val score = (if (mfrMatch) MANUFACTURER_SCORE else 0) +
                            (if (nameMatch) NAME_SCORE else 0) +
                            (if (uuidMatch) UUID_SCORE else 0)

                if (score >= rule.minScore) {
                    ScoredRule(
                        rule = rule,
                        score = score,
                        matchDetails = MatchDetails(mfrMatch, nameMatch, uuidMatch)
                    )
                } else null
            }

        // Highest score wins; tie-break by priority (already sorted, so first wins)
        val best = candidates.maxWithOrNull(compareBy({ it.score }, { it.rule.priority }))

        return if (best != null) {
            ClassificationResult(
                matchedRuleId = best.rule.ruleId,
                ruleVersion = rulesData.metadata.version,
                category = best.rule.category,
                displayLabel = best.rule.displayLabel,
                confidence = best.rule.confidence,
                isWearableCandidate = best.rule.isWearableCandidate,
                evaluationNotes = best.matchDetails.toNotes()
            )
        } else {
            ClassificationResult(
                matchedRuleId = null,
                ruleVersion = rulesData.metadata.version,
                category = DeviceCategory.UNKNOWN_BLE_DEVICE,
                displayLabel = FALLBACK_LABEL,
                confidence = ConfidenceLevel.LOW,
                isWearableCandidate = false,
                evaluationNotes = "No rule matched score threshold"
            )
        }
    }
}

/**
 * Internal record of which fields contributed to a rule match.
 * Converted to a string for storage in session log evaluationNotes.
 */
private data class MatchDetails(
    val manufacturerMatched: Boolean,
    val nameMatched: Boolean,
    val uuidMatched: Boolean
) {
    fun toNotes(): String = buildList {
        if (manufacturerMatched) add("manufacturer ID matched")
        if (nameMatched) add("name pattern matched")
        if (uuidMatched) add("service UUID matched")
    }.joinToString("; ").ifEmpty { "no match details" }
}
