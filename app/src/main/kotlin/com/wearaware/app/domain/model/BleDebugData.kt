package com.wearaware.app.domain.model

/**
 * PURPOSE: Display-only container for raw BLE advertising fields captured at scan time.
 *   Not used for fingerprinting or classification — pure diagnostic data for the UI.
 * NOTES: serviceDataHex values are lowercase hex strings. rawScanBytesHex is the
 *   full ScanRecord bytes as a hex dump — useful for manual protocol inspection.
 *   PHY values: 1=LE 1M, 2=LE 2M, 3=LE Coded. 0 = unavailable (pre-API 26).
 */
data class BleDebugData(
    /** Service solicitation UUIDs (API 29+). Empty on older devices. */
    val serviceSolicitationUuids: List<String>,
    /** Service data keyed by UUID, encoded as lowercase hex strings. */
    val serviceDataHex: Map<String, String>,
    /** Full raw ScanRecord as lowercase hex. Null if ScanRecord was null. */
    val rawScanBytesHex: String?,
    /** Whether the device is advertising as connectable. */
    val isConnectable: Boolean,
    /** LE advertising flags byte value. Null if not in advertisement. */
    val advertisingFlags: Int?,
    /** Hardware timestamp in nanoseconds since device boot. */
    val timestampNanos: Long,
    /** Primary advertising PHY. 0 if unavailable (pre-API 26). */
    val primaryPhy: Int,
    /** Secondary advertising PHY. 0 if unavailable. */
    val secondaryPhy: Int,
    /** Advertising set ID. 255 = no SID. 0 if unavailable. */
    val advertisingSid: Int,
    /** Periodic advertising interval in units of 1.25ms. 0 if unavailable. */
    val periodicAdvertisingInterval: Int,
    /** BluetoothDevice type int: 0=unknown, 1=classic, 2=LE, 3=dual. */
    val deviceType: Int,
    /** BluetoothDevice bond state int: 10=none, 11=bonding, 12=bonded. */
    val bondState: Int
)
