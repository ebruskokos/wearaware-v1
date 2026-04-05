package com.wearaware.app.ui.viewmodel

import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.BleRepository
import com.wearaware.app.domain.repository.CaptureRepository
import com.wearaware.app.domain.repository.LearnedSignatureRepository
import com.wearaware.app.domain.usecase.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelLearnedTest {

    private val testDispatcher = StandardTestDispatcher()

    private val bleRepository = mockk<BleRepository>(relaxed = true)
    private val captureRepository = mockk<CaptureRepository>(relaxed = true)
    private val matchTargetDevice = mockk<MatchTargetDeviceUseCase>(relaxed = true)
    private val compareCapturesUseCase = mockk<CompareCapturesUseCase>(relaxed = true)
    private val saveLearnedSignature = mockk<SaveLearnedSignatureUseCase>(relaxed = true)
    private val matchLearnedSignature = MatchLearnedSignatureUseCase()  // real impl
    private val clearLearnedSignature = mockk<ClearLearnedSignatureUseCase>(relaxed = true)
    private val learnedSignatureRepository = mockk<LearnedSignatureRepository>(relaxed = true)

    private lateinit var viewModel: CaptureViewModel
    private val deviceFlow = MutableStateFlow<List<ObservedDevice>>(emptyList())

    private val glassesDevice = CapturedDevice(
        fingerprintId = "fp-glasses",
        advertisedName = null,
        macAddress = null,
        manufacturerIds = listOf(0x01AB),
        manufacturerDataSummary = "01ab:deadbeef01234567",
        serviceUuids = emptyList(),
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

    private val targetSession = CaptureSession(
        id = "session-TARGET",
        type = CaptureType.TARGET,
        startedAt = System.currentTimeMillis() - 30_000,
        stoppedAt = System.currentTimeMillis(),
        devices = listOf(glassesDevice)
    )

    private val savedSig = LearnedDeviceSignature(
        displayName = "My Meta Glasses",
        savedAt = 1_000_000L,
        fingerprintId = "fp-glasses",
        manufacturerIds = listOf(0x01AB),
        manufacturerDataPrefixes = listOf("01ab:deadbeef"),
        serviceUuids = emptyList()
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { bleRepository.observedDevices } returns deviceFlow
        every { bleRepository.isScanning } returns false
        every { bleRepository.isBleAvailable } returns true
        coEvery { captureRepository.getSession(CaptureType.TARGET) } returns targetSession
        coEvery { captureRepository.getSession(CaptureType.BASELINE) } returns null
        every { learnedSignatureRepository.load() } returns null
        every { compareCapturesUseCase(any(), any(), any()) } returns emptyList()
        // SaveLearnedSignatureUseCase returns the built signature directly
        every { saveLearnedSignature(any()) } returns savedSig

        viewModel = CaptureViewModel(
            bleRepository,
            captureRepository,
            matchTargetDevice,
            compareCapturesUseCase,
            saveLearnedSignature,
            matchLearnedSignature,
            clearLearnedSignature,
            learnedSignatureRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `learnDevice saves signature and sets learnSaveConfirmation`() = runTest {
        advanceUntilIdle()  // init completes: target session loaded into state

        viewModel.learnDevice("fp-glasses")
        // learnDevice() sets state synchronously then launches a 3-second auto-clear coroutine.
        // Advance only 1 second so the confirmation is still visible.
        advanceTimeBy(1_000)

        verify { saveLearnedSignature(glassesDevice) }
        assertEquals("Saved as My Meta Glasses", viewModel.uiState.value.learnSaveConfirmation)
        assertEquals("My Meta Glasses", viewModel.uiState.value.learnedSignature?.displayName)
    }

    @Test
    fun `clearLearnedDevice clears signature and match results from state`() = runTest {
        advanceUntilIdle()
        viewModel.clearLearnedDevice()
        advanceUntilIdle()

        verify { clearLearnedSignature() }
        assertNull(viewModel.uiState.value.learnedSignature)
        assertTrue(viewModel.uiState.value.learnedMatchResults.isEmpty())
    }

    @Test
    fun `learnDevice with unknown fingerprintId is a no-op`() = runTest {
        advanceUntilIdle()
        viewModel.learnDevice("nonexistent-fp")
        advanceUntilIdle()

        verify(exactly = 0) { saveLearnedSignature(any()) }
    }

    @Test
    fun `Apple-only device is saved when explicitly tapped`() = runTest {
        val appleDevice = glassesDevice.copy(
            fingerprintId = "fp-apple",
            manufacturerIds = listOf(0x004C),
            manufacturerDataSummary = "004c:aabbccdd"
        )
        val appleSession = targetSession.copy(devices = listOf(appleDevice))
        val appleSig = savedSig.copy(fingerprintId = "fp-apple")
        coEvery { captureRepository.getSession(CaptureType.TARGET) } returns appleSession
        every { saveLearnedSignature(any()) } returns appleSig

        // Recreate viewModel with apple session
        val vm = CaptureViewModel(
            bleRepository, captureRepository, matchTargetDevice,
            compareCapturesUseCase, saveLearnedSignature, matchLearnedSignature,
            clearLearnedSignature, learnedSignatureRepository
        )
        advanceUntilIdle()

        vm.learnDevice("fp-apple")
        advanceUntilIdle()

        // No Apple suppression for explicit user-initiated save
        verify { saveLearnedSignature(appleDevice) }
    }
}
