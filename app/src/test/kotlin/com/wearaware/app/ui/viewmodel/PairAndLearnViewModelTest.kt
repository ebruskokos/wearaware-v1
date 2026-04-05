package com.wearaware.app.ui.viewmodel

import android.app.Activity
import androidx.activity.result.ActivityResult
import com.wearaware.app.data.ble.BleGattManager
import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.BleRepository
import com.wearaware.app.domain.repository.KnownTargetRepository
import com.wearaware.app.domain.repository.LearningSessionRepository
import com.wearaware.app.domain.usecase.BuildKnownTargetSignatureUseCase
import com.wearaware.app.domain.usecase.LogLearningEventUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PairAndLearnViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val knownTargetRepository = mockk<KnownTargetRepository>(relaxed = true)
    private val learningSessionRepository = mockk<LearningSessionRepository>(relaxed = true)
    private val logLearningEvent = mockk<LogLearningEventUseCase>(relaxed = true)
    private val buildKnownTargetSignature = BuildKnownTargetSignatureUseCase()
    private val bleGattManager = mockk<BleGattManager>()
    private val bleRepository = mockk<BleRepository>(relaxed = true)

    private val deviceFlow = MutableStateFlow<List<ObservedDevice>>(emptyList())

    private lateinit var viewModel: PairAndLearnViewModel

    private val testGattResult = GattDiscoveryResult(
        deviceAddress = "AA:BB:CC:DD:EE:FF",
        serviceUuids = listOf("0000180a-0000-1000-8000-00805f9b34fb"),
        characteristicUuids = emptyMap(),
        discoveredAt = 1000L
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { knownTargetRepository.load() } returns null
        every { bleRepository.observedDevices } returns deviceFlow
        viewModel = PairAndLearnViewModel(
            knownTargetRepository = knownTargetRepository,
            learningSessionRepository = learningSessionRepository,
            logLearningEvent = logLearningEvent,
            buildKnownTargetSignature = buildKnownTargetSignature,
            bleGattManager = bleGattManager,
            bleRepository = bleRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is IDLE with no error`() {
        assertEquals(PairingFlowState.IDLE, viewModel.uiState.value.flowState)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `init loads existing signature`() {
        val sig = KnownTargetSignature(
            displayName = "My Meta Glasses", savedAt = 1000L, fingerprintId = "fp",
            manufacturerIds = emptyList(), manufacturerDataPrefixes = emptyList(),
            serviceUuids = emptyList(), gattServiceUuids = emptyList(), behaviorProfile = null
        )
        every { knownTargetRepository.load() } returns sig
        val vm = PairAndLearnViewModel(
            knownTargetRepository, learningSessionRepository, logLearningEvent,
            buildKnownTargetSignature, bleGattManager, bleRepository
        )
        assertEquals(sig, vm.uiState.value.existingSignature)
    }

    @Test
    fun `onCdmResult with RESULT_CANCELLED transitions to FAILED`() = runTest {
        viewModel.onCdmResult(ActivityResult(Activity.RESULT_CANCELED, null))
        advanceUntilIdle()
        assertEquals(PairingFlowState.FAILED, viewModel.uiState.value.flowState)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `successful GATT flow transitions to COMPLETED and saves signature`() = runTest {
        coEvery { bleGattManager.connectAndDiscover("AA:BB:CC:DD:EE:FF") } returns testGattResult
        coEvery { learningSessionRepository.createSession(any(), any()) } just Runs
        coEvery { learningSessionRepository.updateStatus(any(), any()) } just Runs
        coEvery { learningSessionRepository.updateDeviceAddress(any(), any()) } just Runs
        coEvery { logLearningEvent(any(), any(), any()) } returns PairedLearningEvent(
            1L, "session", LearningEventType.SESSION_STARTED, 1000L, null
        )

        viewModel.onCdmAssociated("AA:BB:CC:DD:EE:FF", "session-1")
        advanceUntilIdle()

        assertEquals(PairingFlowState.COMPLETED, viewModel.uiState.value.flowState)
        verify { knownTargetRepository.save(any()) }
    }

    @Test
    fun `GATT failure transitions to FAILED with error message`() = runTest {
        coEvery { bleGattManager.connectAndDiscover(any()) } throws Exception("Connection timeout")
        coEvery { learningSessionRepository.createSession(any(), any()) } just Runs
        coEvery { learningSessionRepository.updateStatus(any(), any()) } just Runs
        coEvery { learningSessionRepository.updateDeviceAddress(any(), any()) } just Runs
        coEvery { logLearningEvent(any(), any(), any()) } returns PairedLearningEvent(
            1L, "session", LearningEventType.GATT_FAILED, 1000L, null
        )

        viewModel.onCdmAssociated("AA:BB:CC:DD:EE:FF", "session-1")
        advanceUntilIdle()

        assertEquals(PairingFlowState.FAILED, viewModel.uiState.value.flowState)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `clearProfile removes signature from repository and resets state`() {
        viewModel.clearProfile()
        verify { knownTargetRepository.clear() }
        assertNull(viewModel.uiState.value.existingSignature)
    }
}
