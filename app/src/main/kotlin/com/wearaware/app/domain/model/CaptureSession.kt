package com.wearaware.app.domain.model

data class CaptureSession(
    val id: String,
    val type: CaptureType,
    val startedAt: Long,
    val stoppedAt: Long,
    val devices: List<CapturedDevice>
)
