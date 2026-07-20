package com.serendeep.marginalia.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CourseEntity::class,
        LectureEntity::class,
        DocumentEntity::class,
        StrokeEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MarginaliaDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun lectureDao(): LectureDao
    abstract fun documentDao(): DocumentDao
    abstract fun strokeDao(): StrokeDao
}
