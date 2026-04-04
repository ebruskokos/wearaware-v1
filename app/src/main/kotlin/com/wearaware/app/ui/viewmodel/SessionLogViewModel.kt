package com.wearaware.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wearaware.app.domain.model.ScanLogEntry
import com.wearaware.app.domain.usecase.ClearSessionLogUseCase
import com.wearaware.app.domain.usecase.GetSessionLogUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PURPOSE: Provides session log data for SessionLogScreen.
 * NOTES: log StateFlow is backed by Room's Flow — updates automatically when
 *   new entries are logged. SharingStarted.Lazily means collection starts on first subscriber.
 */
@HiltViewModel
class SessionLogViewModel @Inject constructor(
    private val getSessionLog: GetSessionLogUseCase,
    private val clearSessionLog: ClearSessionLogUseCase
) : ViewModel() {

    val log: StateFlow<List<ScanLogEntry>> = getSessionLog()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    /** Clears all session log entries. Called after user confirms the clear dialog. */
    fun clearLog() {
        viewModelScope.launch { clearSessionLog() }
    }
}
