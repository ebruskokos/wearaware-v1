package com.wearaware.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.PersistenceAlert
import com.wearaware.app.domain.model.VisibilityState
import com.wearaware.app.domain.repository.BleRepository
import com.wearaware.app.domain.usecase.*
import com.wearaware.app.util.AboutInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PURPOSE: Orchestrates the BLE scanning session for ScanScreen.
 *   Collects device updates, evaluates persistence alerts, logs events,
 *   and exposes a single ScanUiState via StateFlow.
 * LIMITATIONS: loggedDeviceIds prevents duplicate logging for the same device within
 *   a session but does not persist across sessions.
 * NOTES: ViewModels do not contain BLE parsing, RSSI math, or rule evaluation.
 *   All logic is delegated to use cases or the repository.
 *   ScanViewModel manages alert dismissal UI state but does NOT reset cooldown on dismiss
 *   (by design — see SafeWording design notes).
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val observeScannedDevices: ObserveScannedDevicesUseCase,
    private val evaluatePersistence: EvaluatePersistenceUseCase,
    private val logScanEvent: LogScanEventUseCase,
    private val bleRepository: BleRepository,
    aboutInfo: AboutInfo
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    /** Tracks last alert trigger time per (deviceId:alertType) key for cooldown management. */
    private val lastAlertedAt = mutableMapOf<String, Long>()
    /** Tracks which device IDs have been logged this session to avoid duplicate log entries. */
    private val loggedDeviceIds = mutableSetOf<String>()

    init {
        _uiState.update {
            it.copy(
                appVersion = aboutInfo.appVersion,
                ruleSetVersion = aboutInfo.ruleSetVersion,
                ruleSetHash = aboutInfo.ruleSetHash
            )
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
            observeScannedDevices().collect { devices ->
                processDeviceUpdate(devices)
            }
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
                activeAlert = null
            )
        }
    }

    fun setPermissionsRequired() {
        if (_uiState.value.scanState != ScanState.SCANNING) {
            _uiState.update { it.copy(scanState = ScanState.PERMISSIONS_REQUIRED) }
        }
    }

    /**
     * Hides the active alert banner without resetting the cooldown.
     * The device remains in the list; the cooldown prevents re-alerting too soon.
     */
    fun dismissAlert() {
        _uiState.update { it.copy(activeAlert = null) }
    }

    fun getDeviceById(deviceId: String): ObservedDevice? =
        _uiState.value.devices.find { it.id == deviceId }

    private fun processDeviceUpdate(devices: List<ObservedDevice>) {
        // Log each newly detected device once per session
        devices
            .filter { it.id !in loggedDeviceIds && it.visibilityState == VisibilityState.DETECTED_NOW }
            .forEach { device ->
                loggedDeviceIds.add(device.id)
                viewModelScope.launch { logScanEvent(device) }
            }

        // Evaluate persistence alerts for all DETECTED_NOW devices
        val newAlerts = devices.mapNotNull { device ->
            evaluatePersistence(device, lastAlertedAt)?.also { alert ->
                val key = "${device.id}:${alert.alertType.name}"
                lastAlertedAt[key] = alert.triggeredAt
            }
        }

        // Determine the active alert:
        // - Clear current alert if its device has gone SIGNAL_LOST
        // - Replace with newest alert if multiple triggered this cycle
        val currentAlert = _uiState.value.activeAlert
        val updatedAlert: PersistenceAlert? = when {
            currentAlert != null && deviceIsSignalLost(devices, currentAlert.deviceId) -> null
            newAlerts.isNotEmpty() -> newAlerts.maxByOrNull { it.triggeredAt }
            else -> currentAlert
        }

        _uiState.update { it.copy(devices = devices, activeAlert = updatedAlert) }
    }

    private fun deviceIsSignalLost(devices: List<ObservedDevice>, deviceId: String): Boolean =
        devices.find { it.id == deviceId }?.visibilityState == VisibilityState.SIGNAL_LOST

    override fun onCleared() {
        super.onCleared()
        bleRepository.stopScanning()
    }
}
