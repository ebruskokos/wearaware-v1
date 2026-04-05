package com.wearaware.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wearaware.app.domain.model.LearnedMatchInput
import com.wearaware.app.domain.model.LearnedMatchResult
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.PersistenceAlert
import com.wearaware.app.domain.model.ScanFilter
import com.wearaware.app.domain.model.VisibilityState
import com.wearaware.app.domain.repository.BleRepository
import com.wearaware.app.domain.repository.LearnedSignatureRepository
import com.wearaware.app.domain.usecase.*
import com.wearaware.app.domain.usecase.MatchLearnedSignatureUseCase
import com.wearaware.app.domain.usecase.extractPrefixesFromFingerprintMap
import com.wearaware.app.util.AboutInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val observeScannedDevices: ObserveScannedDevicesUseCase,
    private val evaluatePersistence: EvaluatePersistenceUseCase,
    private val logScanEvent: LogScanEventUseCase,
    private val matchTargetDevice: MatchTargetDeviceUseCase,
    private val bleRepository: BleRepository,
    aboutInfo: AboutInfo,
    private val matchLearnedSignature: MatchLearnedSignatureUseCase,
    private val learnedSignatureRepository: LearnedSignatureRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val lastAlertedAt = mutableMapOf<String, Long>()
    private val loggedDeviceIds = mutableSetOf<String>()

    init {
        _uiState.update {
            it.copy(
                appVersion = aboutInfo.appVersion,
                ruleSetVersion = aboutInfo.ruleSetVersion,
                ruleSetHash = aboutInfo.ruleSetHash
            )
        }
        val sig = learnedSignatureRepository.load()
        if (sig != null) {
            _uiState.update { it.copy(learnedSignature = sig) }
        }
    }

    fun startScanning() {
        if (!bleRepository.isBleAvailable) {
            _uiState.update { it.copy(scanState = ScanState.BLUETOOTH_UNAVAILABLE) }
            return
        }
        bleRepository.startScanning()
        _uiState.update { it.copy(scanState = ScanState.SCANNING) }
        viewModelScope.launch {
            observeScannedDevices().collect { devices -> processDeviceUpdate(devices) }
        }
    }

    fun stopScanning() {
        bleRepository.stopScanning()
        lastAlertedAt.clear()
        loggedDeviceIds.clear()
        _uiState.update {
            it.copy(
                scanState = ScanState.STOPPED,
                devices = emptyList(),
                activeAlert = null,
                deviceMatchScores = emptyMap(),
                learnedMatchResults = emptyMap()
            )
        }
    }

    fun setPermissionsRequired() {
        if (_uiState.value.scanState != ScanState.SCANNING) {
            _uiState.update { it.copy(scanState = ScanState.PERMISSIONS_REQUIRED) }
        }
    }

    fun dismissAlert() {
        _uiState.update { it.copy(activeAlert = null) }
    }

    fun toggleFocusMode() {
        _uiState.update { it.copy(focusMode = !it.focusMode) }
    }

    fun toggleDebugMode() {
        _uiState.update { it.copy(debugMode = !it.debugMode) }
    }

    fun setFilter(filter: ScanFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    fun getDeviceById(deviceId: String): ObservedDevice? =
        _uiState.value.devices.find { it.id == deviceId }

    /** Called from ScanScreen on composition to pick up signatures saved via CaptureScreen. */
    fun reloadLearnedSignature() {
        val sig = learnedSignatureRepository.load()
        val learnedMatches = computeLearnedMatches(_uiState.value.devices, sig)
        _uiState.update { it.copy(learnedSignature = sig, learnedMatchResults = learnedMatches) }
    }

    private fun computeLearnedMatches(
        devices: List<ObservedDevice>,
        sig: com.wearaware.app.domain.model.LearnedDeviceSignature?
    ): Map<String, LearnedMatchResult> {
        if (sig == null) return emptyMap()
        return devices.associate { device ->
            val input = LearnedMatchInput(
                fingerprintId = device.id,
                manufacturerIds = device.fingerprint?.manufacturerIds ?: emptyList(),
                manufacturerDataPrefixes = extractPrefixesFromFingerprintMap(
                    device.fingerprint?.manufacturerDataHex
                ),
                serviceUuids = device.fingerprint?.serviceUuids ?: emptyList(),
                averageRssi = device.averagedRssi,
                seenCount = device.seenCount,
                visibleAtStop = false  // live scan — visibleAtStop is a capture-only concept
            )
            device.id to matchLearnedSignature(input, sig)
        }
    }

    private fun processDeviceUpdate(devices: List<ObservedDevice>) {
        // 1. Compute target match scores
        val matchScores = matchTargetDevice(devices, DefaultTargetProfile.WAYFARER_00ZS)

        // 2. Log new devices with match score
        devices
            .filter { it.id !in loggedDeviceIds && it.visibilityState == VisibilityState.DETECTED_NOW }
            .forEach { device ->
                loggedDeviceIds.add(device.id)
                viewModelScope.launch { logScanEvent(device, matchScores[device.id]) }
            }

        // 3. Evaluate persistence alerts
        val newAlerts = devices.mapNotNull { device ->
            evaluatePersistence(device, lastAlertedAt)?.also { alert ->
                lastAlertedAt["${device.id}:${alert.alertType.name}"] = alert.triggeredAt
            }
        }

        val currentAlert = _uiState.value.activeAlert
        val updatedAlert: PersistenceAlert? = when {
            currentAlert != null && deviceIsSignalLost(devices, currentAlert.deviceId) -> null
            newAlerts.isNotEmpty() -> newAlerts.maxByOrNull { it.triggeredAt }
            else -> currentAlert
        }

        val learnedMatches = computeLearnedMatches(devices, _uiState.value.learnedSignature)

        _uiState.update {
            it.copy(
                devices = devices,
                activeAlert = updatedAlert,
                deviceMatchScores = matchScores,
                learnedMatchResults = learnedMatches
            )
        }
    }

    private fun deviceIsSignalLost(devices: List<ObservedDevice>, deviceId: String): Boolean =
        devices.find { it.id == deviceId }?.visibilityState == VisibilityState.SIGNAL_LOST

    override fun onCleared() {
        super.onCleared()
        bleRepository.stopScanning()
    }
}
