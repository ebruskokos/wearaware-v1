package com.wearaware.app.ui.viewmodel

import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.PersistenceAlert

/**
 * PURPOSE: Complete UI state for ScanScreen. ViewModel exposes this as a StateFlow.
 *   Compose observes it and recomposes only when the state actually changes.
 * NOTES: Using a single data class (rather than separate StateFlows) means UI always
 *   has a consistent snapshot — no partially-updated state between emissions.
 */
data class ScanUiState(
    val scanState: ScanState = ScanState.STOPPED,
    val devices: List<ObservedDevice> = emptyList(),
    /** The active persistence alert, if any. One at a time — most recently triggered. */
    val activeAlert: PersistenceAlert? = null,
    val appVersion: String = "",
    val ruleSetVersion: String = "",
    val ruleSetHash: String = ""
)

enum class ScanState {
    STOPPED,
    SCANNING,
    BLUETOOTH_UNAVAILABLE,
    PERMISSIONS_REQUIRED
}
