package com.wearaware.app.domain.model

/**
 * Per-device temporal tracking state maintained across BLE scan ticks.
 * Used by [com.wearaware.app.domain.usecase.ApplyTemporalMatchFilterUseCase] to
 * compute confidence decay, score smoothing, RSSI variance, and hysteresis.
 *
 * Never persisted — lives only for the duration of a scan session in ScanViewModel.
 */
data class DeviceTemporalState(
    /** Rolling RSSI window, oldest-first, max [RSSI_WINDOW] entries. */
    val rssiHistory: List<Int> = emptyList(),
    /** Rolling raw-score window, oldest-first, max [SCORE_WINDOW] entries. */
    val scoreHistory: List<Int> = emptyList(),
    /** Confidence from the previous tick — used for hysteresis. */
    val prevConfidence: KnownMatchConfidence = KnownMatchConfidence.NONE,
    /** Consecutive ticks where smoothed score >= POSSIBLE threshold (>=4). */
    val stableObservationCount: Int = 0
) {
    companion object {
        const val RSSI_WINDOW = 7
        const val SCORE_WINDOW = 5
    }
}
