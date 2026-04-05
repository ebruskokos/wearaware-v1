package com.wearaware.app.domain.model

enum class LearnedConfidence { STRONG, POSSIBLE, NONE }

/**
 * Output of MatchLearnedSignatureUseCase for a single candidate device.
 * labelOverrideActive is true for STRONG and POSSIBLE — both get a label override.
 */
data class LearnedMatchResult(
    val signature: LearnedDeviceSignature,
    val score: Int,
    val confidence: LearnedConfidence,
    val matchedSignals: List<String>,    // human-readable reasons for debug UI
    val labelOverrideActive: Boolean     // true when confidence != NONE
)
