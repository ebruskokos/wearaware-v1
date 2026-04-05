package com.wearaware.app.domain.model

enum class KnownMatchConfidence { STRONG, POSSIBLE, WEAK, NONE }

data class KnownTargetMatchResult(
    val signature: KnownTargetSignature,
    val score: Int,
    val confidence: KnownMatchConfidence,
    val matchedSignals: List<String>,
    /** True for STRONG and POSSIBLE — these get a display label override. */
    val labelOverrideActive: Boolean
)
