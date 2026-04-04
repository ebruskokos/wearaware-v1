package com.wearaware.app.domain.model

/**
 * PURPOSE: Represents a triggered persistence alert for a device that has remained
 *   nearby beyond the configured threshold.
 * LIMITATIONS: Alert is triggered based on in-session data only. Cannot determine
 *   intent or whether recording is occurring.
 * NOTES: UI layer maps alertType to safe wording strings via SafeWording object.
 *   No raw strings in this model — alertType is an enum to prevent wording drift.
 */
data class PersistenceAlert(
    /** Session-scoped device fingerprint. */
    val deviceId: String,
    /** Display label of the classified device at time of alert. */
    val deviceDisplayLabel: String,
    /** Duration (ms) the device has been continuously DETECTED_NOW at alert trigger. */
    val durationMs: Long,
    /** Type of alert. Determines which safe wording the UI renders. */
    val alertType: PersistenceAlertType,
    /** Epoch ms when this alert was triggered. Used for cooldown calculation. */
    val triggeredAt: Long
)
