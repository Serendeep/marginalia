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

    @Query("SELECT * FROM lectures ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LectureEntity>>

    @Delete
    suspend fun delete(lecture: LectureEntity)

    @Query("UPDATE lectures SET title = :title WHERE id = :id")
    suspend fun rename(id: String, title: String)

    @Query("UPDATE lectures SET courseId = :courseId WHERE id = :id")
    suspend fun move(id: String, courseId: String)

    @Query("DELETE FROM lectures WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: DocumentEntity)

    @Query("SELECT * FROM documents WHERE lectureId = :lectureId ORDER BY versionIndex")
    fun observeByLecture(lectureId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE lectureId = :lectureId ORDER BY versionIndex")
    suspend fun getByLecture(lectureId: String): List<DocumentEntity>

    @Delete
    suspend fun delete(document: DocumentEntity)
}

@Dao
interface AnchorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(anchor: AnchorEntity)

    @Query("SELECT * FROM anchors WHERE lectureId = :lectureId ORDER BY label")
    fun observeByLecture(lectureId: String): Flow<List<AnchorEntity>>

    @Query("SELECT COUNT(*) FROM anchors WHERE lectureId = :lectureId")
    suspend fun countByLecture(lectureId: String): Int

    @Query("DELETE FROM anchors WHERE id = :id")
    suspend fun deleteById(id: String)
}

/** When a lecture's ink was last touched; drives "continue" ordering. */
data class LectureTouch(val lectureId: String, val lastAt: Long)

@Dao
interface StrokeDao {
    @Query("SELECT lectureId AS lectureId, MAX(endedAt) AS lastAt FROM strokes GROUP BY lectureId")
    fun observeLastWritten(): Flow<List<LectureTouch>>
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

    @Query("SELECT id FROM strokes WHERE anchorId = :anchorId")
    suspend fun idsBoundTo(anchorId: String): List<String>

    @Query("UPDATE strokes SET anchorId = NULL WHERE anchorId = :anchorId")
    suspend fun unbindAnchor(anchorId: String)

    @Query("UPDATE strokes SET anchorId = :anchorId WHERE id IN (:strokeIds)")
    suspend fun bindToAnchor(anchorId: String, strokeIds: List<String>)
}
