package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.LearnedSignatureRepository
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test

class SaveLearnedSignatureUseCaseTest {

    private val repo = mockk<LearnedSignatureRepository>(relaxed = true)
    private val useCase = SaveLearnedSignatureUseCase(repo)

    private fun makeDevice(
        fingerprintId: String = "fp-abc",
        manufacturerIds: List<Int> = listOf(0x01AB),
        manufacturerDataSummary: String? = "01ab:deadbeef01234567",
        serviceUuids: List<String> = listOf("uuid-x")
    ) = CapturedDevice(
        fingerprintId = fingerprintId,
        advertisedName = null,
        macAddress = null,
        manufacturerIds = manufacturerIds,
        manufacturerDataSummary = manufacturerDataSummary,
        serviceUuids = serviceUuids,
        category = DeviceCategory.UNKNOWN_BLE_DEVICE,
        companyNames = emptyList(),
        firstSeenInCapture = 0L,
        lastSeenInCapture = 0L,
        peakRssi = -57,
        averageRssi = -57,
        seenCount = 128,
        visibleAtStop = true,
        targetMatchScore = null,
        targetMatchSignals = emptyList()
    )

    @Test
    fun `saves signature with correct displayName`() {
        useCase(makeDevice())
        val slot = slot<LearnedDeviceSignature>()
        verify { repo.save(capture(slot)) }
        assertEquals("My Meta Glasses", slot.captured.displayName)
    }

    @Test
    fun `saves fingerprintId from device`() {
        useCase(makeDevice(fingerprintId = "fp-abc"))
        val slot = slot<LearnedDeviceSignature>()
        verify { repo.save(capture(slot)) }
        assertEquals("fp-abc", slot.captured.fingerprintId)
    }

    @Test
    fun `saves manufacturerIds from device`() {
        useCase(makeDevice(manufacturerIds = listOf(0x01AB)))
        val slot = slot<LearnedDeviceSignature>()
        verify { repo.save(capture(slot)) }
        assertEquals(listOf(0x01AB), slot.captured.manufacturerIds)
    }

    @Test
    fun `extracts manufacturer data prefix from summary`() {
        useCase(makeDevice(manufacturerDataSummary = "01ab:deadbeef01234567"))
        val slot = slot<LearnedDeviceSignature>()
        verify { repo.save(capture(slot)) }
        assertEquals(listOf("01ab:deadbeef"), slot.captured.manufacturerDataPrefixes)
    }

    @Test
    fun `saves serviceUuids from device`() {
        useCase(makeDevice(serviceUuids = listOf("uuid-x")))
        val slot = slot<LearnedDeviceSignature>()
        verify { repo.save(capture(slot)) }
        assertEquals(listOf("uuid-x"), slot.captured.serviceUuids)
    }

    @Test
    fun `null manufacturerDataSummary produces empty prefixes`() {
        useCase(makeDevice(manufacturerDataSummary = null))
        val slot = slot<LearnedDeviceSignature>()
        verify { repo.save(capture(slot)) }
        assertTrue(slot.captured.manufacturerDataPrefixes.isEmpty())
    }

    @Test
    fun `repository save is called exactly once`() {
        useCase(makeDevice())
        verify(exactly = 1) { repo.save(any()) }
    }
}
