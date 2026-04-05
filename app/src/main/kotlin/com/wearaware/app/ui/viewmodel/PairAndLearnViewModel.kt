package com.wearaware.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.app.Activity
import androidx.activity.result.ActivityResult
import com.wearaware.app.data.ble.BleGattManager
import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.BleRepository
import com.wearaware.app.domain.repository.KnownTargetRepository
import com.wearaware.app.domain.repository.LearningSessionRepository
import com.wearaware.app.domain.usecase.BuildKnownTargetSignatureUseCase
import com.wearaware.app.domain.usecase.LogLearningEventUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PairAndLearnViewModel @Inject constructor(
    private val knownTargetRepository: KnownTargetRepository,
    private val learningSessionRepository: LearningSessionRepository,
    private val logLearningEvent: LogLearningEventUseCase,
    private val buildKnownTargetSignature: BuildKnownTargetSignatureUseCase,
    private val bleGattManager: BleGattManager,
    private val bleRepository: BleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairAndLearnUiState())
    val uiState: StateFlow<PairAndLearnUiState> = _uiState.asStateFlow()

    private val _effects = Channel<PairAndLearnEffect>(Channel.BUFFERED)
    val effects: Flow<PairAndLearnEffect> = _effects.receiveAsFlow()

    init {
        val sig = knownTargetRepository.load()
        _uiState.update { it.copy(existingSignature = sig) }
    }

    /**
     * Called from Screen when CDM device picker returns. Handles both cancel and success.
     * On success, calls onCdmAssociated with the selected device's MAC address.
     */
    fun onCdmResult(result: ActivityResult) {
        if (result.resultCode != Activity.RESULT_OK) {
            viewModelScope.launch {
                val sessionId = _uiState.value.sessionId
                if (sessionId != null) {
                    logLearningEvent(sessionId, LearningEventType.CDM_FAILED, "User cancelled device picker")
                    learningSessionRepository.updateStatus(sessionId, LearningSessionStatus.ABANDONED)
                }
                _uiState.update { it.copy(flowState = PairingFlowState.FAILED, errorMessage = "Pairing cancelled") }
            }
            return
        }
        val device = result.data?.getParcelableExtra<android.bluetooth.BluetoothDevice>(
            android.companion.CompanionDeviceManager.EXTRA_DEVICE
        )
        val address = device?.address
        if (address == null) {
            _uiState.update { it.copy(flowState = PairingFlowState.FAILED, errorMessage = "No device address received") }
            return
        }
        val sessionId = _uiState.value.sessionId ?: return
        onCdmAssociated(address, sessionId)
    }

    /**
     * Exposed for testing. Starts GATT connect + learn flow for a known device address.
     */
    fun onCdmAssociated(deviceAddress: String, sessionId: String) {
        viewModelScope.launch {
            try {
                logLearningEvent(sessionId, LearningEventType.CDM_ASSOCIATED, "Device selected: $deviceAddress")
                learningSessionRepository.updateDeviceAddress(sessionId, deviceAddress)

                _uiState.update { it.copy(flowState = PairingFlowState.GATT_CONNECTING, statusMessage = "Connecting to device...") }
                logLearningEvent(sessionId, LearningEventType.GATT_CONNECTING, "Initiating GATT connection")

                _uiState.update { it.copy(flowState = PairingFlowState.GATT_DISCOVERING, statusMessage = "Discovering services...") }
                val gattResult = bleGattManager.connectAndDiscover(deviceAddress)

                logLearningEvent(sessionId, LearningEventType.GATT_SERVICES_DISCOVERED,
                    "Discovered ${gattResult.serviceUuids.size} GATT services")
                logLearningEvent(sessionId, LearningEventType.GATT_DISCONNECTED, "GATT disconnected after discovery")

                _uiState.update { it.copy(flowState = PairingFlowState.LEARNING, statusMessage = "Building glasses profile...") }

                // Find the device in current BLE scan results by MAC address
                val observedDevice = bleRepository.observedDevices.value
                    .firstOrNull { it.macAddress == deviceAddress }

                val signature = buildKnownTargetSignature(observedDevice, gattResult)
                knownTargetRepository.save(signature)

                logLearningEvent(sessionId, LearningEventType.SIGNATURE_BUILT, "Signature saved: ${signature.fingerprintId}")
                logLearningEvent(sessionId, LearningEventType.SESSION_COMPLETED, "Learning session completed")
                learningSessionRepository.updateStatus(sessionId, LearningSessionStatus.COMPLETED)

                _uiState.update {
                    it.copy(
                        flowState = PairingFlowState.COMPLETED,
                        statusMessage = "Glasses profile saved!",
                        existingSignature = signature
                    )
                }
            } catch (e: Exception) {
                logLearningEvent(sessionId, LearningEventType.GATT_FAILED, "Error: ${e.message}")
                learningSessionRepository.updateStatus(sessionId, LearningSessionStatus.FAILED)
                _uiState.update {
                    it.copy(flowState = PairingFlowState.FAILED, errorMessage = "Connection failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Initiates the CDM association flow. Emits LaunchCdmPicker effect when the device
     * chooser IntentSender is ready.
     */
    fun startPairing(companionDeviceManager: android.companion.CompanionDeviceManager) {
        val sessionId = UUID.randomUUID().toString()
        viewModelScope.launch {
            learningSessionRepository.createSession(sessionId, System.currentTimeMillis())
            logLearningEvent(sessionId, LearningEventType.SESSION_STARTED, "Pairing session initiated")
            _uiState.update { it.copy(flowState = PairingFlowState.CDM_SCANNING, statusMessage = "Scanning for BLE devices...", sessionId = sessionId) }
            logLearningEvent(sessionId, LearningEventType.CDM_SCANNING, "CDM scanning started")

            val request = android.companion.AssociationRequest.Builder()
                .addDeviceFilter(
                    android.companion.BluetoothLeDeviceFilter.Builder().build()
                )
                .setSingleDevice(false)
                .build()

            companionDeviceManager.associate(request, object : android.companion.CompanionDeviceManager.Callback() {
                override fun onDeviceFound(chooserLauncher: android.content.IntentSender) {
                    viewModelScope.launch {
                        _uiState.update { it.copy(flowState = PairingFlowState.CDM_WAITING_SELECTION, statusMessage = "Select your glasses from the list") }
                        logLearningEvent(sessionId, LearningEventType.CDM_WAITING_SELECTION, "Device picker displayed")
                        _effects.send(PairAndLearnEffect.LaunchCdmPicker(chooserLauncher))
                    }
                }
                override fun onFailure(error: CharSequence?) {
                    viewModelScope.launch {
                        logLearningEvent(sessionId, LearningEventType.CDM_FAILED, "CDM failed: $error")
                        learningSessionRepository.updateStatus(sessionId, LearningSessionStatus.FAILED)
                        _uiState.update { it.copy(flowState = PairingFlowState.FAILED, errorMessage = "Scan failed: $error") }
                    }
                }
            }, null)
        }
    }

    fun clearProfile() {
        knownTargetRepository.clear()
        _uiState.update { it.copy(existingSignature = null) }
    }

    fun resetToIdle() {
        _uiState.update { it.copy(flowState = PairingFlowState.IDLE, errorMessage = null, statusMessage = "") }
    }
}
