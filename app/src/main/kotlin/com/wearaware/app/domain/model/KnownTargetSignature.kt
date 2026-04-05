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
    val manufacturerDataPrefixes: List<String>,
    val serviceUuids: List<String>,
    val gattServiceUuids: List<String>,
    val behaviorProfile: KnownBehaviorProfile?
)

data class KnownBehaviorProfile(
    val typicalRssiAtClose: Int,
    val minSeenCount: Int
)
