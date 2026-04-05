package com.wearaware.app.di

import com.wearaware.app.data.ble.BleGattManager
import com.wearaware.app.data.ble.BleGattManagerImpl
import com.wearaware.app.data.repository.RulesRepositoryImpl
import com.wearaware.app.domain.repository.RulesRepository
import com.wearaware.app.domain.rules.FingerprintClassifier
import com.wearaware.app.util.AboutInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * PURPOSE: Provides BLE-related and classification dependencies.
 * NOTES: FingerprintClassifier depends on RulesRepository — rules are loaded once
 *   (lazily) and cached by RulesRepositoryImpl.
 *   FingerprintClassifier does NOT have @Inject constructor because RulesData is not
 *   directly injectable by Hilt. This @Provides method is the only binding.
 */
@Module
@InstallIn(SingletonComponent::class)
object BleModule {

    @Provides
    @Singleton
    fun provideRulesRepository(impl: RulesRepositoryImpl): RulesRepository = impl

    @Provides
    @Singleton
    fun provideFingerprintClassifier(rulesRepository: RulesRepository): FingerprintClassifier =
        FingerprintClassifier(rulesRepository.getRules())

    @Provides
    @Singleton
    fun provideAboutInfo(rulesRepository: RulesRepository): AboutInfo {
        val metadata = rulesRepository.getRules().metadata
        return AboutInfo(
            appVersion = "1.0.0",
            ruleSetVersion = metadata.version,
            ruleSetHash = metadata.hash
        )
    }

    @Provides
    @Singleton
    fun provideBleGattManager(impl: BleGattManagerImpl): BleGattManager = impl
}

