package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.repository.LearnedSignatureRepository
import io.mockk.*
import org.junit.Test

class ClearLearnedSignatureUseCaseTest {

    private val repo = mockk<LearnedSignatureRepository>(relaxed = true)
    private val useCase = ClearLearnedSignatureUseCase(repo)

    @Test
    fun `calls repository clear exactly once`() {
        useCase()
        verify(exactly = 1) { repo.clear() }
    }

    @Test
    fun `does not call save or load`() {
        useCase()
        verify(exactly = 0) { repo.save(any()) }
        verify(exactly = 0) { repo.load() }
    }
}
