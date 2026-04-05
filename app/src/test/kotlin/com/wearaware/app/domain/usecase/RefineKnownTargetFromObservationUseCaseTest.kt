package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.KnownTargetRepository
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test

class RefineKnownTargetFromObservationUseCaseTest {

    private val repository: KnownTargetRepository = mockk(relaxed = true)
    private val useCase = RefineKnownTargetFromObservationUseCase(repository)

    private fun makeSignature(
        manufacturerIds: List<Int> = listOf(0x0075),
        prefixes: List<String> = listOf("0075:deadbeef"),
        prefixFrequency: Map<String, Int> = mapOf("0075:deadbeef" to 1),
        serviceUuids: List<String> = listOf("svc-1"),
        gattServiceUuids: List<String> = listOf("gatt-1"),
        behaviorProfile: KnownBehaviorProfile? = KnownBehaviorProfile(
            typicalRssiAtClose = -65,
            minSeenCount = 20,
            rssiSampleCount = 1,
            maxSeenCount = 40,
            visibleAtStopCount = 0,
            totalObservations = 1
        ),
        learnCount: Int = 1
    ) = KnownTargetSignature(
        displayName = "My Meta Glasses",
        savedAt = 1000L,
        fingerprintId = "fp-001",
        manufacturerIds = manufacturerIds,
        manufacturerDataPrefixes = prefixes,
        serviceUuids = serviceUuids,
        gattServiceUuids = gattServiceUuids,
        behaviorProfile = behaviorProfile,
        learnCount = learnCount,
        lastUpdatedAt = 1000L,
        manufacturerDataPrefixFrequency = prefixFrequency
    )

    private fun makeDevice(
        manufacturerIds: List<Int> = listOf(0x0075),
        manufacturerDataHex: Map<Int, String> = mapOf(0x0075 to "deadbeef0000"),
        serviceUuids: List<String> = listOf("svc-1"),
        averagedRssi: Int = -60,
        seenCount: Int = 30,
        visibilityState: VisibilityState = VisibilityState.DETECTED_NOW
    ): ObservedDevice {
        val fingerprint = DeviceFingerprint(
            fingerprintId = "fp-001",
            manufacturerIds = manufacturerIds,
            manufacturerNames = emptyList(),
            manufacturerDataHex = manufacturerDataHex,
            serviceUuids = serviceUuids,
            normalizedName = null,
            txPower = null
        )
        return ObservedDevice(
            id = "fp-001",
            advertisedName = null,
            rawRssi = averagedRssi,
            averagedRssi = averagedRssi,
            proximityLabel = ProximityLabel.NEARBY,
            visibilityState = visibilityState,
            firstSeenAt = 0L,
            lastSeenAt = 1000L,
            seenCount = seenCount,
            classification = ClassificationResult(
                matchedRuleId = null,
                ruleVersion = null,
                category = DeviceCategory.UNKNOWN_BLE_DEVICE,
                displayLabel = "Unknown",
                confidence = ConfidenceLevel.LOW,
                isWearableCandidate = false,
                evaluationNotes = null
            ),
            persistenceAlert = null,
            fingerprint = fingerprint
        )
    }

    @Test
    fun `learnCount increments on refinement`() {
        val result = useCase(makeSignature(learnCount = 3), makeDevice())
        assertEquals(4, result.learnCount)
    }

    @Test
    fun `manufacturer IDs from live device are merged`() {
        val sig = makeSignature(manufacturerIds = listOf(0x0075))
        val device = makeDevice(manufacturerIds = listOf(0x0075, 0x004C))
        val result = useCase(sig, device)
        assertTrue(result.manufacturerIds.contains(0x004C))
        assertTrue(result.manufacturerIds.contains(0x0075))
    }

    @Test
    fun `service UUIDs from live device are merged`() {
        val sig = makeSignature(serviceUuids = listOf("svc-1"))
        val device = makeDevice(serviceUuids = listOf("svc-1", "svc-2"))
        val result = useCase(sig, device)
        assertEquals(setOf("svc-1", "svc-2"), result.serviceUuids.toSet())
    }

    @Test
    fun `gatt service UUIDs are unchanged`() {
        val sig = makeSignature(gattServiceUuids = listOf("gatt-only"))
        val result = useCase(sig, makeDevice())
        assertEquals(listOf("gatt-only"), result.gattServiceUuids)
    }

    @Test
    fun `prefix from fingerprint map is extracted and tracked`() {
        val sig = makeSignature(
            prefixes = listOf("0075:deadbeef"),
            prefixFrequency = mapOf("0075:deadbeef" to 1)
        )
        val device = makeDevice(manufacturerDataHex = mapOf(0x0075 to "deadbeef0000"))
        val result = useCase(sig, device)
        // "0075:deadbeef" frequency should be 2
        assertEquals(2, result.manufacturerDataPrefixFrequency["0075:deadbeef"])
    }

