package com.wearaware.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.wearaware.app.domain.model.KnownTargetSignature
import com.wearaware.app.domain.model.PairedLearningEvent
import com.wearaware.app.domain.model.PairedLearningSession
import com.wearaware.app.domain.repository.KnownTargetRepository
import com.wearaware.app.domain.repository.LearningSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignatureLogViewerUiState(
    val signature: KnownTargetSignature? = null,
    val sessions: List<PairedLearningSession> = emptyList(),
    val latestSessionEvents: List<PairedLearningEvent> = emptyList(),
    val exportJson: String? = null,
    val showRawJson: Boolean = false
)

@HiltViewModel
class SignatureLogViewerViewModel @Inject constructor(
    private val knownTargetRepository: KnownTargetRepository,
    private val learningSessionRepository: LearningSessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignatureLogViewerUiState())
    val uiState: StateFlow<SignatureLogViewerUiState> = _uiState

    init {
        viewModelScope.launch {
            val sig = knownTargetRepository.load()
            val sessions = learningSessionRepository.getAllSessions()
                .sortedByDescending { it.startedAt }
            val latest = sessions.firstOrNull()
            val events = if (latest != null)
                learningSessionRepository.getEventsForSession(latest.sessionId)
            else emptyList()

            val json = if (sig != null) {
                val export = mapOf(
                    "learnedSignature" to sig,
                    "sessionCount" to sessions.size,
                    "latestSession" to latest,
                    "latestSessionEvents" to events
                )
                GsonBuilder().setPrettyPrinting().create().toJson(export)
            } else null

            _uiState.value = SignatureLogViewerUiState(
                signature = sig,
                sessions = sessions,
                latestSessionEvents = events,
                exportJson = json
            )
        }
    }

    fun toggleRawJson() {
        _uiState.value = _uiState.value.copy(showRawJson = !_uiState.value.showRawJson)
    }
}
