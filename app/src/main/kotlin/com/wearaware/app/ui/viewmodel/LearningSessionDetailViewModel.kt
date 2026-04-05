package com.wearaware.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wearaware.app.domain.model.PairedLearningEvent
import com.wearaware.app.domain.model.PairedLearningSession
import com.wearaware.app.domain.repository.LearningSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LearningSessionDetailViewModel @Inject constructor(
    private val repository: LearningSessionRepository
) : ViewModel() {

    private val _events = MutableStateFlow<List<PairedLearningEvent>>(emptyList())
    val events: StateFlow<List<PairedLearningEvent>> = _events

    private val _session = MutableStateFlow<PairedLearningSession?>(null)
    val session: StateFlow<PairedLearningSession?> = _session

    fun load(sessionId: String) {
        viewModelScope.launch {
            _session.value = repository.getSession(sessionId)
            _events.value = repository.getEventsForSession(sessionId)
        }
    }
}
