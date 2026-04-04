package com.wearaware.app.domain.model

/**
 * PURPOSE: Enriched domain representation of a BLE device observed during a scan session.
 *   Built by BleRepositoryImpl from raw scan data; consumed by use cases and ViewModels.
 * NOTES: id is the content-based fingerprintId (stable across MAC rotations).
 *   macAddress stores the raw BLE address for display purposes only — not used as identity.
 *   seenDurationMs is a computed property: lastSeenAt - firstSeenAt.
 */
data class ObservedDevice(
    /** Content-based fingerprint hash. Stable across BLE MAC address rotations. */
    val id: String,
    val advertisedName: String?,
    val rawRssi: Int,
    val averagedRssi: Int,
    val proximityLabel: ProximityLabel,
    val visibilityState: VisibilityState,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val seenCount: Int,
    val classification: ClassificationResult,
    val persistenceAlert: PersistenceAlert?,
    /** Raw BLE MAC address — may be randomized per-session. For display only. */
    val macAddress: String? = null,
    /** Content-based fingerprint derived from advertising fields. */
    val fingerprint: DeviceFingerprint? = null,
    /** Resolved company/brand names from manufacturer IDs (e.g. "Meta", "Apple"). */
    val companyNames: List<String> = emptyList(),
    /** Raw BLE diagnostic data captured at first observation. Null if data unavailable. */
    val rawBleData: BleDebugData? = null
) {
    val seenDurationMs: Long get() = lastSeenAt - firstSeenAt
}
