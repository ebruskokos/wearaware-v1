package com.wearaware.app.ui.viewmodel

import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.PersistenceAlert
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
    val deviceMatchScores: Map<String, TargetMatchResult> = emptyMap()
) {
    /** Best-scoring candidate device paired with its match result, or null. */
    val bestMatch: Pair<ObservedDevice, TargetMatchResult>?
        get() {
            val top = deviceMatchScores.values.firstOrNull { it.isTopCandidate } ?: return null
            val device = devices.find { it.id == top.deviceId } ?: return null
            return device to top
        }

    /**
     * In focus mode: sorted by target match score descending.
     * Otherwise: sorted by signal strength (from BleRepositoryImpl).
     */
    val sortedDevices: List<ObservedDevice>
        get() = if (focusMode && deviceMatchScores.isNotEmpty()) {
            devices.sortedByDescending { deviceMatchScores[it.id]?.score ?: 0 }
        } else {
            devices
        }
}

enum class ScanState {
    STOPPED,
    SCANNING,
    BLUETOOTH_UNAVAILABLE,
    PERMISSIONS_REQUIRED
}
