package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class BuildKnownTargetSignatureUseCaseTest {

    private val useCase = BuildKnownTargetSignatureUseCase()

    private fun makeFingerprint(
        fingerprintId: String = "fp-abc",
        manufacturerIds: List<Int> = listOf(0x0075),
        manufacturerDataHex: Map<Int, String> = mapOf(0x0075 to "deadbeef01234567"),
        serviceUuids: List<String> = listOf("uuid-glasses")
    ) = DeviceFingerprint(
        fingerprintId = fingerprintId,
        manufacturerIds = manufacturerIds,
        manufacturerNames = listOf("Meta"),
        manufacturerDataHex = manufacturerDataHex,
        serviceUuids = serviceUuids,
        normalizedName = null,
        txPower = null
    )

    private fun makeObservedDevice(
        id: String = "fp-abc",
        fingerprint: DeviceFingerprint? = makeFingerprint(),
        averagedRssi: Int = -57,
        seenCount: Int = 100
    ) = ObservedDevice(
        id = id,
        advertisedName = "Meta Smart Glasses",
        rawRssi = -57,
        averagedRssi = averagedRssi,
        proximityLabel = ProximityLabel.VERY_CLOSE,
        visibilityState = VisibilityState.DETECTED_NOW,
        firstSeenAt = 0L,
        lastSeenAt = 0L,
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

    private fun makeGattResult(
        deviceAddress: String = "AA:BB:CC:DD:EE:FF",
        serviceUuids: List<String> = listOf("0000180a-0000-1000-8000-00805f9b34fb")
    ) = GattDiscoveryResult(
        deviceAddress = deviceAddress,
        serviceUuids = serviceUuids,
        characteristicUuids = mapOf("0000180a-0000-1000-8000-00805f9b34fb" to listOf("characteristic-1")),
        discoveredAt = 1000L
    )

    @Test
    fun `builds signature with manufacturer data prefixes from observed device`() {
        val sig = useCase(makeObservedDevice(), makeGattResult())
        assertEquals(listOf("0075:deadbeef"), sig.manufacturerDataPrefixes)
    }

    @Test
    fun `builds signature with GATT service UUIDs`() {
        val sig = useCase(makeObservedDevice(), makeGattResult())
        assertEquals(listOf("0000180a-0000-1000-8000-00805f9b34fb"), sig.gattServiceUuids)
    }

    @Test
    fun `builds signature with manufacturer IDs from fingerprint`() {
        val sig = useCase(makeObservedDevice(), makeGattResult())
        assertEquals(listOf(0x0075), sig.manufacturerIds)
    }

    @Test
    fun `builds signature with fingerprintId from observed device`() {
        val sig = useCase(makeObservedDevice(id = "my-fp"), makeGattResult())
        assertEquals("my-fp", sig.fingerprintId)
    }

    @Test
    fun `builds behavior profile from observed device`() {
        val sig = useCase(makeObservedDevice(averagedRssi = -55, seenCount = 80), makeGattResult())
        assertNotNull(sig.behaviorProfile)
        val profile = sig.behaviorProfile
        assertEquals(-55, profile?.typicalRssiAtClose)
        assertEquals(80, profile?.minSeenCount)
    }

    @Test
    fun `falls back to gatt device address as fingerprintId when observed device is null`() {
        val sig = useCase(null, makeGattResult(deviceAddress = "AA:BB:CC:DD:EE:FF"))
        assertEquals("AA:BB:CC:DD:EE:FF", sig.fingerprintId)
    }

    @Test
    fun `when observed device is null manufacturer fields are empty`() {
        val sig = useCase(null, makeGattResult())
        assertTrue(sig.manufacturerIds.isEmpty())
        assertTrue(sig.manufacturerDataPrefixes.isEmpty())
        assertTrue(sig.serviceUuids.isEmpty())
        assertNull(sig.behaviorProfile)
    }

    @Test
    fun `displayName is always My Meta Glasses`() {
        val sig = useCase(makeObservedDevice(), makeGattResult())
        assertEquals("My Meta Glasses", sig.displayName)
    }
}
