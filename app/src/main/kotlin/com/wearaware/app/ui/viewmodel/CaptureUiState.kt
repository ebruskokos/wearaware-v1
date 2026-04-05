package com.wearaware.app.ui.viewmodel

import com.wearaware.app.domain.model.CaptureSession
import com.wearaware.app.domain.model.CompareMatchResult
import com.wearaware.app.domain.model.KnownTargetMatchResult
import com.wearaware.app.domain.model.KnownTargetSignature

data class CaptureUiState(
    val baselineCaptureState: CaptureState = CaptureState.IDLE,
    val targetCaptureState: CaptureState = CaptureState.IDLE,
    val baseline: CaptureSession? = null,
    val target: CaptureSession? = null,
    val liveAccumulatedCount: Int = 0,
    val compareResults: List<CompareMatchResult> = emptyList(),
    val captureStartedAt: Long = 0L,
    val isBleScanningActive: Boolean = false,
    val isBleAvailable: Boolean = true,
    val knownTargetSignature: KnownTargetSignature? = null,
    /** Keyed by fingerprintId. Populated after compare + known-target matching runs. */
    val learnedMatchResults: Map<String, KnownTargetMatchResult> = emptyMap(),
    /** Set by learnDevice(), shown as a snackbar, auto-cleared after 3s. */
    val learnSaveConfirmation: String? = null,
)

enum class CaptureState { IDLE, CAPTURING, DONE }
