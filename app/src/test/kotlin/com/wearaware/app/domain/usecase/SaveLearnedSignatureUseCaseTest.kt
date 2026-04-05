package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.LearnedSignatureRepository
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SaveLearnedSignatureUseCaseTest {

    private lateinit var repo: LearnedSignatureRepository
    private lateinit var useCase: SaveLearnedSignatureUseCase

    @Before
    fun setUp() {
        repo = mockk(relaxed = true)
        useCase = SaveLearnedSignatureUseCase(repo)
    }

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
        val result = useCase(makeDevice())
        assertEquals("My Meta Glasses", result.displayName)
        verify { repo.save(result) }
    }

    @Test
    fun `saves fingerprintId from device`() {
        val result = useCase(makeDevice(fingerprintId = "fp-abc"))
        assertEquals("fp-abc", result.fingerprintId)
    }

    @Test
    fun `saves manufacturerIds from device`() {
        val result = useCase(makeDevice(manufacturerIds = listOf(0x01AB)))
        assertEquals(listOf(0x01AB), result.manufacturerIds)
    }

    @Test
    fun `extracts manufacturer data prefix from summary`() {
        val result = useCase(makeDevice(manufacturerDataSummary = "01ab:deadbeef01234567"))
        assertEquals(listOf("01ab:deadbeef"), result.manufacturerDataPrefixes)
    }

    @Test
    fun `saves serviceUuids from device`() {
        val result = useCase(makeDevice(serviceUuids = listOf("uuid-x")))
        assertEquals(listOf("uuid-x"), result.serviceUuids)
    }

    @Test
    fun `null manufacturerDataSummary produces empty prefixes`() {
        val result = useCase(makeDevice(manufacturerDataSummary = null))
        assertTrue(result.manufacturerDataPrefixes.isEmpty())
    }

    @Test
    fun `repository save is called exactly once`() {
        useCase(makeDevice())
        verify(exactly = 1) { repo.save(any()) }
    }
}
