package com.serendeep.marginalia.di

import android.content.Context
import coil3.ImageLoader
import coil3.request.crossfade
import com.serendeep.marginalia.library.PdfCoverFetcher
import com.serendeep.marginalia.library.PdfCoverKeyer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageModule {

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(PdfCoverKeyer())
                add(PdfCoverFetcher.Factory(context))
            }
            .crossfade(true)
            .build()
}
