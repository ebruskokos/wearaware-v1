package com.wearaware.app.ui.viewmodel

import android.content.IntentSender
import com.wearaware.app.domain.model.KnownTargetSignature
import com.wearaware.app.domain.model.PairedLearningEvent

enum class PairingFlowState {
    IDLE,
    CDM_SCANNING,
    CDM_WAITING_SELECTION,
    CDM_ASSOCIATED,
    GATT_CONNECTING,
    GATT_DISCOVERING,
    LEARNING,
    COMPLETED,
    FAILED
}

data class PairAndLearnUiState(
    val flowState: PairingFlowState = PairingFlowState.IDLE,
    val statusMessage: String = "",
    val sessionId: String? = null,
    val recentEvents: List<PairedLearningEvent> = emptyList(),
    val existingSignature: KnownTargetSignature? = null,
    val errorMessage: String? = null,
    val isTraining: Boolean = false,
    val trainingSessionId: String? = null
)

sealed class PairAndLearnEffect {
    data class LaunchCdmPicker(val intentSender: IntentSender) : PairAndLearnEffect()
}
