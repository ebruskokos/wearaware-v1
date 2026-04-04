package com.wearaware.app.domain.model

/**
 * Scoring result for one ObservedDevice against a TargetDeviceProfile.
 * isTopCandidate is set by MatchTargetDeviceUseCase after comparing all devices.
 */
data class TargetMatchResult(
    val deviceId: String,
    val score: Int,
    val confidence: MatchConfidence,
    val matchedSignals: List<String>,
    val isTopCandidate: Boolean
)

enum class MatchConfidence {
    HIGH,    // score >= 9
    MEDIUM,  // score >= 6
    LOW,     // score >= 3
    NONE     // score < 3
}
