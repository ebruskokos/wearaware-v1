package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.repository.LearnedSignatureRepository
import javax.inject.Inject

class ClearLearnedSignatureUseCase @Inject constructor(
    private val repository: LearnedSignatureRepository
) {
    operator fun invoke() = repository.clear()
}
