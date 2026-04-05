package com.wearaware.app.domain.model

data class CompareMatchResult(
    val capturedDevice: CapturedDevice,
    val score: Int,
    val confidence: CompareConfidence,
    /** Human-readable strings explaining each signal that fired. Shown in UI. */
    val comparisonSignals: List<String>,
    val seenInBaseline: Boolean,
    val seenInTarget: Boolean,          // always true — these are target devices
    val baselineAverageRssi: Int?,      // null if not seen in baseline
    val rssiDelta: Int?,                // targetAvg − baselineAvg; null if not in baseline
    /** False when no baseline session was provided; UI shows weaker-evidence disclaimer. */
    val hasBaseline: Boolean
)
