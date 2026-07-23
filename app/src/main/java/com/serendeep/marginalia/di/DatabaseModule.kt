package com.serendeep.marginalia.di

import android.content.Context
import androidx.room.Room
import com.serendeep.marginalia.data.AnchorDao
import com.serendeep.marginalia.data.CourseDao
import com.serendeep.marginalia.data.DocumentDao
import com.serendeep.marginalia.data.LectureDao
import com.serendeep.marginalia.data.MarginaliaDatabase
import com.serendeep.marginalia.data.StrokeDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MarginaliaDatabase =
        Room.databaseBuilder(context, MarginaliaDatabase::class.java, "marginalia.db")
            .addMigrations(MarginaliaDatabase.MIGRATION_1_2, MarginaliaDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideCourseDao(db: MarginaliaDatabase): CourseDao = db.courseDao()

    @Provides
    fun provideLectureDao(db: MarginaliaDatabase): LectureDao = db.lectureDao()

    @Provides
    fun provideDocumentDao(db: MarginaliaDatabase): DocumentDao = db.documentDao()

    @Provides
    fun provideStrokeDao(db: MarginaliaDatabase): StrokeDao = db.strokeDao()

    @Provides
    fun provideAnchorDao(db: MarginaliaDatabase): AnchorDao = db.anchorDao()
}
