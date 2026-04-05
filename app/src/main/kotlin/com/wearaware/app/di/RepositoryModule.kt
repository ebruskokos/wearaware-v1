package com.wearaware.app.di

import com.wearaware.app.data.repository.BleRepositoryImpl
import com.wearaware.app.data.repository.CaptureRepositoryImpl
import com.wearaware.app.data.repository.KnownTargetRepositoryImpl
import com.wearaware.app.data.repository.LearnedSignatureRepositoryImpl
import com.wearaware.app.data.repository.LearningSessionRepositoryImpl
import com.wearaware.app.data.repository.ScanLogRepositoryImpl
import com.wearaware.app.domain.repository.BleRepository
import com.wearaware.app.domain.repository.CaptureRepository
import com.wearaware.app.domain.repository.KnownTargetRepository
import com.wearaware.app.domain.repository.LearnedSignatureRepository
import com.wearaware.app.domain.repository.LearningSessionRepository
import com.wearaware.app.domain.repository.ScanLogRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * PURPOSE: Binds data layer implementations to their domain repository interfaces.
 *   The domain layer depends on interfaces; the data layer provides implementations.
 * NOTES: @Binds is preferred over @Provides for interface-to-implementation binding
 *   because it generates less code and is more efficient.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBleRepository(impl: BleRepositoryImpl): BleRepository

    @Binds
    @Singleton
    abstract fun bindScanLogRepository(impl: ScanLogRepositoryImpl): ScanLogRepository

    @Binds
    @Singleton
    abstract fun bindCaptureRepository(impl: CaptureRepositoryImpl): CaptureRepository

    @Binds
    @Singleton
    abstract fun bindKnownTargetRepository(impl: KnownTargetRepositoryImpl): KnownTargetRepository

    @Binds
    @Singleton
    abstract fun bindLearningSessionRepository(impl: LearningSessionRepositoryImpl): LearningSessionRepository

    @Binds
    @Singleton
    abstract fun bindLearnedSignatureRepository(impl: LearnedSignatureRepositoryImpl): LearnedSignatureRepository
}
