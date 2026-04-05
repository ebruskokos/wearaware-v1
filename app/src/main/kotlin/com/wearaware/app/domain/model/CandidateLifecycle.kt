package com.wearaware.app.domain.model

/**
 * Per-device lifecycle state tracked across scan ticks for ranking purposes.
 * Lives in ScanViewModel — never persisted.
 */
data class CandidateLifecycle(
    /** Epoch ms when this device first appeared in a match result (score > 0). */
    val discoveredAt: Long,
    /** Epoch ms of the device's most recent BLE advertisement (from ObservedDevice.lastSeenAt). */
    val lastSeenAt: Long,
    /** Highest adjusted match score ever observed for this device. */
    val peakScore: Int,
    /** Number of scan ticks in which this device appeared in match results. */
    val totalSeenCount: Int,
    /**
     * Epoch ms when this device first reached KnownMatchConfidence.STRONG.
     * Reset to null whenever confidence drops below STRONG.
     */
    val strongSince: Long? = null,
    /**
     * Number of scan ticks in which this device scored >= POSSIBLE threshold
     * but was NOT the primary lock target.
     * Used by the false-positive guard: devices that consistently score high but
     * never lock are marked as "persistent non-target" and receive a score penalty.
     */
    val highScoreWithoutLockCount: Int = 0
)
