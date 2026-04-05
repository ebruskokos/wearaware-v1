package com.wearaware.app.ui.viewmodel

import android.app.Activity
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.wearaware.app.data.ble.BleGattManager
import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.BleRepository
import com.wearaware.app.domain.repository.KnownTargetRepository
import com.wearaware.app.domain.repository.LearningSessionRepository
import com.wearaware.app.domain.usecase.BuildKnownTargetSignatureUseCase
import com.wearaware.app.domain.usecase.LogLearningEventUseCase
import com.wearaware.app.domain.usecase.MatchKnownTargetSignatureUseCase
import com.wearaware.app.domain.usecase.extractPrefixesFromFingerprintMap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
    private val bleRepository: BleRepository,
    private val matchKnownTarget: MatchKnownTargetSignatureUseCase
) : ViewModel() {

    private val TAG = "WearAware.PairLearnVM"
    private var trainingJob: Job? = null

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
                    val event = logLearningEvent(sessionId, LearningEventType.CDM_FAILED, "User cancelled device picker")
                    _uiState.update { it.copy(recentEvents = it.recentEvents + event) }
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
        val sessionId = _uiState.value.sessionId ?: run {
            _uiState.update { it.copy(flowState = PairingFlowState.FAILED, errorMessage = "Session not started") }
            return
        }
        onCdmAssociated(address, sessionId)
    }

    /**
     * Exposed for testing. Starts GATT connect + learn flow for a known device address.
     */
    fun onCdmAssociated(deviceAddress: String, sessionId: String) {
        viewModelScope.launch {
            try {
                val cdmEvent = logLearningEvent(sessionId, LearningEventType.CDM_ASSOCIATED, "Device selected: $deviceAddress")
                _uiState.update { it.copy(recentEvents = it.recentEvents + cdmEvent) }
                learningSessionRepository.updateDeviceAddress(sessionId, deviceAddress)

                _uiState.update { it.copy(flowState = PairingFlowState.GATT_CONNECTING, statusMessage = "Connecting to device...") }
                val gattConnectingEvent = logLearningEvent(sessionId, LearningEventType.GATT_CONNECTING, "Initiating GATT connection")
                _uiState.update { it.copy(recentEvents = it.recentEvents + gattConnectingEvent) }

                val gattResult = bleGattManager.connectAndDiscover(deviceAddress)

                _uiState.update { it.copy(flowState = PairingFlowState.GATT_DISCOVERING, statusMessage = "Discovering services...") }
                val gattDiscoveredEvent = logLearningEvent(sessionId, LearningEventType.GATT_SERVICES_DISCOVERED,
                    "Discovered ${gattResult.serviceUuids.size} GATT services")
                _uiState.update { it.copy(recentEvents = it.recentEvents + gattDiscoveredEvent) }
                val gattDisconnectedEvent = logLearningEvent(sessionId, LearningEventType.GATT_DISCONNECTED, "GATT disconnected after discovery")
                _uiState.update { it.copy(recentEvents = it.recentEvents + gattDisconnectedEvent) }

                _uiState.update { it.copy(flowState = PairingFlowState.LEARNING, statusMessage = "Building glasses profile...") }

                // Find the device in current BLE scan results by MAC address
                val observedDevice = bleRepository.observedDevices.value
                    .firstOrNull { it.macAddress == deviceAddress }

                val signature = buildKnownTargetSignature(observedDevice, gattResult)
                knownTargetRepository.save(signature)
                learningSessionRepository.updateFingerprintId(sessionId, signature.fingerprintId)

                val signatureEvent = logLearningEvent(sessionId, LearningEventType.SIGNATURE_BUILT, "Signature saved: ${signature.fingerprintId}")
                _uiState.update { it.copy(recentEvents = it.recentEvents + signatureEvent) }
                val completedEvent = logLearningEvent(sessionId, LearningEventType.SESSION_COMPLETED, "Learning session completed")
                _uiState.update { it.copy(recentEvents = it.recentEvents + completedEvent) }
                learningSessionRepository.updateStatus(sessionId, LearningSessionStatus.COMPLETED)

                _uiState.update {
                    it.copy(
                        flowState = PairingFlowState.COMPLETED,
                        statusMessage = "Glasses profile saved!",
                        existingSignature = signature
                    )
                }
            } catch (e: Exception) {
                val failedEvent = logLearningEvent(sessionId, LearningEventType.GATT_FAILED, "Error: ${e.message}")
                _uiState.update { it.copy(recentEvents = it.recentEvents + failedEvent) }
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

    /**
     * Builds a JSON string containing the current learned signature and the events from
     * the most recent completed session. Returns null if no signature exists.
     */
    suspend fun buildExportJson(): String? {
        val sig = _uiState.value.existingSignature ?: return null
        val sessions = learningSessionRepository.getAllSessions()
        val latestSession = sessions.maxByOrNull { it.startedAt }
        val events = if (latestSession != null) {
            learningSessionRepository.getEventsForSession(latestSession.sessionId)
        } else emptyList()

        val export = mapOf(
            "learnedSignature" to sig,
            "sessionCount" to sessions.size,
            "latestSession" to latestSession,
            "latestSessionEvents" to events
        )
        val gson = GsonBuilder().setPrettyPrinting().create()
        return gson.toJson(export)
    }

    /**
     * Starts a training session that continuously logs BLE signal observations for
     * the learned device. Runs until [stopTraining] is called.
     * Logs BLE_SIGNAL_OBSERVED events every 5 seconds while the device is visible.
     */
    fun startTraining() {
        val sig = _uiState.value.existingSignature ?: run {
            Log.d(TAG, "startTraining called but no signature — aborting")
            return
        }
        val sessionId = UUID.randomUUID().toString()
        trainingJob?.cancel()
        trainingJob = viewModelScope.launch {
            learningSessionRepository.createSession(sessionId, System.currentTimeMillis())
            logLearningEvent(sessionId, LearningEventType.SESSION_STARTED, "Training session started")
            _uiState.update { it.copy(trainingSessionId = sessionId, isTraining = true) }
            Log.d(TAG, "Training session started: $sessionId")

            // Observe live BLE devices and log signal for the best matching device every 5s
            bleRepository.observedDevices
                .collect { devices ->
                    val matchResult = devices.mapNotNull { device ->
                        val input = KnownTargetMatchInput(
                            fingerprintId = device.id,
                            manufacturerIds = device.fingerprint?.manufacturerIds ?: emptyList(),
                            manufacturerDataPrefixes = extractPrefixesFromFingerprintMap(
                                device.fingerprint?.manufacturerDataHex
                            ),
                            serviceUuids = device.fingerprint?.serviceUuids ?: emptyList(),
                            gattServiceUuids = emptyList(),
                            averageRssi = device.averagedRssi,
                            seenCount = device.seenCount,
                            visibleAtStop = false,
                            connectable = device.rawBleData?.isConnectable ?: false
                        )
                        val result = matchKnownTarget(input, sig)
                        if (result.score > 0) device to result else null
                    }.maxByOrNull { it.second.score }

                    if (matchResult != null) {
                        val (device, result) = matchResult
                        val mfIds = device.fingerprint?.manufacturerIds
                            ?.joinToString(",") { "0x${it.toString(16).uppercase().padStart(4, '0')}" } ?: "none"
                        val prefixes = extractPrefixesFromFingerprintMap(device.fingerprint?.manufacturerDataHex)
                            .joinToString(",").ifEmpty { "none" }
                        val svcUuids = device.fingerprint?.serviceUuids?.joinToString(",")?.ifEmpty { "none" } ?: "none"
                        val detail = buildString {
                            append("rssi=${device.averagedRssi}dBm ")
                            append("rawRssi=${device.rawRssi}dBm ")
                            append("seenCount=${device.seenCount} ")
                            append("visibility=${device.visibilityState.name} ")
                            append("connectable=${device.rawBleData?.isConnectable} ")
                            append("manufacturers=[$mfIds] ")
                            append("prefixes=[$prefixes] ")
                            append("serviceUuids=[$svcUuids] ")
                            append("confidence=${result.confidence.name} ")
                            append("score=${result.score} ")
                            append("signals=${result.matchedSignals.size}")
                        }
                        logLearningEvent(sessionId, LearningEventType.BLE_SIGNAL_OBSERVED, detail)
                        Log.d("WearAware.Training", "Training observation: $detail")
                        result.matchedSignals.forEach { signal ->
                            Log.d("WearAware.Training", "  signal: $signal")
                        }
                    }
                    delay(5_000)
                }
        }
    }

    fun stopTraining() {
        trainingJob?.cancel()
        trainingJob = null
        val sessionId = _uiState.value.trainingSessionId
        if (sessionId != null) {
            viewModelScope.launch {
                logLearningEvent(sessionId, LearningEventType.SESSION_COMPLETED, "Training session stopped by user")
                learningSessionRepository.updateStatus(sessionId, LearningSessionStatus.COMPLETED)
                Log.d(TAG, "Training session stopped: $sessionId")
            }
        }
        _uiState.update { it.copy(isTraining = false, trainingSessionId = null) }
    }

    fun clearProfile() {
        stopTraining()
        knownTargetRepository.clear()
        _uiState.update { it.copy(existingSignature = null) }
    }

    fun resetToIdle() {
        _uiState.update { it.copy(flowState = PairingFlowState.IDLE, errorMessage = null, statusMessage = "") }
    }
}
