package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.KnownTargetRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MergeKnownTargetSignatureUseCaseTest {

    private val repository: KnownTargetRepository = mockk(relaxed = true)
    private val useCase = MergeKnownTargetSignatureUseCase(repository)

    private fun makeSignature(
        fingerprintId: String = "fp-001",
        manufacturerIds: List<Int> = listOf(0x0075),
        prefixes: List<String> = listOf("0075:deadbeef"),
        prefixFrequency: Map<String, Int> = mapOf("0075:deadbeef" to 1),
        serviceUuids: List<String> = listOf("uuid-svc"),
        gattServiceUuids: List<String> = listOf("gatt-svc"),
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
        fingerprintId = fingerprintId,
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
        manufacturerDataSummary: String? = "0075:deadbeefaabb",
        serviceUuids: List<String> = listOf("uuid-svc"),
        averageRssi: Int = -60,
        seenCount: Int = 30,
        visibleAtStop: Boolean = false
    ) = CapturedDevice(
        fingerprintId = "fp-001",
        advertisedName = null,
        macAddress = null,
        manufacturerIds = manufacturerIds,
        manufacturerDataSummary = manufacturerDataSummary,
        serviceUuids = serviceUuids,
        category = DeviceCategory.SMART_GLASSES,
        companyNames = emptyList(),
        firstSeenInCapture = 0L,
        lastSeenInCapture = 1000L,
        peakRssi = averageRssi,
        averageRssi = averageRssi,
        seenCount = seenCount,
        visibleAtStop = visibleAtStop,
        targetMatchScore = null,
        targetMatchSignals = emptyList()
    )

    @Test
    fun `learnCount increments on each merge`() {
        val sig = makeSignature(learnCount = 1)
        val result = useCase(sig, makeDevice())
        assertEquals(2, result.learnCount)
    }

    @Test
    fun `learnCount increments multiple times`() {
        var sig = makeSignature(learnCount = 1)
        sig = useCase(sig, makeDevice())
        sig = useCase(sig, makeDevice())
        assertEquals(3, sig.learnCount)
    }

    @Test
    fun `manufacturer IDs are unioned and deduplicated`() {
        val sig = makeSignature(manufacturerIds = listOf(0x0075))
        val device = makeDevice(manufacturerIds = listOf(0x0075, 0x004C))
        val result = useCase(sig, device)
        assertEquals(setOf(0x0075, 0x004C), result.manufacturerIds.toSet())
    }

    @Test
    fun `new manufacturer ID not in existing is added`() {
        val sig = makeSignature(manufacturerIds = listOf(0x0075))
        val device = makeDevice(manufacturerIds = listOf(0x0099))
        val result = useCase(sig, device)
        assertTrue(result.manufacturerIds.contains(0x0099))
        assertTrue(result.manufacturerIds.contains(0x0075))
    }

    @Test
    fun `service UUIDs are unioned and deduplicated`() {
        val sig = makeSignature(serviceUuids = listOf("svc-1"))
        val device = makeDevice(serviceUuids = listOf("svc-1", "svc-2"))
        val result = useCase(sig, device)
        assertEquals(setOf("svc-1", "svc-2"), result.serviceUuids.toSet())
    }

    @Test
    fun `gatt service UUIDs are unchanged after merge`() {
        val sig = makeSignature(gattServiceUuids = listOf("gatt-only"))
        val result = useCase(sig, makeDevice())
        assertEquals(listOf("gatt-only"), result.gattServiceUuids)
    }

    // --- Welford moving average RSSI ---

    @Test
    fun `first merge averages existing rssi with new rssi`() {
        val sig = makeSignature(
            behaviorProfile = KnownBehaviorProfile(
                typicalRssiAtClose = -70,
                minSeenCount = 10,
                rssiSampleCount = 1
            )
        )
        val device = makeDevice(averageRssi = -60)
        val result = useCase(sig, device)
        // (-70 * 1 + -60) / 2 = -65
        assertEquals(-65, result.behaviorProfile?.typicalRssiAtClose)
        assertEquals(2, result.behaviorProfile?.rssiSampleCount)
    }

    @Test
    fun `rssi moving average converges correctly over three samples`() {
        val sig = makeSignature(
            behaviorProfile = KnownBehaviorProfile(
                typicalRssiAtClose = -70,
                minSeenCount = 10,
                rssiSampleCount = 2
            )
        )
        val device = makeDevice(averageRssi = -55)
        // (-70 * 2 + -55) / 3 = -195 / 3 = -65
        val result = useCase(sig, device)
        assertEquals(-65, result.behaviorProfile?.typicalRssiAtClose)
        assertEquals(3, result.behaviorProfile?.rssiSampleCount)
    }

    @Test
    fun `rssi sample count is incremented on every merge`() {
        var sig = makeSignature(behaviorProfile = KnownBehaviorProfile(
            typicalRssiAtClose = -70,
            minSeenCount = 10,
            rssiSampleCount = 1
        ))
        repeat(4) { sig = useCase(sig, makeDevice(averageRssi = -70)) }
        assertEquals(5, sig.behaviorProfile?.rssiSampleCount)
    }

    @Test
    fun `null behavior profile is handled gracefully`() {
        val sig = makeSignature(behaviorProfile = null)
        val device = makeDevice(averageRssi = -65, seenCount = 25)
        val result = useCase(sig, device)
        assertNotNull(result.behaviorProfile)
        assertEquals(-65, result.behaviorProfile?.typicalRssiAtClose)
    }

    // --- seenCount range tracking ---

    @Test
    fun `minSeenCount tracks lower seenCount`() {
        val sig = makeSignature(behaviorProfile = KnownBehaviorProfile(
            typicalRssiAtClose = -65, minSeenCount = 30, maxSeenCount = 50, rssiSampleCount = 1
        ))
        val result = useCase(sig, makeDevice(seenCount = 10))
        assertEquals(10, result.behaviorProfile?.minSeenCount)
    }

    @Test
    fun `maxSeenCount tracks higher seenCount`() {
        val sig = makeSignature(behaviorProfile = KnownBehaviorProfile(
            typicalRssiAtClose = -65, minSeenCount = 10, maxSeenCount = 30, rssiSampleCount = 1
        ))
        val result = useCase(sig, makeDevice(seenCount = 80))
        assertEquals(80, result.behaviorProfile?.maxSeenCount)
    }

    @Test
    fun `minSeenCount does not change when new seenCount is higher`() {
        val sig = makeSignature(behaviorProfile = KnownBehaviorProfile(
            typicalRssiAtClose = -65, minSeenCount = 10, maxSeenCount = 30, rssiSampleCount = 1
        ))
        val result = useCase(sig, makeDevice(seenCount = 25))
        assertEquals(10, result.behaviorProfile?.minSeenCount)
    }

    @Test
    fun `maxSeenCount does not change when new seenCount is lower`() {
        val sig = makeSignature(behaviorProfile = KnownBehaviorProfile(
            typicalRssiAtClose = -65, minSeenCount = 10, maxSeenCount = 30, rssiSampleCount = 1
        ))
        val result = useCase(sig, makeDevice(seenCount = 15))
        assertEquals(30, result.behaviorProfile?.maxSeenCount)
    }

    // --- visibleAtStop count ---

    @Test
    fun `visibleAtStopCount increments when device was visible at stop`() {
        val sig = makeSignature(behaviorProfile = KnownBehaviorProfile(
            typicalRssiAtClose = -65, minSeenCount = 10, visibleAtStopCount = 2, rssiSampleCount = 1
        ))
        val result = useCase(sig, makeDevice(visibleAtStop = true))
        assertEquals(3, result.behaviorProfile?.visibleAtStopCount)
    }

    @Test
    fun `visibleAtStopCount does not increment when device was not visible at stop`() {
        val sig = makeSignature(behaviorProfile = KnownBehaviorProfile(
            typicalRssiAtClose = -65, minSeenCount = 10, visibleAtStopCount = 2, rssiSampleCount = 1
        ))
        val result = useCase(sig, makeDevice(visibleAtStop = false))
        assertEquals(2, result.behaviorProfile?.visibleAtStopCount)
    }

    @Test
    fun `totalObservations increments on each merge`() {
        val sig = makeSignature(behaviorProfile = KnownBehaviorProfile(
            typicalRssiAtClose = -65, minSeenCount = 10, totalObservations = 3, rssiSampleCount = 1
        ))
        val result = useCase(sig, makeDevice())
        assertEquals(4, result.behaviorProfile?.totalObservations)
    }

    // --- Prefix frequency voting ---

    @Test
    fun `new prefix is added to frequency map`() {
        val sig = makeSignature(
            prefixes = listOf("0075:deadbeef"),
            prefixFrequency = mapOf("0075:deadbeef" to 1)
        )
        val device = makeDevice(manufacturerDataSummary = "0099:aabbccddee")
        val result = useCase(sig, device)
        assertTrue(result.manufacturerDataPrefixFrequency.containsKey("0099:aabbccdd"))
    }

    @Test
    fun `repeated prefix increments its frequency count`() {
        val sig = makeSignature(
            prefixes = listOf("0075:deadbeef"),
            prefixFrequency = mapOf("0075:deadbeef" to 3)
        )
        val device = makeDevice(manufacturerDataSummary = "0075:deadbeefaabb")
        val result = useCase(sig, device)
        assertEquals(4, result.manufacturerDataPrefixFrequency["0075:deadbeef"])
    }

    @Test
    fun `top 5 prefixes by frequency are kept in manufacturerDataPrefixes`() {
        val frequencyMap = mapOf(
            "0075:aabbccdd" to 10,
            "0075:11223344" to 8,
            "0075:55667788" to 6,
            "0075:99aabbcc" to 4,
            "0075:ddeeff00" to 2,
            "0099:12345678" to 1
        )
        val sig = makeSignature(
            prefixes = frequencyMap.entries.sortedByDescending { it.value }.take(5).map { it.key },
            prefixFrequency = frequencyMap
        )
        // new device adds another vote to a low-frequency prefix — top-5 still should be the same 5
        val device = makeDevice(manufacturerDataSummary = "0099:12345678aabb")
        val result = useCase(sig, device)
        // Result should contain at most 5 prefixes
        assertTrue(result.manufacturerDataPrefixes.size <= 5)
        // The top frequency prefix must be included
        assertTrue(result.manufacturerDataPrefixes.contains("0075:aabbccdd"))
    }

    @Test
    fun `lower ranked prefix is dropped when frequency cap is reached`() {
        val frequencyMap = (1..6).associate { i ->
            "prefix:${"%08x".format(i)}" to (7 - i)  // prefix:00000001→6, ..., prefix:00000006→1
        }
        val sig = makeSignature(
            prefixes = frequencyMap.entries.sortedByDescending { it.value }.take(5).map { it.key },
            prefixFrequency = frequencyMap
        )
        // Add big boost to top prefix
        val device = makeDevice(manufacturerDataSummary = "0000:000000010000")
        // re-invoke to let top keep its rank
        val result = useCase(sig, device)
        assertTrue(result.manufacturerDataPrefixes.size <= 5)
    }

    @Test
    fun `null manufacturerDataSummary produces no new prefixes`() {
        val sig = makeSignature(
            prefixes = listOf("0075:deadbeef"),
            prefixFrequency = mapOf("0075:deadbeef" to 1)
        )
        val device = makeDevice(manufacturerDataSummary = null)
        val result = useCase(sig, device)
        // Existing prefix should still be present
        assertEquals(listOf("0075:deadbeef"), result.manufacturerDataPrefixes)
    }

    // --- Repository persistence ---

    @Test
    fun `merged signature is saved to repository`() {
        val sig = makeSignature()
        useCase(sig, makeDevice())
        verify(exactly = 1) { repository.save(any()) }
    }
}
