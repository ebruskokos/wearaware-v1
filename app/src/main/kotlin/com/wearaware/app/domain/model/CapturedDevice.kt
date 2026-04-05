package com.wearaware.app.domain.model

/**
 * PURPOSE: Per-device snapshot accumulated during a capture window.
 *   Not a point-in-time snapshot — all fields reflect the full window.
 * NOTES: manufacturerIds is the canonical field for logic (e.g. 0x0075 = Meta).
 *   companyNames is derived/display-only and must not be used for scoring.
 */
data class CapturedDevice(
    val fingerprintId: String,
    val advertisedName: String?,
    val macAddress: String?,
    /** Bluetooth SIG Company IDs. Canonical source for manufacturer-based logic. */
    val manufacturerIds: List<Int>,
    /** Human-readable summary e.g. "0075:deadbeef,004c:...". Display only. */
    val manufacturerDataSummary: String?,
    val serviceUuids: List<String>,
    val category: DeviceCategory,
    /** Resolved company/brand names. Display only — do not use for scoring. */
    val companyNames: List<String>,
    val firstSeenInCapture: Long,
    val lastSeenInCapture: Long,
    val peakRssi: Int,
    val averageRssi: Int,
    val seenCount: Int,
    val visibleAtStop: Boolean,
    val targetMatchScore: Int?,
    val targetMatchSignals: List<String>,
    /**
     * Richest-observation debug log for this device. Only populated for captures
     * performed in the current app session — null when loaded from Room.
     */
    val observationLog: CaptureObservationLog? = null
)
