package com.wearaware.app.domain.model

/**
 * PURPOSE: Fully enriched domain model for a detected BLE device. This is the
 *   primary object flowing from domain use cases to the UI layer.
 * LIMITATIONS: id is a session-scoped fingerprint derived from advertising data.
 *   The same physical device may have a different id across app sessions due to
 *   BLE MAC address randomization on Android 6+.
 * NOTES: seenDurationMs is a computed property — always consistent with
 *   firstSeenAt and lastSeenAt. Do not store separately.
 */
data class ObservedDevice(
    /**
     * Session-scoped fingerprint. Derived from manufacturer data, service UUIDs,
     * and advertised name. NOT a MAC address.
     */
    val id: String,
    /** Advertised name from BLE scan record. Null if device does not broadcast name. */
    val advertisedName: String?,
    /** Most recent raw RSSI reading in dBm. */
    val rawRssi: Int,
    /** Rolling-average RSSI over the configured window. Used for proximity calculation. */
    val averagedRssi: Int,
    /** Proximity label derived from averagedRssi via ProximityConfig thresholds. */
    val proximityLabel: ProximityLabel,
    /** Whether the device has been seen recently (within SIGNAL_LOST_AFTER_MS). */
    val visibilityState: VisibilityState,
    /** Epoch ms when this device was first seen in the current session. */
    val firstSeenAt: Long,
    /** Epoch ms of the most recent scan result for this device. */
    val lastSeenAt: Long,
    /** Total number of BLE scan results received for this device in this session. */
    val seenCount: Int,
    /** Result of fingerprint rule classification. */
    val classification: ClassificationResult,
    /** Active persistence alert for this device, if any. Null if no alert is active. */
    val persistenceAlert: PersistenceAlert?
) {
    /**
     * Time elapsed since first detection.
     * Note: this resets if the device enters SIGNAL_LOST for long enough to be removed
     * and then re-appears, since that creates a new ObservedDevice entry.
     */
    val seenDurationMs: Long get() = lastSeenAt - firstSeenAt
}
