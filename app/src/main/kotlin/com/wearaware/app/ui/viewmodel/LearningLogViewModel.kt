package com.wearaware.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wearaware.app.domain.model.PairedLearningSession
import com.wearaware.app.domain.repository.LearningSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LearningLogViewModel @Inject constructor(
    private val learningSessionRepository: LearningSessionRepository
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<PairedLearningSession>>(emptyList())
    val sessions: StateFlow<List<PairedLearningSession>> = _sessions

    init {
        viewModelScope.launch {
            _sessions.value = learningSessionRepository.getAllSessions()
        }
    }
}
