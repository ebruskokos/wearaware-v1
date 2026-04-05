package com.wearaware.app.ui.viewmodel

import com.wearaware.app.domain.model.KnownTargetMatchResult
import com.wearaware.app.domain.model.KnownTargetSignature
import com.wearaware.app.domain.model.MatchConfidence
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.PersistenceAlert
import com.wearaware.app.domain.model.ScanFilter
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
