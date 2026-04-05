package com.wearaware.app.domain.model

/**
 * Overall training quality of a [KnownTargetSignature], derived from observation count
 * and RSSI sample depth.
 *
 * LOW  < 8 observations or < 3 RSSI samples
 * MEDIUM >= 8 observations or >= 5 RSSI samples (but not HIGH)
 * HIGH >= 20 observations AND >= 15 RSSI samples
 */
enum class SignatureConfidence { LOW, MEDIUM, HIGH }

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
    val manufacturerDataPrefixFrequency: Map<String, Int> = emptyMap(),
    /**
     * Monotonically increasing version counter incremented by adaptive refinement.
     * Starts at 1 on initial learn. Old saves deserialise to 0 — treated as 1.
     */
    val version: Int = 1,
    /**
     * Total number of BLE observations merged into this signature (explicit learns +
     * adaptive refinements). Old saves deserialise to 0.
     */
    val observationCount: Int = 0
) {
    /**
     * Derived training quality — does not change the persisted record.
     * HIGH requires 20+ observations AND 15+ RSSI samples (deep history).
     * MEDIUM requires either 8+ observations or 5+ RSSI samples.
     * LOW otherwise.
     */
    val signatureConfidence: SignatureConfidence
        get() {
            val samples = behaviorProfile?.rssiSampleCount ?: 0
            return when {
                observationCount >= 20 && samples >= 15 -> SignatureConfidence.HIGH
                observationCount >= 8 || samples >= 5 -> SignatureConfidence.MEDIUM
                else -> SignatureConfidence.LOW
            }
        }
}

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
    val totalObservations: Int = 1,
    /**
     * Weakest (most negative) RSSI observed across all adaptive refinement ticks.
     * Null on old saves — initialised to [typicalRssiAtClose] on first refinement.
     */
    val rssiMin: Int? = null,
    /**
     * Strongest (least negative) RSSI observed across all adaptive refinement ticks.
     * Null on old saves — initialised to [typicalRssiAtClose] on first refinement.
     */
    val rssiMax: Int? = null,
    /**
     * Rolling list of per-session average RSSI values (most recent last, max 10 entries).
     * Each entry corresponds to one [com.wearaware.app.domain.usecase.MergeKnownTargetSignatureUseCase] call.
     * Allows cross-session RSSI trend analysis.
     */
    val sessionRssiAverages: List<Int> = emptyList()
)
