package com.wearaware.app.domain.repository

import com.wearaware.app.domain.rules.FingerprintRule
import com.wearaware.app.domain.rules.RuleSetMetadata

/**
 * PURPOSE: Provides the fingerprint classification rules and metadata.
 *   Rules are loaded from the bundled fingerprint_rules.json asset.
 * NOTES: getRules() is synchronous because rules are bundled (always available, small).
 *   Rules are cached on first load — no repeated file I/O.
 *   Implemented by RulesRepositoryImpl in the data layer.
 */
interface RulesRepository {
    /** Returns all enabled fingerprint rules and their metadata. */
    fun getRules(): RulesData
}

/**
 * Container for the loaded rules and their version metadata.
 * Kept in domain because FingerprintClassifier (domain) consumes it.
 */
data class RulesData(
    val metadata: RuleSetMetadata,
    val rules: List<FingerprintRule>
)
