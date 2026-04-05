package com.wearaware.app.ui.viewmodel

import com.wearaware.app.domain.model.KnownTargetMatchResult
import com.wearaware.app.domain.model.KnownTargetSignature
import com.wearaware.app.domain.model.MatchConfidence
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.PersistenceAlert
import com.wearaware.app.domain.model.RankedCandidate
import com.wearaware.app.domain.model.ScanAnomalyEvent
import com.wearaware.app.domain.model.ScanFilter
import com.wearaware.app.domain.model.SessionReport
import com.wearaware.app.domain.model.TargetMatchResult

data class ScanUiState(
    val scanState: ScanState = ScanState.STOPPED,
    val devices: List<ObservedDevice> = emptyList(),
    val activeAlert: PersistenceAlert? = null,
    val appVersion: String = "",
    val ruleSetVersion: String = "",
    val ruleSetHash: String = "",
    val focusMode: Boolean = false,
    val debugMode: Boolean = false,
    val deviceMatchScores: Map<String, TargetMatchResult> = emptyMap(),
    val activeFilter: ScanFilter = ScanFilter.ALL,
    val knownTargetSignature: KnownTargetSignature? = null,
    val learnedMatchResults: Map<String, KnownTargetMatchResult> = emptyMap(),
    /** Stable top-N ranked candidates against the learned signature, updated each scan tick. */
    val rankedCandidates: List<RankedCandidate> = emptyList(),
    /** Device ID of the primary lock target, or null if no lock is active. */
    val primaryLockDeviceId: String? = null,
    /** True while an adaptive refinement is in progress (I/O save). */
    val isRefiningSignature: Boolean = false,
    /**
     * Most recent adaptive refinement delta description.
     * Set after each successful refinement; cleared on scan stop.
     */
    val lastRefinementDelta: String? = null,
    /**
     * Device IDs that have scored >= POSSIBLE for [ScanViewModel.NON_TARGET_THRESHOLD] ticks
     * without ever becoming the primary lock. Receive a graduated score penalty.
     * In-memory only — cleared on scan stop.
     */
    val persistentNonTargetIds: Set<String> = emptySet(),
    /** True when debug overlay (lock/score/RSSI strip) is visible. Toggled via toolbar. */
    val debugOverlayVisible: Boolean = false,
    /**
     * Relearn mode temporarily disables variance and drift guards in adaptive refinement,
     * allowing faster signature updates when the user explicitly requests it.
     */
    val relearnModeActive: Boolean = false,
    /** Anomaly events logged during the current scan session. In-memory only. */
    val anomalyLog: List<ScanAnomalyEvent> = emptyList(),
    /** Session report produced when the last scan session ended. Null before first session. */
    val lastSessionReport: SessionReport? = null,
) {
    val bestMatch: Pair<ObservedDevice, TargetMatchResult>?
        get() {
            val top = deviceMatchScores.values.firstOrNull {
                it.isTopCandidate &&
                    (it.confidence == MatchConfidence.HIGH || it.confidence == MatchConfidence.MEDIUM)
            } ?: return null
            val device = devices.find { it.id == top.deviceId } ?: return null
            return device to top
        }

    val sortedDevices: List<ObservedDevice>
        get() = if (focusMode && deviceMatchScores.isNotEmpty()) {
            devices.sortedByDescending { deviceMatchScores[it.id]?.score ?: 0 }
        } else {
            devices
        }

    val filteredDevices: List<ObservedDevice>
        get() = sortedDevices.filter { activeFilter.matches(it) }
}

enum class ScanState {
    STOPPED,
    SCANNING,
    BLUETOOTH_UNAVAILABLE,
    PERMISSIONS_REQUIRED
}
