package com.wearaware.app.domain.model

/**
 * Persisted BLE signal signature for a device the user has confirmed as their glasses.
 * Stored via SharedPreferences + Gson. One record at a time.
 *
 * DESIGN: No MAC address (randomized). Behavior signals (RSSI, seenCount) are
 * session-specific and computed fresh at match time — not stored here.
 */
data class LearnedDeviceSignature(
    val displayName: String,                     // always "My Meta Glasses"
    val savedAt: Long,                           // epoch millis
    val fingerprintId: String,                   // bonus signal only — never required for match
    val manufacturerIds: List<Int>,              // strong match signal
    val manufacturerDataPrefixes: List<String>,  // very strong signal: "companyIdHex:first4bytesHex"
    val serviceUuids: List<String>               // moderate match signal
)
