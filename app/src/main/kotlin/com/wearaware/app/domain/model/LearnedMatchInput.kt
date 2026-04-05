package com.wearaware.app.domain.model

/**
 * Neutral input type for MatchLearnedSignatureUseCase.
 * Allows the use case to work with both CapturedDevice (compare flow) and
 * ObservedDevice (live scan flow) without importing either model.
 */
data class LearnedMatchInput(
    val fingerprintId: String,
    val manufacturerIds: List<Int>,
    val manufacturerDataPrefixes: List<String>,  // same format as LearnedDeviceSignature
    val serviceUuids: List<String>,
    val averageRssi: Int,
    val seenCount: Int,
    val visibleAtStop: Boolean
)
