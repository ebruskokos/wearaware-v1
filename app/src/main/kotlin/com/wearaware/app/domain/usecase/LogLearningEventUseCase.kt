package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.LearningEventType
import com.wearaware.app.domain.model.PairedLearningEvent
import com.wearaware.app.domain.repository.LearningSessionRepository
import javax.inject.Inject

class LogLearningEventUseCase @Inject constructor(
    private val repository: LearningSessionRepository
) {
    suspend operator fun invoke(
        sessionId: String,
        eventType: LearningEventType,
        detail: String? = null
    ): PairedLearningEvent = repository.appendEvent(
        sessionId = sessionId,
        eventType = eventType,
        occurredAt = System.currentTimeMillis(),
        detail = detail
    )
}
