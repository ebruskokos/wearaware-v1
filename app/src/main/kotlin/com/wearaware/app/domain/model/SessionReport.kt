package com.wearaware.app.domain.model

/**
 * Summary produced at the end of a scan session (on stopScanning).
 * Captures key observability metrics for real-world tuning.
 */
data class SessionReport(
    /** Total wall-clock duration of the scan session in ms. */
    val sessionDurationMs: Long,
    /** Total ms the primary lock was held during the session. */
    val totalLockedMs: Long,
    /** Device IDs seen as candidates (score > 0) at any point. */
    val totalCandidatesSeen: Int,
    /** Highest score any non-locked candidate reached during the session. */
    val maxCompetingScore: Int,
    /** Device name/id of the device that held the lock, or null if no lock was ever acquired. */
    val lockedDeviceId: String?,
    /** How many times the lock target switched during the session. */
    val lockSwitchCount: Int,
    /** Epoch ms when the session ended. */
    val endedAt: Long
)

/**
 * One logged anomaly event during a scan session.
 */
data class ScanAnomalyEvent(
    val timestampMs: Long,
    val type: AnomalyType,
    /** Human-readable description of what happened and why. */
    val detail: String
)

enum class AnomalyType {
    /** Primary lock switched from one device to another. */
    LOCK_SWITCH,
    /** A non-locked device reached STRONG confidence (potential false positive). */
    STRONG_FALSE_POSITIVE
}
