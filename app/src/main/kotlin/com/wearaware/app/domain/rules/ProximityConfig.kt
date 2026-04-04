package com.wearaware.app.domain.rules

import com.wearaware.app.domain.model.ProximityLabel

/**
 * PURPOSE: Single source of truth for all RSSI thresholds, visibility timeouts,
 *   and signal bar mapping. All proximity logic references these constants.
 * LIMITATIONS: Thresholds are calibrated for typical indoor environments.
 *   Actual performance varies with obstacles, reflections, and device orientation.
 *   Re-tune RSSI thresholds after real-world testing with Meta Ray-Ban glasses.
 * NOTES: All timeout values are in milliseconds.
 */
object ProximityConfig {

    // RSSI thresholds (averaged dBm). Higher value = stronger signal = closer.
    const val VERY_CLOSE_THRESHOLD_DBM = -55
    const val STRONG_THRESHOLD_DBM     = -65
    const val NEARBY_THRESHOLD_DBM     = -75
    const val WEAK_THRESHOLD_DBM       = -85

    // Visibility lifecycle timeouts
    /** After this many ms without a scan result, mark device as SIGNAL_LOST. */
    const val SIGNAL_LOST_AFTER_MS = 15_000L
    /** After this many ms in SIGNAL_LOST state, remove device from the active list. */
    const val REMOVE_AFTER_MS      = 30_000L

    // RSSI smoothing
    /** Rolling average window size. Increase for smoother bars; decrease for faster response. */
    const val RSSI_WINDOW_SIZE = 5

    /**
     * Maps an averaged RSSI value to a ProximityLabel.
     * Used by BleRepositoryImpl when building ObservedDevice instances.
     */
    fun labelFromRssi(averagedRssi: Int): ProximityLabel = when {
        averagedRssi >= VERY_CLOSE_THRESHOLD_DBM -> ProximityLabel.VERY_CLOSE
        averagedRssi >= STRONG_THRESHOLD_DBM     -> ProximityLabel.STRONG
        averagedRssi >= NEARBY_THRESHOLD_DBM     -> ProximityLabel.NEARBY
        averagedRssi >= WEAK_THRESHOLD_DBM       -> ProximityLabel.WEAK
        else                                      -> ProximityLabel.UNKNOWN
    }

    /**
     * Maps a ProximityLabel to a 1–5 bar count for the SignalBars UI component.
     */
    fun barCountFromLabel(label: ProximityLabel): Int = when (label) {
        ProximityLabel.VERY_CLOSE -> 5
        ProximityLabel.STRONG     -> 4
        ProximityLabel.NEARBY     -> 3
        ProximityLabel.WEAK       -> 2
        ProximityLabel.UNKNOWN    -> 1
    }
}