    @Test
    fun `new prefix from live device is added to frequency map`() {
        val sig = makeSignature(
            prefixes = listOf("0075:deadbeef"),
            prefixFrequency = mapOf("0075:deadbeef" to 1)
        )
        val device = makeDevice(manufacturerDataHex = mapOf(0x0099 to "aabbccddee00"))
        val result = useCase(sig, device)
        assertTrue(result.manufacturerDataPrefixFrequency.containsKey("0099:aabbccdd"))
    }

    @Test
    fun `top 5 prefixes are kept even when more are seen`() {
        val freq = (1..6).associate { i -> "pref:${"%08x".format(i)}" to (7 - i) }
        val sig = makeSignature(
            prefixes = freq.entries.sortedByDescending { it.value }.take(5).map { it.key },
            prefixFrequency = freq
        )
        val result = useCase(sig, makeDevice())
        assertTrue(result.manufacturerDataPrefixes.size <= 5)
    }

    // --- Welford moving average RSSI ---

    @Test
    fun `rssi moving average is computed correctly`() {
        val sig = makeSignature(behaviorProfile = KnownBehaviorProfile(
            typicalRssiAtClose = -70,
            minSeenCount = 10,
            rssiSampleCount = 1
        ))
        val result = useCase(sig, makeDevice(averagedRssi = -60))
        // (-70 * 1 + -60) / 2 = -65
        assertEquals(-65, result.behaviorProfile?.typicalRssiAtClose)
        assertEquals(2, result.behaviorProfile?.rssiSampleCount)
    }

    @Test
    fun `rssi sample count grows on each refinement`() {
        var sig = makeSignature(behaviorProfile = KnownBehaviorProfile(
            typicalRssiAtClose = -65, minSeenCount = 10, rssiSampleCount = 1
        ))
        repeat(3) { sig = useCase(sig, makeDevice()) }
        assertEquals(4, sig.behaviorProfile?.rssiSampleCount)
    }

    @Test
    fun `null behavior profile is initialised from live device`() {
        val sig = makeSignature(behaviorProfile = null)
        val result = useCase(sig, makeDevice(averagedRssi = -72, seenCount = 25))
        assertNotNull(result.behaviorProfile)
        assertEquals(-72, result.behaviorProfile?.typicalRssiAtClose)
    }

    // --- seenCount range ---

    @Test
    fun `minSeenCount is updated when live seenCount is lower`() {
        val sig = makeSignature(behaviorProfile = KnownBehaviorProfile(
            typicalRssiAtClose = -65, minSeenCount = 30, maxSeenCount = 60, rssiSampleCount = 1
        ))
        val result = useCase(sig, makeDevice(seenCount = 5))
        assertEquals(5, result.behaviorProfile?.minSeenCount)
    }

    @Test
    fun `maxSeenCount is updated when live seenCount is higher`() {
        val sig = makeSignature(behaviorProfile = KnownBehaviorProfile(
            typicalRssiAtClose = -65, minSeenCount = 10, maxSeenCount = 30, rssiSampleCount = 1
        ))
        val result = useCase(sig, makeDevice(seenCount = 100))
        assertEquals(100, result.behaviorProfile?.maxSeenCount)
    }

    // --- visibleAtStop ---

    @Test
    fun `visibleAtStopCount increments when visibleAtStop is true`() {
        val sig = makeSignature(behaviorProfile = KnownBehaviorProfile(
            typicalRssiAtClose = -65, minSeenCount = 10, visibleAtStopCount = 1, rssiSampleCount = 1
        ))
        val result = useCase(sig, makeDevice(), visibleAtStop = true)
        assertEquals(2, result.behaviorProfile?.visibleAtStopCount)
    }

    @Test
    fun `visibleAtStopCount unchanged when visibleAtStop is false`() {
        val sig = makeSignature(behaviorProfile = KnownBehaviorProfile(
            typicalRssiAtClose = -65, minSeenCount = 10, visibleAtStopCount = 3, rssiSampleCount = 1
        ))
        val result = useCase(sig, makeDevice(), visibleAtStop = false)
        assertEquals(3, result.behaviorProfile?.visibleAtStopCount)
    }

    @Test
    fun `device with no fingerprint produces no new prefixes`() {
        val sig = makeSignature(
            prefixes = listOf("0075:deadbeef"),
            prefixFrequency = mapOf("0075:deadbeef" to 2)
        )
        val device = makeDevice().copy(fingerprint = null)
        val result = useCase(sig, device)
        // Existing prefix frequency unchanged
        assertEquals(2, result.manufacturerDataPrefixFrequency["0075:deadbeef"])
    }

    @Test
    fun `refined signature is saved to repository`() {
        useCase(makeSignature(), makeDevice())
        verify(exactly = 1) { repository.save(any()) }
    }
}
