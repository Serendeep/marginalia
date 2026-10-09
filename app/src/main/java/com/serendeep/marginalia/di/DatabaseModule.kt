package com.serendeep.marginalia.di

import android.content.Context
import androidx.room.Room
import com.serendeep.marginalia.data.AnchorDao
import com.serendeep.marginalia.data.CardDao
import com.serendeep.marginalia.data.ChatDao
import com.serendeep.marginalia.data.CourseDao
import com.serendeep.marginalia.data.DocumentDao
import com.serendeep.marginalia.data.HighlightDao
import com.serendeep.marginalia.data.LectureDao
import com.serendeep.marginalia.data.MarginaliaDatabase
import com.serendeep.marginalia.data.SearchDao
import com.serendeep.marginalia.data.StrokeDao
import com.serendeep.marginalia.data.StudySessionDao
import com.serendeep.marginalia.data.TagDao
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
            .addMigrations(
                MarginaliaDatabase.MIGRATION_1_2,
                MarginaliaDatabase.MIGRATION_2_3,
                MarginaliaDatabase.MIGRATION_3_4,
                MarginaliaDatabase.MIGRATION_4_5,
                MarginaliaDatabase.MIGRATION_5_6,
                MarginaliaDatabase.MIGRATION_6_7,
                MarginaliaDatabase.MIGRATION_7_8,
                MarginaliaDatabase.MIGRATION_8_9,
                MarginaliaDatabase.MIGRATION_9_10,
            )
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

    @Provides
    fun provideStudySessionDao(db: MarginaliaDatabase): StudySessionDao = db.studySessionDao()

    @Provides
    fun provideSearchDao(db: MarginaliaDatabase): SearchDao = db.searchDao()

    @Provides
    fun provideHighlightDao(db: MarginaliaDatabase): HighlightDao = db.highlightDao()

    @Provides
    fun provideCardDao(db: MarginaliaDatabase): CardDao = db.cardDao()

    @Provides
    fun provideTagDao(db: MarginaliaDatabase): TagDao = db.tagDao()

    @Provides
    fun provideChatDao(db: MarginaliaDatabase): ChatDao = db.chatDao()
}
