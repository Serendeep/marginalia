package com.serendeep.marginalia.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(course: CourseEntity)

    @Query("SELECT * FROM courses ORDER BY orderIndex, createdAt")
    fun observeAll(): Flow<List<CourseEntity>>

    @Delete
    suspend fun delete(course: CourseEntity)
}

@Dao
interface LectureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lecture: LectureEntity)

    @Query("SELECT * FROM lectures WHERE courseId = :courseId ORDER BY orderIndex, createdAt")
    fun observeByCourse(courseId: String): Flow<List<LectureEntity>>

    @Delete
    suspend fun delete(lecture: LectureEntity)
}

@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: DocumentEntity)

    @Query("SELECT * FROM documents WHERE lectureId = :lectureId ORDER BY versionIndex")
    fun observeByLecture(lectureId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE lectureId = :lectureId ORDER BY versionIndex")
    suspend fun getByLecture(lectureId: String): List<DocumentEntity>

    @Delete
    suspend fun delete(document: DocumentEntity)
}

@Dao
interface StrokeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stroke: StrokeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(strokes: List<StrokeEntity>)

    @Query("SELECT * FROM strokes WHERE lectureId = :lectureId ORDER BY startedAt")
    suspend fun getByLecture(lectureId: String): List<StrokeEntity>

    @Query("SELECT * FROM strokes WHERE lectureId = :lectureId ORDER BY startedAt")
    fun observeByLecture(lectureId: String): Flow<List<StrokeEntity>>

    @Query("DELETE FROM strokes WHERE id = :id")
    suspend fun deleteById(id: String)
}
