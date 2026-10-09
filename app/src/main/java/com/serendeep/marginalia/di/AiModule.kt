package com.serendeep.marginalia.di

import android.content.Context
import com.serendeep.marginalia.ai.AiSettings
import com.serendeep.marginalia.ai.TokenStore
import com.serendeep.marginalia.ai.agent.AgentData
import com.serendeep.marginalia.ai.agent.RepositoryAgentData
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context): TokenStore = TokenStore(context)

    @Provides
    fun provideAgentData(impl: RepositoryAgentData): AgentData = impl

    @Provides
    @Singleton
    fun provideAiSettings(@ApplicationContext context: Context): AiSettings = AiSettings(context)
}
