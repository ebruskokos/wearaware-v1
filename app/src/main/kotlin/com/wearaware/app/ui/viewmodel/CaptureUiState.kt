package com.wearaware.app.ui.viewmodel

import com.wearaware.app.domain.model.CaptureSession
import com.wearaware.app.domain.model.CompareMatchResult

data class CaptureUiState(
    val baselineCaptureState: CaptureState = CaptureState.IDLE,
    val targetCaptureState: CaptureState = CaptureState.IDLE,
    val baseline: CaptureSession? = null,
    val target: CaptureSession? = null,
    /** Number of unique devices accumulated so far during the active capture. Updated live. */
    val liveAccumulatedCount: Int = 0,
    val compareResults: List<CompareMatchResult> = emptyList()
)

enum class CaptureState { IDLE, CAPTURING, DONE }
