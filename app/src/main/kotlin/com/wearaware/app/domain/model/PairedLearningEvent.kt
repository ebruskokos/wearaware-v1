package com.wearaware.app.domain.model

enum class LearningEventType {
    SESSION_STARTED,
    CDM_SCANNING,
    CDM_WAITING_SELECTION,
    CDM_ASSOCIATED,
    CDM_FAILED,
    GATT_CONNECTING,
    GATT_CONNECTED,
    GATT_SERVICES_DISCOVERED,
    GATT_DISCONNECTED,
    GATT_FAILED,
    BLE_SIGNAL_OBSERVED,
    SIGNATURE_BUILT,
    SESSION_COMPLETED
}

data class PairedLearningEvent(
    val eventId: Long,
    val sessionId: String,
    val eventType: LearningEventType,
    val occurredAt: Long,
    val detail: String?
)
