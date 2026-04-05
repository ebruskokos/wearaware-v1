package com.wearaware.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.BleRepository
import com.wearaware.app.domain.repository.CaptureRepository
import com.wearaware.app.domain.usecase.CompareCapturesUseCase
import com.wearaware.app.domain.usecase.DefaultTargetProfile
import com.wearaware.app.domain.usecase.MatchTargetDeviceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val bleRepository: BleRepository,
    private val captureRepository: CaptureRepository,
    private val matchTargetDevice: MatchTargetDeviceUseCase,
    private val compareCapturesUseCase: CompareCapturesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    /** In-memory accumulator for devices observed during the active capture window. */
    private val accumulator = mutableMapOf<String, DeviceAccumulator>()
    private var captureStartedAt = 0L
    private var activeCaptureType: CaptureType? = null

    init {
        // Load any previously persisted captures from Room
        viewModelScope.launch {
            val baseline = captureRepository.getSession(CaptureType.BASELINE)
            val target = captureRepository.getSession(CaptureType.TARGET)
            _uiState.update {
                it.copy(
                    baseline = baseline,
                    baselineCaptureState = if (baseline != null) CaptureState.DONE else CaptureState.IDLE,
                    target = target,
                    targetCaptureState = if (target != null) CaptureState.DONE else CaptureState.IDLE
                )
            }
            if (target != null) runCompare(baseline, target)
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
            captureStartedAt = System.currentTimeMillis()
            activeCaptureType = type
            _uiState.update {
                if (type == CaptureType.BASELINE)
                    it.copy(baselineCaptureState = CaptureState.CAPTURING, baseline = null, liveAccumulatedCount = 0)
                else
                    it.copy(targetCaptureState = CaptureState.CAPTURING, target = null, liveAccumulatedCount = 0)
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
                    targetMatchSignals = acc.targetMatchSignals
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
                    it.copy(baselineCaptureState = CaptureState.DONE, baseline = session, liveAccumulatedCount = 0)
                else
                    it.copy(targetCaptureState = CaptureState.DONE, target = session, liveAccumulatedCount = 0)
            }

            if (newTarget != null) runCompare(newBaseline, newTarget)
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
                existing.lastSeenInCapture = now
                existing.seenCount++
                if (device.rawRssi > existing.peakRssi) existing.peakRssi = device.rawRssi
                existing.runningRssiSum += device.rawRssi
                existing.readingCount++
            }
        }
    }

    private suspend fun runCompare(baseline: CaptureSession?, target: CaptureSession) {
        val results = compareCapturesUseCase(baseline, target, DefaultTargetProfile.WAYFARER_00ZS)
        _uiState.update { it.copy(compareResults = results) }
    }

    /** Internal mutable accumulator for one device during a capture window. */
    private data class DeviceAccumulator(
        val fingerprintId: String,
        val advertisedName: String?,
        val macAddress: String?,
        val manufacturerIds: List<Int>,
        val manufacturerDataSummary: String?,
        val serviceUuids: List<String>,
        val category: DeviceCategory,
        val companyNames: List<String>,
        val firstSeenInCapture: Long,
        var lastSeenInCapture: Long,
        var peakRssi: Int,
        var runningRssiSum: Long,
        var readingCount: Int,
        var seenCount: Int,
        val targetMatchScore: Int?,
        val targetMatchSignals: List<String>
    ) {
        val averageRssi: Int get() = if (readingCount > 0) (runningRssiSum / readingCount).toInt() else peakRssi
    }
}
