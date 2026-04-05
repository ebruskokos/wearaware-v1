package com.wearaware.app.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.google.gson.Gson
import com.wearaware.app.data.local.CaptureDao
import com.wearaware.app.data.local.LearningSessionDao
import com.wearaware.app.data.local.ScanLogDao
import com.wearaware.app.data.local.WearAwareDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * PURPOSE: Provides the Room database and its DAO instances.
 * NOTES: Database is a singleton — one instance for the app lifetime.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WearAwareDatabase =
        Room.databaseBuilder(
            context,
            WearAwareDatabase::class.java,
            WearAwareDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideScanLogDao(database: WearAwareDatabase): ScanLogDao =
        database.scanLogDao()

    @Provides
    fun provideCaptureDao(database: WearAwareDatabase): CaptureDao =
        database.captureDao()

    @Provides
    fun provideLearningSessionDao(database: WearAwareDatabase): LearningSessionDao =
        database.learningSessionDao()

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("wearaware_prefs", Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()
}
