package com.serendeep.marginalia.di

import com.serendeep.marginalia.update.SafetySnapshot
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SafetySnapshotModule {

    @Provides
    @Singleton
    fun provideSafetySnapshot(): SafetySnapshot = SafetySnapshot { }
}
