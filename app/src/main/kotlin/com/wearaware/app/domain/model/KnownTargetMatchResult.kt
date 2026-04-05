package com.wearaware.app.domain.model

enum class KnownMatchConfidence { STRONG, POSSIBLE, WEAK, NONE }

data class KnownTargetMatchResult(
    val signature: KnownTargetSignature,
    /** Raw fingerprint score before temporal adjustments. */
    val score: Int,
    val confidence: KnownMatchConfidence,
    val matchedSignals: List<String>,
    /** True for STRONG and POSSIBLE — these get a display label override. */
    val labelOverrideActive: Boolean,
    /**
     * True when at least one structural signal (mfr ID, prefix, UUID, or fingerprint ID)
     * contributed to the score. Used by temporal filter to gate STRONG/POSSIBLE.
     */
    val hasStructuralSignal: Boolean = false,
    /**
     * Human-readable notes from the temporal filter (decay, smoothing, hysteresis).
     * Empty when temporal filter has not yet been applied.
     */
    val temporalNotes: List<String> = emptyList(),
    /**
     * Weighted score breakdown by category — populated by [MatchKnownTargetSignatureUseCase].
     * Each item carries a [ScoreBreakdownItem.points] value (positive = bonus, negative = penalty).
     * Empty on results produced before this field was added.
     */
    val scoreBreakdown: List<ScoreBreakdownItem> = emptyList()
)
