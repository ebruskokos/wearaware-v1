package com.wearaware.app.domain.model

/**
 * PURPOSE: Pure representation of a single BLE scan result with ALL available fields.
 *   Sits at the data/domain boundary — created in data layer, consumed by domain.
 * LIMITATIONS: address may be randomized (Android 6+ BLE MAC randomization).
 *   Fields guarded by API level have safe defaults (empty / 0 / null / 255).
 * NOTES: No android.bluetooth imports — all types are Kotlin primitives or stdlib.
 */
data class RawScanResult(
    /** BLE device address. May be randomized per-session on Android 6+. */
    val address: String,
    /** Advertised device name from ScanRecord. Null if device does not broadcast name. */
    val advertisedName: String?,
    /** Device name from BluetoothDevice (bonded/cached). May differ from advertisedName. */
    val bluetoothDeviceName: String?,
    /** Raw RSSI in dBm. */
    val rssi: Int,
    /** Manufacturer-specific data. Key = Bluetooth SIG Company ID (int), value = raw bytes. */
    val manufacturerData: Map<Int, ByteArray>,
    /** Service UUIDs advertised. Normalized to lowercase strings. */
    val serviceUuids: List<String>,
    /** Service data map. Key = service UUID (lowercase), value = raw bytes. */
    val serviceData: Map<String, ByteArray>,
    /** TX power level from ad packet. Null if not present. */
    val txPowerLevel: Int?,
    /** LE advertising flags byte. Null if not present in ScanRecord. */
    val advertisingFlags: Int?,
    /** Whether the device is connectable. Requires API 26+; false if unavailable. */
    val isConnectable: Boolean,
    /** BluetoothDevice type: 0=unknown, 1=classic, 2=LE, 3=dual. */
    val deviceType: Int,
    /** BluetoothDevice bond state: 10=none, 11=bonding, 12=bonded. */
    val bondState: Int,
    /** Epoch milliseconds when this scan result was received. */
    val timestampMs: Long,
    /** Service solicitation UUIDs. Requires API 29+; empty if unavailable. */
    val serviceSolicitationUuids: List<String> = emptyList(),
    /** Raw ScanRecord bytes. Null if ScanRecord is null. */
    val rawScanBytes: ByteArray? = null,
    /** Hardware timestamp in nanoseconds since boot (from ScanResult.timestampNanos). */
    val timestampNanos: Long = 0L,
    /** Primary advertising PHY (1=LE 1M, 2=LE 2M, 3=LE Coded). Requires API 26+; 0 if unavailable. */
    val primaryPhy: Int = 0,
    /** Secondary advertising PHY. Requires API 26+; 0 if unavailable. */
    val secondaryPhy: Int = 0,
    /** Advertising set ID. Requires API 26+; 255 = not present. */
    val advertisingSid: Int = 255,
    /** Periodic advertising interval in units of 1.25 ms. Requires API 26+; 0 if unavailable. */
    val periodicAdvertisingInterval: Int = 0
)
