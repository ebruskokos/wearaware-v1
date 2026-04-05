package com.wearaware.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.wearaware.app.domain.model.AnomalyType
import com.wearaware.app.domain.model.CandidateLifecycle
import com.wearaware.app.domain.model.DeviceTemporalState
import com.wearaware.app.domain.model.KnownMatchConfidence
import com.wearaware.app.domain.model.KnownTargetMatchInput
import com.wearaware.app.domain.model.KnownTargetMatchResult
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.PersistenceAlert
import com.wearaware.app.domain.model.ScanAnomalyEvent
import com.wearaware.app.domain.model.ScanFilter
import com.wearaware.app.domain.model.SessionReport
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
import kotlinx.coroutines.Job
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
    /**
     * False-positive guard: counts per-device ticks where score >= POSSIBLE threshold but the
     * device is NOT the primary lock. Devices reaching [NON_TARGET_THRESHOLD] ticks are marked
     * as "persistent non-targets" and receive a [NON_TARGET_PENALTY] score penalty each tick.
     */
    private val nonTargetTickCounts = mutableMapOf<String, Int>()
    /** Coroutine that collects from observeScannedDevices — cancelled on stop to prevent leaks. */
    private var scanCollectJob: Job? = null
    /** Tracks whether the adaptive scan mode is currently set to balanced (locked state). */
    private var scanModeBalanced: Boolean = false

    // --- Session tracking (for SessionReport) ---
    private var sessionStartMs: Long = 0L
    private var lockStartMs: Long = 0L
    private var totalLockedMs: Long = 0L
    private var lockSwitchCount: Int = 0
    private var maxCompetingScore: Int = 0
    private val candidatesSeen = mutableSetOf<String>()
    private var prevLockId: String? = null

    companion object {
        private const val TAG = "WearAware.ScanVM"
        /** Minimum interval between adaptive signature refinements. */
        private const val ADAPTIVE_INTERVAL_MS = 30_000L
        /** Ticks a high-scoring non-lock device must accumulate before being penalised. */
        private const val NON_TARGET_THRESHOLD = 20
        /** Score penalty applied per [NON_TARGET_PENALTY_INTERVAL] ticks beyond [NON_TARGET_THRESHOLD]. */
        private const val NON_TARGET_PENALTY = 1
        /** Apply one additional penalty point per this many ticks over threshold (max total -3). */
        private const val NON_TARGET_PENALTY_INTERVAL = 10
    }

    init {
        _uiState.update {
            it.copy(
                appVersion = aboutInfo.appVersion,
                ruleSetVersion = aboutInfo.ruleSetVersion,
                ruleSetHash = aboutInfo.ruleSetHash
            )
        }
        val sig = runCatching { knownTargetRepository.load() }.getOrNull()
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
        sessionStartMs = System.currentTimeMillis()
        totalLockedMs = 0L
        lockStartMs = 0L
        lockSwitchCount = 0
        maxCompetingScore = 0
        candidatesSeen.clear()
        prevLockId = null
        _uiState.update { it.copy(scanState = ScanState.SCANNING, anomalyLog = emptyList()) }
        scanCollectJob?.cancel()
        // Run processing on Default to keep scoring/matching off the main thread
        scanCollectJob = viewModelScope.launch(Dispatchers.Default) {
            observeScannedDevices().collect { devices -> processDeviceUpdate(devices) }
        }
    }

    fun stopScanning() {
        scanCollectJob?.cancel()
        scanCollectJob = null
        bleRepository.stopScanning()

        // Finalise session report before clearing state
        val now = System.currentTimeMillis()
        if (lockStartMs > 0L) totalLockedMs += (now - lockStartMs)
        val report = if (sessionStartMs > 0L) SessionReport(
            sessionDurationMs = now - sessionStartMs,
            totalLockedMs = totalLockedMs,
            totalCandidatesSeen = candidatesSeen.size,
            maxCompetingScore = maxCompetingScore,
            lockedDeviceId = primaryLockId,
            lockSwitchCount = lockSwitchCount,
            endedAt = now
        ) else null

        lastAlertedAt.clear()
        loggedDeviceIds.clear()
        deviceTemporalStates.clear()
        deviceLifecycles.clear()
        prevRankOrder = emptyList()
        primaryLockId = null
        lastAdaptiveRefineAt = 0L
        nonTargetTickCounts.clear()
        scanModeBalanced = false
        sessionStartMs = 0L
        lockStartMs = 0L
        totalLockedMs = 0L
        lockSwitchCount = 0
        maxCompetingScore = 0
        candidatesSeen.clear()
        prevLockId = null

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
                lastRefinementDelta = null,
                persistentNonTargetIds = emptySet(),
                anomalyLog = emptyList(),
                relearnModeActive = false,
                lastSessionReport = report ?: it.lastSessionReport
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

    fun toggleDebugOverlay() {
        _uiState.update { it.copy(debugOverlayVisible = !it.debugOverlayVisible) }
    }

    fun toggleRelearnMode() {
        _uiState.update { it.copy(relearnModeActive = !it.relearnModeActive) }
    }

    /** Wipes the learned signature and full history. Cannot be undone. */
    fun resetLearning() {
        runCatching { knownTargetRepository.clear() }
        _uiState.update {
            it.copy(
                knownTargetSignature = null,
                learnedMatchResults = emptyMap(),
                rankedCandidates = emptyList(),
                primaryLockDeviceId = null,
                lastRefinementDelta = null,
                relearnModeActive = false
            )
        }
        Log.d(TAG, "Learning reset — signature and history cleared")
    }

    fun getDeviceById(deviceId: String): ObservedDevice? =
        _uiState.value.devices.find { it.id == deviceId }

    /** Called from ScanScreen on composition to pick up signatures saved via CaptureScreen. */
    fun reloadLearnedSignature() {
        val sig = runCatching { knownTargetRepository.load() }.getOrNull()
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

        // 4. Compute known-target matches then apply false-positive penalty
        val rawLearnedMatches = computeKnownTargetMatches(devices, _uiState.value.knownTargetSignature)
        updateNonTargetCounts(rawLearnedMatches)
        val persistentNonTargetIds = nonTargetTickCounts
            .filter { (_, count) -> count >= NON_TARGET_THRESHOLD }
            .keys.toSet()
        val learnedMatches = applyNonTargetPenalties(rawLearnedMatches, persistentNonTargetIds)

        // 5. Update lifecycle data and compute ranked candidates
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

        // 6. Session tracking + anomaly logging
        val tickNow = System.currentTimeMillis()
        val newAnomalies = mutableListOf<ScanAnomalyEvent>()

        // Track all candidates seen this session
        learnedMatches.forEach { (id, result) -> if (result.score > 0) candidatesSeen.add(id) }

        // Track max competing score (non-lock candidates)
        learnedMatches.forEach { (id, result) ->
            if (id != newLockId && result.score > maxCompetingScore) maxCompetingScore = result.score
        }

        // Lock switch detection
        if (newLockId != prevLockId) {
            if (prevLockId != null && lockStartMs > 0L) {
                totalLockedMs += (tickNow - lockStartMs)
            }
            if (newLockId != null) {
                lockStartMs = tickNow
                if (prevLockId != null) {
                    // Actual switch — lock moved from one device to another
                    lockSwitchCount++
                    val fromResult = learnedMatches[prevLockId]
                    val toResult = learnedMatches[newLockId]
                    val detail = "Lock switched: $prevLockId (score=${fromResult?.score}) → $newLockId (score=${toResult?.score})"
                    Log.w(TAG, "ANOMALY lock-switch: $detail")
                    newAnomalies += ScanAnomalyEvent(tickNow, AnomalyType.LOCK_SWITCH, detail)
                }
            } else {
                lockStartMs = 0L
            }
            prevLockId = newLockId
        }

        // Strong false positive: non-lock device reached STRONG confidence
        learnedMatches.forEach { (id, result) ->
            if (id != newLockId && result.confidence == KnownMatchConfidence.STRONG) {
                val breakdown = result.scoreBreakdown.joinToString { "${it.category.name}:${it.points}" }
                val detail = "STRONG non-lock: device=$id score=${result.score} breakdown=[$breakdown]"
                Log.w(TAG, "ANOMALY false-positive: $detail")
                newAnomalies += ScanAnomalyEvent(tickNow, AnomalyType.STRONG_FALSE_POSITIVE, detail)
            }
        }

        // 7. Battery: switch scan mode based on lock stability (only when mode needs to change)
        val wantBalanced = newLockId != null
        if (wantBalanced != scanModeBalanced) {
            scanModeBalanced = wantBalanced
            bleRepository.setAdaptiveScanMode(wantBalanced)
        }

        // 8. Prune orphaned per-device state for devices no longer in the scan list
        val activeIds = devices.map { it.id }.toSet()
        deviceTemporalStates.keys.retainAll(activeIds)
        deviceLifecycles.keys.retainAll(activeIds)
        nonTargetTickCounts.keys.retainAll(activeIds)

        _uiState.update {
            it.copy(
                devices = devices,
                activeAlert = updatedAlert,
                deviceMatchScores = matchScores,
                learnedMatchResults = learnedMatches,
                rankedCandidates = ranked,
                primaryLockDeviceId = newLockId,
                persistentNonTargetIds = persistentNonTargetIds,
                anomalyLog = if (newAnomalies.isEmpty()) it.anomalyLog
                             else (it.anomalyLog + newAnomalies).takeLast(50)
            )
        }

        // 8. Adaptive refinement — only when a primary lock is established
        newLockId?.let { lockedId -> maybeAdaptiveRefine(lockedId, devices, _uiState.value.relearnModeActive) }
    }

    /**
     * Increments non-target tick counter for devices scoring >= POSSIBLE that are not the lock.
     * Resets counter when a device becomes the primary lock.
     */
    private fun updateNonTargetCounts(matches: Map<String, KnownTargetMatchResult>) {
        matches.forEach { (id, result) ->
            when {
                id == primaryLockId -> nonTargetTickCounts.remove(id)
                result.confidence == KnownMatchConfidence.POSSIBLE ||
                    result.confidence == KnownMatchConfidence.STRONG -> {
                    nonTargetTickCounts[id] = (nonTargetTickCounts[id] ?: 0) + 1
                }
                else -> Unit // below threshold — don't track
            }
        }
    }

    /**
     * Applies a graduated penalty to persistent non-target devices.
     * Penalty = 1 per [NON_TARGET_PENALTY_INTERVAL] ticks over [NON_TARGET_THRESHOLD], capped at 3.
     */
    private fun applyNonTargetPenalties(
        matches: Map<String, KnownTargetMatchResult>,
        nonTargetIds: Set<String>
    ): Map<String, KnownTargetMatchResult> {
        if (nonTargetIds.isEmpty()) return matches
        return matches.mapValues { (id, result) ->
            if (id !in nonTargetIds || result.score <= 0) return@mapValues result
            val ticks = nonTargetTickCounts[id] ?: 0
            val extra = ((ticks - NON_TARGET_THRESHOLD) / NON_TARGET_PENALTY_INTERVAL)
                .coerceIn(0, 2)  // 0–2 extra points → total penalty 1–3
            val penalty = NON_TARGET_PENALTY + extra
            result.copy(score = (result.score - penalty).coerceAtLeast(0))
        }
    }

    private fun maybeAdaptiveRefine(lockedId: String, devices: List<ObservedDevice>, relearnMode: Boolean = false) {
        val sig = _uiState.value.knownTargetSignature ?: return
        val device = devices.find { it.id == lockedId } ?: return
        val temporalState = deviceTemporalStates[lockedId] ?: return

        val now = System.currentTimeMillis()
        // Relearn mode uses a tighter interval (5s instead of 30s)
        val interval = if (relearnMode) 5_000L else ADAPTIVE_INTERVAL_MS
        if (now - lastAdaptiveRefineAt < interval) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRefiningSignature = true) }
            val result = withContext(Dispatchers.IO) {
                adaptiveRefine(sig, device, temporalState, relearnMode)
            }
            if (result.wasRefined && result.signature != null) {
                val delta = result.deltaDescription ?: ""
                knownTargetRepository.saveWithHistory(result.signature, delta)
                lastAdaptiveRefineAt = System.currentTimeMillis()
                Log.d(TAG, "Adaptive refinement saved: $delta")
                _uiState.update {
                    it.copy(
                        knownTargetSignature = result.signature,
                        isRefiningSignature = false,
                        lastRefinementDelta = delta
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
            // Track high-score-without-lock for false positive guard
            val isPossibleOrStrong = result.confidence == KnownMatchConfidence.POSSIBLE ||
                result.confidence == KnownMatchConfidence.STRONG
            val isLocked = id == primaryLockId
            val prevHighScore = existing?.highScoreWithoutLockCount ?: 0
            val newHighScore = when {
                isLocked -> 0  // reset when locked
                isPossibleOrStrong -> prevHighScore + 1
                else -> prevHighScore
            }
            deviceLifecycles[id] = CandidateLifecycle(
                discoveredAt = existing?.discoveredAt ?: nowMs,
                lastSeenAt = device.lastSeenAt,
                peakScore = maxOf(existing?.peakScore ?: 0, result.score),
                totalSeenCount = (existing?.totalSeenCount ?: 0) + 1,
                strongSince = strongSince,
                highScoreWithoutLockCount = newHighScore
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
     * Serialises the learned signature, evolution history, latest session events,
     * match breakdowns, and rejected candidates to a pretty-printed JSON string.
     * Returns null if no signature exists. Caller should dispatch to IO before calling.
     */
    suspend fun buildExportJson(): String? {
        val sig = _uiState.value.knownTargetSignature ?: return null
        val sessions = withContext(Dispatchers.IO) { learningSessionRepository.getAllSessions() }
        val latestSession = sessions.maxByOrNull { it.startedAt }
        val events = if (latestSession != null)
            withContext(Dispatchers.IO) { learningSessionRepository.getEventsForSession(latestSession.sessionId) }
        else emptyList()
        val history = withContext(Dispatchers.IO) { knownTargetRepository.loadHistory() }

        // Collect current match breakdowns for ranked candidates
        val matchBreakdowns = _uiState.value.learnedMatchResults.mapValues { (_, result) ->
            mapOf(
                "score" to result.score,
                "confidence" to result.confidence.name,
                "breakdown" to result.scoreBreakdown.map {
                    mapOf("category" to it.category.name, "points" to it.points, "description" to it.description)
                },
                "temporalNotes" to result.temporalNotes
            )
        }

        val export = mapOf(
            "learnedSignature" to sig,
            "signatureConfidence" to sig.signatureConfidence.name,
            "sessionCount" to sessions.size,
            "latestSession" to latestSession,
            "latestSessionEvents" to events,
            "evolutionHistory" to history.map { snap ->
                mapOf(
                    "version" to snap.version,
                    "savedAt" to snap.savedAt,
                    "deltaDescription" to snap.deltaDescription
                )
            },
            "matchBreakdowns" to matchBreakdowns,
            "persistentNonTargetIds" to _uiState.value.persistentNonTargetIds.toList(),
            "nonTargetTickCounts" to nonTargetTickCounts.filter { (_, c) -> c >= NON_TARGET_THRESHOLD }
        )
        return GsonBuilder().setPrettyPrinting().create().toJson(export)
    }

    private fun deviceIsSignalLost(devices: List<ObservedDevice>, deviceId: String): Boolean =
        devices.find { it.id == deviceId }?.visibilityState == VisibilityState.SIGNAL_LOST

    override fun onCleared() {
        super.onCleared()
        scanCollectJob?.cancel()
        bleRepository.stopScanning()
    }
}
