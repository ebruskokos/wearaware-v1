package com.wearaware.app.domain.model

/**
 * PURPOSE: Pure representation of a single BLE scan result before any enrichment.
 *   Sits at the data/domain boundary — created in the data layer, consumed by domain.
 * LIMITATIONS: address may be randomized (Android 6+ BLE address randomization).
 *   manufacturerData keys are Company IDs (16-bit integers from Bluetooth SIG).
 * NOTES: This is NOT an Android class — it contains no android.bluetooth imports.
 */
data class RawScanResult(
    /** BLE device address. May be randomized per-session on Android 6+. */
    val address: String,
    /** Advertised device name from BLE ScanRecord. Null if device does not broadcast name. */
    val advertisedName: String?,
    /** Raw RSSI in dBm. Negative value; closer to 0 = stronger signal. */
    val rssi: Int,
    /** BLE manufacturer-specific data. Key = Company ID (int), Value = raw bytes. */
    val manufacturerData: Map<Int, ByteArray>,
    /** BLE service UUIDs advertised by the device. Normalized to lowercase strings. */
    val serviceUuids: List<String>,
    /** TX power level from BLE advertising packet. Null if not present in scan record. */
    val txPowerLevel: Int?,
    /** Epoch milliseconds when this scan result was received. */
    val timestampMs: Long
)
