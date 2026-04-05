package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class AdaptiveSignatureRefineUseCaseTest {

    private val useCase = AdaptiveSignatureRefineUseCase()

    private fun makeSig(
        manufacturerIds: List<Int> = listOf(0x0075),
        prefixes: List<String> = listOf("0075:deadbeef"),
        prefixFrequency: Map<String, Int> = mapOf("0075:deadbeef" to 3),
        behaviorProfile: KnownBehaviorProfile? = KnownBehaviorProfile(
            typicalRssiAtClose = -65,
            minSeenCount = 10,
            rssiSampleCount = 5,
            maxSeenCount = 30,
            visibleAtStopCount = 2,
            totalObservations = 5
        ),
        version: Int = 1,
        observationCount: Int = 5
    ) = KnownTargetSignature(
        displayName = "My Glasses",
        savedAt = 1000L,
        fingerprintId = "fp-001",
        manufacturerIds = manufacturerIds,
        manufacturerDataPrefixes = prefixes,
        serviceUuids = listOf("svc-1"),
        gattServiceUuids = emptyList(),
        behaviorProfile = behaviorProfile,
        version = version,
        observationCount = observationCount,
        manufacturerDataPrefixFrequency = prefixFrequency
    )

    private fun makeDevice(
        manufacturerIds: List<Int> = listOf(0x0075),
        manufacturerDataHex: Map<Int, String> = mapOf(0x0075 to "deadbeef0000"),
        averagedRssi: Int = -65,
        seenCount: Int = 20
    ): ObservedDevice {
        val fingerprint = DeviceFingerprint(
            fingerprintId = "fp-001",
            manufacturerIds = manufacturerIds,
            manufacturerNames = emptyList(),
            manufacturerDataHex = manufacturerDataHex,
            serviceUuids = listOf("svc-1"),
            normalizedName = null,
            txPower = null
        )
        return ObservedDevice(
            id = "fp-001",
            advertisedName = "Test Device",
            rawRssi = averagedRssi,
            averagedRssi = averagedRssi,
            proximityLabel = ProximityLabel.NEARBY,
            visibilityState = VisibilityState.DETECTED_NOW,
            firstSeenAt = 0L,
            lastSeenAt = 1000L,
            seenCount = seenCount,
            classification = ClassificationResult(
                matchedRuleId = null, ruleVersion = null,
                category = DeviceCategory.UNKNOWN_BLE_DEVICE,
                displayLabel = "Unknown", confidence = ConfidenceLevel.LOW,
                isWearableCandidate = false, evaluationNotes = null
            ),
            persistenceAlert = null,
            fingerprint = fingerprint
        )
    }

    /** Stable RSSI history — σ ≈ 2.0, well below VARIANCE_THRESHOLD=6 */
    private val stableHistory = listOf(-64, -65, -66, -65, -64)

    /** High-variance RSSI history — σ ≈ 7.5, exceeds VARIANCE_THRESHOLD */
    private val noisyHistory = listOf(-50, -70, -50, -75, -55, -80, -50)

    private fun stableState(rssi: Int = -65) = DeviceTemporalState(
        rssiHistory = stableHistory
    )

    // --- Guard: RSSI variance ---

    @Test
    fun `skips when rssiHistory has fewer than 3 samples`() {
        val state = DeviceTemporalState(rssiHistory = listOf(-65, -66))
        val result = useCase(makeSig(), makeDevice(), state)
        assertFalse(result.wasRefined)
        assertNotNull(result.skippedReason)
    }

    @Test
    fun `skips when rssi variance exceeds threshold`() {
        val state = DeviceTemporalState(rssiHistory = noisyHistory)
        val result = useCase(makeSig(), makeDevice(), state)
        assertFalse(result.wasRefined)
        assertTrue(result.skippedReason!!.contains("variance", ignoreCase = true))
    }

    @Test
    fun `proceeds when rssi variance is below threshold`() {
        val result = useCase(makeSig(), makeDevice(), stableState())
        assertTrue(result.wasRefined)
    }

    // --- Guard: RSSI drift ---

    @Test
    fun `skips when rssi deviates more than MAX_RSSI_DRIFT from learned avg`() {
        val learnedAvg = -65
        val deviceRssi = learnedAvg - AdaptiveSignatureRefineUseCase.MAX_RSSI_DRIFT - 1
        val result = useCase(makeSig(), makeDevice(averagedRssi = deviceRssi), stableState())
        assertFalse(result.wasRefined)
        assertTrue(result.skippedReason!!.contains("drift", ignoreCase = true))
    }

    @Test
    fun `proceeds when rssi drift is within MAX_RSSI_DRIFT`() {
        val deviceRssi = -65 + AdaptiveSignatureRefineUseCase.MAX_RSSI_DRIFT  // exactly at limit
        val result = useCase(makeSig(), makeDevice(averagedRssi = deviceRssi), stableState())
        assertTrue(result.wasRefined)
    }

    // --- Guard: structural anchor ---

    @Test
    fun `skips when device has no manufacturer id overlap with signature`() {
        val sigWithId = makeSig(manufacturerIds = listOf(0x0075))
        val deviceDiffId = makeDevice(manufacturerIds = listOf(0x00E0))
        val result = useCase(sigWithId, deviceDiffId, stableState())
        assertFalse(result.wasRefined)
        assertTrue(result.skippedReason!!.contains("anchor", ignoreCase = true))
    }

    @Test
    fun `proceeds when device shares at least one manufacturer id`() {
        val result = useCase(makeSig(), makeDevice(manufacturerIds = listOf(0x0075, 0x00E0)), stableState())
        assertTrue(result.wasRefined)
    }

    @Test
    fun `proceeds when signature has no manufacturer ids (no anchor required)`() {
        val sigNoIds = makeSig(manufacturerIds = emptyList())
        val result = useCase(sigNoIds, makeDevice(), stableState())
        assertTrue(result.wasRefined)
    }

    // --- Version and observation count ---

    @Test
    fun `version is incremented on each refinement`() {
        val sig = makeSig(version = 3, observationCount = 10)
        val result = useCase(sig, makeDevice(), stableState())
        assertEquals(4, result.signature!!.version)
    }

    @Test
    fun `observationCount is incremented on each refinement`() {
        val sig = makeSig(observationCount = 7)
        val result = useCase(sig, makeDevice(), stableState())
        assertEquals(8, result.signature!!.observationCount)
    }

    @Test
    fun `version 0 treated as 1 (old saves compatibility)`() {
        val sig = makeSig(version = 0)
        val result = useCase(sig, makeDevice(), stableState())
        // coerceAtLeast(1) + 1 = 2
        assertEquals(2, result.signature!!.version)
    }

    // --- Behavior profile (RSSI avg + bounds) ---

    @Test
    fun `rssi average is updated via welford formula`() {
        val profile = KnownBehaviorProfile(
            typicalRssiAtClose = -60,
            minSeenCount = 10,
            rssiSampleCount = 4,
            maxSeenCount = 30,
            visibleAtStopCount = 0,
            totalObservations = 4
        )
        val sig = makeSig(behaviorProfile = profile)
        val device = makeDevice(averagedRssi = -65)
        val result = useCase(sig, device, stableState())
        // new avg = ((-60 * 4) + (-65)) / 5 = -305 / 5 = -61
        assertEquals(-61, result.signature!!.behaviorProfile!!.typicalRssiAtClose)
    }

    @Test
    fun `rssiSampleCount is incremented`() {
        val profile = KnownBehaviorProfile(
            typicalRssiAtClose = -65, minSeenCount = 10, rssiSampleCount = 3,
            maxSeenCount = 30, visibleAtStopCount = 0, totalObservations = 3
        )
        val sig = makeSig(behaviorProfile = profile)
        val result = useCase(sig, makeDevice(), stableState())
        assertEquals(4, result.signature!!.behaviorProfile!!.rssiSampleCount)
    }

    @Test
    fun `rssiMin tracks weakest observed rssi`() {
        val profile = KnownBehaviorProfile(
            typicalRssiAtClose = -65, minSeenCount = 10, rssiSampleCount = 5,
            maxSeenCount = 30, visibleAtStopCount = 0, totalObservations = 5,
            rssiMin = -68, rssiMax = -62
        )
        val sig = makeSig(behaviorProfile = profile)
        val device = makeDevice(averagedRssi = -70)  // new weakest within drift limit: -65-15=-80 → clamped
        val result = useCase(sig, device, stableState())
        assertTrue(result.signature!!.behaviorProfile!!.rssiMin!! <= -68)
    }

    @Test
    fun `rssiMax tracks strongest observed rssi`() {
        val profile = KnownBehaviorProfile(
            typicalRssiAtClose = -65, minSeenCount = 10, rssiSampleCount = 5,
            maxSeenCount = 30, visibleAtStopCount = 0, totalObservations = 5,
            rssiMin = -70, rssiMax = -60
        )
        val sig = makeSig(behaviorProfile = profile)
        val device = makeDevice(averagedRssi = -58)  // stronger
        val result = useCase(sig, device, stableState())
        assertTrue(result.signature!!.behaviorProfile!!.rssiMax!! >= -60)
    }

    @Test
    fun `rssi range is clamped to MAX_RSSI_RANGE`() {
        val farApart = KnownBehaviorProfile(
            typicalRssiAtClose = -50, minSeenCount = 10, rssiSampleCount = 5,
            maxSeenCount = 30, visibleAtStopCount = 0, totalObservations = 5,
            rssiMin = -90, rssiMax = -30
        )
        val sig = makeSig(behaviorProfile = farApart)
        val result = useCase(sig, makeDevice(averagedRssi = -50), stableState())
        val profile = result.signature!!.behaviorProfile!!
        assertTrue(profile.rssiMax!! - profile.rssiMin!! <= AdaptiveSignatureRefineUseCase.MAX_RSSI_RANGE)
    }

    // --- Prefix frequency ---

    @Test
    fun `prefix frequency is incremented for observed known-id prefixes`() {
        val sig = makeSig(
            prefixes = listOf("0075:deadbeef"),
            prefixFrequency = mapOf("0075:deadbeef" to 2)
        )
        val device = makeDevice(
            manufacturerIds = listOf(0x0075),
            manufacturerDataHex = mapOf(0x0075 to "deadbeef1122")
        )
        val result = useCase(sig, device, stableState())
        assertEquals(3, result.signature!!.manufacturerDataPrefixFrequency["0075:deadbeef"])
    }

    @Test
    fun `prefix from unknown manufacturer id is ignored`() {
        val sig = makeSig(manufacturerIds = listOf(0x0075))
        val device = makeDevice(
            manufacturerIds = listOf(0x0075, 0x00E0),
            manufacturerDataHex = mapOf(
                0x0075 to "deadbeef0000",
                0x00E0 to "aabbccdd1122"  // 0x00E0 not in sig
            )
        )
        val result = useCase(sig, device, stableState())
        assertFalse(result.signature!!.manufacturerDataPrefixFrequency.containsKey("00e0:aabbccdd"))
    }

    @Test
    fun `returned signature has null when refinement was skipped`() {
        val state = DeviceTemporalState(rssiHistory = noisyHistory)
        val result = useCase(makeSig(), makeDevice(), state)
        assertNull(result.signature)
        assertFalse(result.wasRefined)
    }

    @Test
    fun `deltaDescription is set when refinement succeeds`() {
        val result = useCase(makeSig(), makeDevice(), stableState())
        assertTrue(result.wasRefined)
        assertNotNull(result.deltaDescription)
        assertTrue(result.deltaDescription!!.isNotBlank())
    }
}
