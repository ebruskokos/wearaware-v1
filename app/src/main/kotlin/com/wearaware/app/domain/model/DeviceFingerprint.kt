package com.wearaware.app.domain.model

/**
 * PURPOSE: Content-based device identity derived from stable BLE advertising fields.
 *   fingerprintId is a SHA-256 hash of manufacturer data + service UUIDs + normalized name.
 *   Does NOT include MAC address — stable across MAC rotations within a session.
 * NOTES: Two physically different devices may theoretically produce the same fingerprint
 *   if they advertise identical manufacturer data, UUIDs, and name. This is extremely
 *   unlikely for real consumer devices. Documented as a known limitation.
 */
data class DeviceFingerprint(
    /** First 16 hex chars of SHA-256 hash of stable advertising fields. */
    val fingerprintId: String,
    /** Bluetooth SIG Company IDs present in manufacturer-specific data. */
    val manufacturerIds: List<Int>,
    /** Resolved company names from CompanyIdMap. */
    val manufacturerNames: List<String>,
    /** Manufacturer data as hex strings, keyed by Company ID. */
    val manufacturerDataHex: Map<Int, String>,
    /** Service UUIDs advertised by the device. */
    val serviceUuids: List<String>,
    /** Lowercase-trimmed advertised name, or null. */
    val normalizedName: String?,
    /** TX power level, or null if not present. */
    val txPower: Int?
)
