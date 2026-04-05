package com.wearaware.app.domain.model

/**
 * Persisted BLE + GATT signature for a device the user has confirmed as their glasses.
 * Replaces LearnedDeviceSignature. Stored via SharedPreferences + Gson. One record at a time.
 *
 * DESIGN: No MAC address. gattServiceUuids are empty when learned via quick-learn
 * (capture path); populated when learned via full Pair & Learn flow.
 */
data class KnownTargetSignature(
    val displayName: String,
    val savedAt: Long,
    val fingerprintId: String,
    val manufacturerIds: List<Int>,
    /** Top-N most frequently observed manufacturer data prefixes across all learning sessions. */
    val manufacturerDataPrefixes: List<String>,
    val serviceUuids: List<String>,
    val gattServiceUuids: List<String>,
    val behaviorProfile: KnownBehaviorProfile?,
    /** Characteristic UUID → hex prefix of its value (4 bytes). Empty on quick-learn path. */
    val characteristicValuePrefixes: Map<String, String> = emptyMap(),
    /** Total number of learn/merge operations performed on this signature. */
    val learnCount: Int = 1,
    /** Millisecond timestamp of the last merge/update. */
    val lastUpdatedAt: Long = savedAt,
    /**
     * Tracks observation frequency per manufacturer data prefix across all sessions.
     * Used to rank prefixes by consistency — only top-5 are kept in [manufacturerDataPrefixes].
     */
    val manufacturerDataPrefixFrequency: Map<String, Int> = emptyMap()
)

data class KnownBehaviorProfile(
    /** Moving average RSSI computed across all observations. */
    val typicalRssiAtClose: Int,
    /** Lowest seenCount observed across all sessions. */
    val minSeenCount: Int,
    /** Number of RSSI samples contributing to [typicalRssiAtClose]. 0 on old saves → treated as 1. */
    val rssiSampleCount: Int = 1,
    /** Highest seenCount observed across all sessions. */
    val maxSeenCount: Int = minSeenCount,
    /** How many times the device was still visible when the observation ended. */
    val visibleAtStopCount: Int = 0,
    /** Total observation/merge calls recorded. */
    val totalObservations: Int = 1
)
