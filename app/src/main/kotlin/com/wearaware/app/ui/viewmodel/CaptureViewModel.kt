package com.wearaware.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.BleRepository
import com.wearaware.app.domain.repository.CaptureRepository
import com.wearaware.app.domain.repository.LearnedSignatureRepository
import com.wearaware.app.domain.usecase.ClearLearnedSignatureUseCase
import com.wearaware.app.domain.usecase.CompareCapturesUseCase
import com.wearaware.app.domain.usecase.DefaultTargetProfile
import com.wearaware.app.domain.usecase.MatchLearnedSignatureUseCase
import com.wearaware.app.domain.usecase.MatchTargetDeviceUseCase
import com.wearaware.app.domain.usecase.SaveLearnedSignatureUseCase
import com.wearaware.app.domain.usecase.extractPrefixesFromSummary
import com.wearaware.app.domain.model.LearnedMatchInput
import com.wearaware.app.domain.model.LearnedMatchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val bleRepository: BleRepository,
    private val captureRepository: CaptureRepository,
    private val matchTargetDevice: MatchTargetDeviceUseCase,
    private val compareCapturesUseCase: CompareCapturesUseCase,
    private val saveLearnedSignature: SaveLearnedSignatureUseCase,
    private val matchLearnedSignature: MatchLearnedSignatureUseCase,
    private val clearLearnedSignature: ClearLearnedSignatureUseCase,
    private val learnedSignatureRepository: LearnedSignatureRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    /** In-memory accumulator for devices observed during the active capture window. */
    private val accumulator = mutableMapOf<String, DeviceAccumulator>()
    private var captureStartedAt = 0L
    private var activeCaptureType: CaptureType? = null

    /**
     * True if this ViewModel called startScanning() on the repository.
     * Used in onCleared() to only stop scanning if we started it — avoids
     * clearing the device list while ScanViewModel is still actively scanning.
     */
    private var startedScanningForCapture = false

    init {
        // Load any previously persisted captures from Room
        viewModelScope.launch {
            val baseline = captureRepository.getSession(CaptureType.BASELINE)
            val target = captureRepository.getSession(CaptureType.TARGET)
            // Load learned signature first — runCompare reads it from state
            val sig = learnedSignatureRepository.load()
            _uiState.update {
                it.copy(
                    baseline = baseline,
                    baselineCaptureState = if (baseline != null) CaptureState.DONE else CaptureState.IDLE,
                    target = target,
                    targetCaptureState = if (target != null) CaptureState.DONE else CaptureState.IDLE,
                    learnedSignature = sig
                )
            }
            if (target != null) runCompare(baseline, target)
        }

        // Auto-start BLE scanning if it isn't already running (e.g. user navigates directly
        // to CaptureScreen without first using the main scan screen).
        if (!bleRepository.isScanning) {
            bleRepository.startScanning()
            startedScanningForCapture = true
        }
        _uiState.update {
            it.copy(
                isBleScanningActive = bleRepository.isScanning,
                isBleAvailable = bleRepository.isBleAvailable
            )
        }

        // Observe BLE device stream — only accumulates when activeCaptureType != null
        viewModelScope.launch {
            bleRepository.observedDevices.collect { devices ->
                if (activeCaptureType != null) {
                    accumulateDevices(devices)
                    _uiState.update { it.copy(liveAccumulatedCount = accumulator.size) }
                }
            }
        }
    }

    /**
     * Starts a capture of the given type.
     * Only one capture may be active at a time. If the other type is already CAPTURING,
     * this call is a no-op (the UI should disable the button in that case).
     */
    fun startCapture(type: CaptureType) {
        val otherState = if (type == CaptureType.BASELINE)
            _uiState.value.targetCaptureState
        else
            _uiState.value.baselineCaptureState
        if (otherState == CaptureState.CAPTURING) return  // lifecycle rule: no overlap

        viewModelScope.launch {
            captureRepository.deleteSession(type)
            accumulator.clear()
            val startedAt = System.currentTimeMillis()
            captureStartedAt = startedAt
            activeCaptureType = type
            _uiState.update {
                if (type == CaptureType.BASELINE)
                    it.copy(
                        baselineCaptureState = CaptureState.CAPTURING,
                        baseline = null,
                        liveAccumulatedCount = 0,
                        captureStartedAt = startedAt
                    )
                else
                    it.copy(
                        targetCaptureState = CaptureState.CAPTURING,
                        target = null,
                        liveAccumulatedCount = 0,
                        captureStartedAt = startedAt
                    )
            }
        }
    }

    /**
     * Stops the active capture, snapshots accumulated devices, saves to Room, and
     * auto-triggers compare if the other capture also exists.
     */
    fun stopCapture(type: CaptureType) {
        if (activeCaptureType != type) return
        activeCaptureType = null

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val visibleIds = bleRepository.observedDevices.value
                .filter { it.visibilityState == VisibilityState.DETECTED_NOW }
                .map { it.id }
                .toSet()

            val devices = accumulator.values.map { acc ->
                CapturedDevice(
                    fingerprintId = acc.fingerprintId,
                    advertisedName = acc.advertisedName,
                    macAddress = acc.macAddress,
                    manufacturerIds = acc.manufacturerIds,
                    manufacturerDataSummary = acc.manufacturerDataSummary,
                    serviceUuids = acc.serviceUuids,
                    category = acc.category,
                    companyNames = acc.companyNames,
                    firstSeenInCapture = acc.firstSeenInCapture,
                    lastSeenInCapture = acc.lastSeenInCapture,
                    peakRssi = acc.peakRssi,
                    averageRssi = acc.averageRssi,
                    seenCount = acc.seenCount,
                    visibleAtStop = acc.fingerprintId in visibleIds,
                    targetMatchScore = acc.targetMatchScore,
                    targetMatchSignals = acc.targetMatchSignals,
                    observationLog = acc.buildObservationLog()
                )
            }

            val session = CaptureSession(
                id = UUID.randomUUID().toString(),
                type = type,
                startedAt = captureStartedAt,
                stoppedAt = now,
                devices = devices
            )
            captureRepository.saveSession(session)
            accumulator.clear()

            val newBaseline = if (type == CaptureType.BASELINE) session else _uiState.value.baseline
            val newTarget = if (type == CaptureType.TARGET) session else _uiState.value.target

            _uiState.update {
                if (type == CaptureType.BASELINE)
                    it.copy(
                        baselineCaptureState = CaptureState.DONE,
                        baseline = session,
                        liveAccumulatedCount = 0,
                        captureStartedAt = 0L
                    )
                else
                    it.copy(
                        targetCaptureState = CaptureState.DONE,
                        target = session,
                        liveAccumulatedCount = 0,
                        captureStartedAt = 0L
                    )
            }

            if (newTarget != null) runCompare(newBaseline, newTarget)
        }
    }

    /**
     * Saves the device with the given fingerprintId as the learned glasses signature.
     * Device must exist in the current target session.
     * Triggers a 3-second confirmation message via learnSaveConfirmation.
     */
    fun learnDevice(fingerprintId: String) {
        val device = _uiState.value.target?.devices
            ?.firstOrNull { it.fingerprintId == fingerprintId } ?: return
        saveLearnedSignature(device)
        val sig = learnedSignatureRepository.load() ?: return
        val matchResults = computeLearnedMatchesForCompare(sig, _uiState.value.compareResults)
        _uiState.update {
            it.copy(
                learnedSignature = sig,
                learnedMatchResults = matchResults,
                learnSaveConfirmation = "Saved as My Meta Glasses"
            )
        }
        viewModelScope.launch {
            delay(3_000)
            _uiState.update { it.copy(learnSaveConfirmation = null) }
        }
    }

    fun clearLearnedDevice() {
        clearLearnedSignature()
        _uiState.update {
            it.copy(
                learnedSignature = null,
                learnedMatchResults = emptyMap()
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Only stop scanning if this ViewModel was the one that started it —
        // avoids clearing the shared BleRepository device list while ScanViewModel is active.
        if (startedScanningForCapture) {
            bleRepository.stopScanning()
        }
    }

    private fun accumulateDevices(devices: List<ObservedDevice>) {
        val now = System.currentTimeMillis()
        val matchScores = matchTargetDevice(devices, DefaultTargetProfile.WAYFARER_00ZS)

        for (device in devices) {
            val existing = accumulator[device.id]
            if (existing == null) {
                val matchResult = matchScores[device.id]
                val mfDataSummary = device.fingerprint?.manufacturerDataHex
                    ?.entries?.joinToString(",") { (id, hex) ->
                        "${id.toString(16).padStart(4, '0')}:$hex"
                    }?.takeIf { it.isNotEmpty() }
                accumulator[device.id] = DeviceAccumulator(
                    fingerprintId = device.id,
                    advertisedName = device.advertisedName,
                    macAddress = device.macAddress,
                    manufacturerIds = device.fingerprint?.manufacturerIds ?: emptyList(),
                    manufacturerDataSummary = mfDataSummary,
                    serviceUuids = device.fingerprint?.serviceUuids ?: emptyList(),
                    category = device.classification.category,
                    companyNames = device.companyNames,
                    firstSeenInCapture = now,
                    lastSeenInCapture = now,
                    peakRssi = device.rawRssi,
                    runningRssiSum = device.rawRssi.toLong(),
                    readingCount = 1,
                    seenCount = 1,
                    targetMatchScore = matchResult?.score,
                    targetMatchSignals = matchResult?.matchedSignals ?: emptyList()
                )
            } else {
                val matchResult = matchScores[device.id]
                existing.updateFrom(device, matchResult, now)
            }
        }
    }

    private suspend fun runCompare(baseline: CaptureSession?, target: CaptureSession) {
        val results = compareCapturesUseCase(baseline, target, DefaultTargetProfile.WAYFARER_00ZS)
        val sig = _uiState.value.learnedSignature
        val matchResults = if (sig != null) {
            computeLearnedMatchesForCompare(sig, results)
        } else {
            emptyMap()
        }
        _uiState.update {
            it.copy(
                compareResults = results,
                learnedMatchResults = matchResults
            )
        }
    }

    private fun computeLearnedMatchesForCompare(
        signature: com.wearaware.app.domain.model.LearnedDeviceSignature,
        compareResults: List<com.wearaware.app.domain.model.CompareMatchResult>
    ): Map<String, com.wearaware.app.domain.model.LearnedMatchResult> {
        return compareResults.associate { result ->
            val device = result.capturedDevice
            val input = LearnedMatchInput(
                fingerprintId = device.fingerprintId,
                manufacturerIds = device.manufacturerIds,
                manufacturerDataPrefixes = extractPrefixesFromSummary(device.manufacturerDataSummary),
                serviceUuids = device.serviceUuids,
                averageRssi = device.averageRssi,
                seenCount = device.seenCount,
                visibleAtStop = device.visibleAtStop
            )
            device.fingerprintId to matchLearnedSignature(input, signature)
        }
    }
}
