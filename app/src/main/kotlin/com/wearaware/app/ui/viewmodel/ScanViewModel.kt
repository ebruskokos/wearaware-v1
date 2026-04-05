package com.wearaware.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.wearaware.app.domain.model.CandidateLifecycle
import com.wearaware.app.domain.model.DeviceTemporalState
import com.wearaware.app.domain.model.KnownMatchConfidence
import com.wearaware.app.domain.model.KnownTargetMatchInput
import com.wearaware.app.domain.model.KnownTargetMatchResult
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.PersistenceAlert
import com.wearaware.app.domain.model.ScanFilter
import com.wearaware.app.domain.model.VisibilityState
import com.wearaware.app.domain.repository.BleRepository
import com.wearaware.app.domain.repository.KnownTargetRepository
import com.wearaware.app.domain.repository.LearningSessionRepository
import com.wearaware.app.domain.usecase.*
import com.wearaware.app.domain.usecase.AdaptiveSignatureRefineUseCase
import com.wearaware.app.domain.usecase.ApplyTemporalMatchFilterUseCase
import com.wearaware.app.domain.usecase.ComputeRankedCandidatesUseCase
import com.wearaware.app.domain.usecase.MatchKnownTargetSignatureUseCase
import com.wearaware.app.domain.usecase.RefineKnownTargetFromObservationUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val matchKnownTarget: MatchKnownTargetSignatureUseCase,
    private val knownTargetRepository: KnownTargetRepository,
    private val refineKnownTarget: RefineKnownTargetFromObservationUseCase,
    private val learningSessionRepository: LearningSessionRepository,
    private val applyTemporalFilter: ApplyTemporalMatchFilterUseCase,
    private val computeRankedCandidates: ComputeRankedCandidatesUseCase,
    private val adaptiveRefine: AdaptiveSignatureRefineUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val lastAlertedAt = mutableMapOf<String, Long>()
    private val loggedDeviceIds = mutableSetOf<String>()
    /** Temporal state per device ID — reset on stopScanning(). */
    private val deviceTemporalStates = mutableMapOf<String, DeviceTemporalState>()
    /** Per-device lifecycle tracking for ranking — reset on stopScanning(). */
    private val deviceLifecycles = mutableMapOf<String, CandidateLifecycle>()
    /** Stable rank order from previous tick (device IDs, #1 first). */
    private var prevRankOrder: List<String> = emptyList()
    /** Currently locked primary target device ID. */
    private var primaryLockId: String? = null
    /** Epoch ms of last adaptive refinement save — throttle to once per ADAPTIVE_INTERVAL_MS. */
    private var lastAdaptiveRefineAt: Long = 0L

    companion object {
        private const val TAG = "WearAware.ScanVM"
        /** Minimum interval between adaptive signature refinements. */
        private const val ADAPTIVE_INTERVAL_MS = 30_000L
    }

    init {
        _uiState.update {
            it.copy(
                appVersion = aboutInfo.appVersion,
                ruleSetVersion = aboutInfo.ruleSetVersion,
                ruleSetHash = aboutInfo.ruleSetHash
            )
        }
        val sig = knownTargetRepository.load()
        if (sig != null) {
            Log.d(TAG, "Learned signature loaded: '${sig.displayName}' (fingerprintId=${sig.fingerprintId})")
            val learnedMatches = computeKnownTargetMatches(_uiState.value.devices, sig)
            _uiState.update { it.copy(knownTargetSignature = sig, learnedMatchResults = learnedMatches) }
        } else {
            Log.d(TAG, "No learned signature found in storage")
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
        deviceTemporalStates.clear()
        deviceLifecycles.clear()
        prevRankOrder = emptyList()
        primaryLockId = null
        lastAdaptiveRefineAt = 0L
        _uiState.update {
            it.copy(
                scanState = ScanState.STOPPED,
                devices = emptyList(),
                activeAlert = null,
                deviceMatchScores = emptyMap(),
                learnedMatchResults = emptyMap(),
                rankedCandidates = emptyList(),
                primaryLockDeviceId = null,
                isRefiningSignature = false,
                lastRefinementDelta = null
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
        val sig = knownTargetRepository.load()
        if (sig != null) {
            Log.d(TAG, "Learned signature reloaded: '${sig.displayName}' (fingerprintId=${sig.fingerprintId})")
        }
        val learnedMatches = computeKnownTargetMatches(_uiState.value.devices, sig)
        _uiState.update { it.copy(knownTargetSignature = sig, learnedMatchResults = learnedMatches) }
    }

    private fun computeKnownTargetMatches(
        devices: List<ObservedDevice>,
        sig: com.wearaware.app.domain.model.KnownTargetSignature?
    ): Map<String, KnownTargetMatchResult> {
        if (sig == null) {
            Log.d(TAG, "No learned signature — skipping match")
            return emptyMap()
        }
        val now = System.currentTimeMillis()
        return devices.associate { device ->
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
            val rawResult = matchKnownTarget(input, sig)

            // Apply temporal filter (decay, smoothing, variance, hysteresis)
            val temporalState = deviceTemporalStates[device.id] ?: DeviceTemporalState()
            val filterResult = applyTemporalFilter(
                rawResult = rawResult,
                state = temporalState,
                currentRssi = device.averagedRssi,
                visibilityState = device.visibilityState,
                lastSeenAtMs = device.lastSeenAt,
                nowMs = now
            )
            deviceTemporalStates[device.id] = filterResult.updatedState

            val final = filterResult.adjustedResult
            Log.d(TAG, "Device: ${device.id}  rawScore=${rawResult.score}  " +
                "confidence=${final.confidence.name}  stable=${filterResult.updatedState.stableObservationCount}")
            if (final.temporalNotes.isNotEmpty()) {
                Log.d(TAG, "  temporal: ${final.temporalNotes.joinToString(" | ")}")
            }
            when (final.confidence) {
                KnownMatchConfidence.STRONG -> Log.d(TAG, "OVERRIDE → ${sig.displayName} (device ${device.id})")
                KnownMatchConfidence.POSSIBLE -> Log.d(TAG, "OVERRIDE → Possible match (device ${device.id})")
                else -> Unit
            }
            device.id to final
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

        val learnedMatches = computeKnownTargetMatches(devices, _uiState.value.knownTargetSignature)

        // Update lifecycle data and compute ranked candidates
        val now = System.currentTimeMillis()
        updateLifecycles(devices, learnedMatches, now)
        val (ranked, newLockId) = if (_uiState.value.knownTargetSignature != null) {
            val inputs = learnedMatches.mapNotNull { (id, result) ->
                val device = devices.find { it.id == id } ?: return@mapNotNull null
                val lifecycle = deviceLifecycles[id] ?: return@mapNotNull null
                ComputeRankedCandidatesUseCase.RankingInput(device, result, lifecycle)
            }
            computeRankedCandidates(inputs, prevRankOrder, primaryLockId, now)
        } else {
            emptyList<com.wearaware.app.domain.model.RankedCandidate>() to null
        }
        prevRankOrder = ranked.map { it.device.id }
        primaryLockId = newLockId

        _uiState.update {
            it.copy(
                devices = devices,
                activeAlert = updatedAlert,
                deviceMatchScores = matchScores,
                learnedMatchResults = learnedMatches,
                rankedCandidates = ranked,
                primaryLockDeviceId = newLockId
            )
        }

        // Adaptive refinement — only when a primary lock is established
        newLockId?.let { lockedId -> maybeAdaptiveRefine(lockedId, devices) }
    }

    private fun maybeAdaptiveRefine(lockedId: String, devices: List<ObservedDevice>) {
        val sig = _uiState.value.knownTargetSignature ?: return
        val device = devices.find { it.id == lockedId } ?: return
        val temporalState = deviceTemporalStates[lockedId] ?: return

        val now = System.currentTimeMillis()
        if (now - lastAdaptiveRefineAt < ADAPTIVE_INTERVAL_MS) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRefiningSignature = true) }
            val result = withContext(Dispatchers.IO) {
                adaptiveRefine(sig, device, temporalState)
            }
            if (result.wasRefined && result.signature != null) {
                knownTargetRepository.save(result.signature)
                lastAdaptiveRefineAt = System.currentTimeMillis()
                Log.d(TAG, "Adaptive refinement saved: ${result.deltaDescription}")
                _uiState.update {
                    it.copy(
                        knownTargetSignature = result.signature,
                        isRefiningSignature = false,
                        lastRefinementDelta = result.deltaDescription
                    )
                }
            } else {
                Log.d(TAG, "Adaptive refinement skipped: ${result.skippedReason}")
                _uiState.update { it.copy(isRefiningSignature = false) }
            }
        }
    }

    private fun updateLifecycles(
        devices: List<ObservedDevice>,
        matchResults: Map<String, KnownTargetMatchResult>,
        nowMs: Long
    ) {
        matchResults.forEach { (id, result) ->
            if (result.score <= 0) return@forEach
            val device = devices.find { it.id == id } ?: return@forEach
            val existing = deviceLifecycles[id]
            val isStrong = result.confidence == KnownMatchConfidence.STRONG
            val strongSince = when {
                isStrong && existing?.strongSince != null -> existing.strongSince
                isStrong -> nowMs
                else -> null
            }
            deviceLifecycles[id] = CandidateLifecycle(
                discoveredAt = existing?.discoveredAt ?: nowMs,
                lastSeenAt = device.lastSeenAt,
                peakScore = maxOf(existing?.peakScore ?: 0, result.score),
                totalSeenCount = (existing?.totalSeenCount ?: 0) + 1,
                strongSince = strongSince
            )
        }
    }

    /**
     * Merges the live ObservedDevice matching [deviceId] into the existing learned signature.
     * No-op if no signature exists or device is not in the current scan.
     */
    fun refineFromObservation(deviceId: String) {
        val sig = _uiState.value.knownTargetSignature ?: return
        val device = _uiState.value.devices.find { it.id == deviceId } ?: return
        viewModelScope.launch {
            val refined = withContext(Dispatchers.IO) {
                refineKnownTarget(sig, device, visibleAtStop = device.visibilityState == VisibilityState.DETECTED_NOW)
            }
            Log.d(TAG, "Signature refined from live device: learnCount=${refined.learnCount}")
            reloadLearnedSignature()
        }
    }

    /**
     * Serialises the learned signature + latest session events to a pretty-printed JSON string.
     * Returns null if no signature exists. Caller should dispatch to IO before calling.
     */
    suspend fun buildExportJson(): String? {
        val sig = _uiState.value.knownTargetSignature ?: return null
        val sessions = learningSessionRepository.getAllSessions()
        val latestSession = sessions.maxByOrNull { it.startedAt }
        val events = if (latestSession != null)
            learningSessionRepository.getEventsForSession(latestSession.sessionId)
        else emptyList()
        val export = mapOf(
            "learnedSignature" to sig,
            "sessionCount" to sessions.size,
            "latestSession" to latestSession,
            "latestSessionEvents" to events
        )
        return GsonBuilder().setPrettyPrinting().create().toJson(export)
    }

    private fun deviceIsSignalLost(devices: List<ObservedDevice>, deviceId: String): Boolean =
        devices.find { it.id == deviceId }?.visibilityState == VisibilityState.SIGNAL_LOST

    override fun onCleared() {
        super.onCleared()
        bleRepository.stopScanning()
    }

}
