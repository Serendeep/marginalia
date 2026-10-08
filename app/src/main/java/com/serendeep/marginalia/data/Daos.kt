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

    @Query("SELECT * FROM lectures WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<LectureEntity?>

    @Delete
    suspend fun delete(lecture: LectureEntity)

    @Query("UPDATE lectures SET title = :title WHERE id = :id")
    suspend fun rename(id: String, title: String)

    @Query("UPDATE lectures SET courseId = :courseId WHERE id = :id")
    suspend fun move(id: String, courseId: String)

    @Query("DELETE FROM lectures WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM lectures WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LectureEntity?

    @Query(
        "UPDATE lectures SET lastOpenedAt = :at, " +
            "readingStatus = CASE WHEN readingStatus = 'TO_READ' THEN 'READING' ELSE readingStatus END " +
            "WHERE id = :id",
    )
    suspend fun markOpened(id: String, at: Long)

    @Query("UPDATE lectures SET lastPage = :page WHERE id = :id")
    suspend fun setLastPage(id: String, page: Int)

    @Query("UPDATE lectures SET readingStatus = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)
}

@Dao
interface StudySessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: StudySessionEntity)

    @Query("SELECT * FROM study_sessions ORDER BY startedAt")
    fun observeAll(): Flow<List<StudySessionEntity>>
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

data class TitleHit(val lectureId: String, val title: String)

data class PageHit(val lectureId: String, val title: String, val page: Int, val snippet: String)

/** A highlight with the title of the notebook it belongs to. */
data class HighlightRow(
    @androidx.room.Embedded val highlight: HighlightEntity,
    val lectureTitle: String,
)

data class PageRow(val rowId: Long, val documentId: String)

@Dao
interface SearchDao {
    @Query("INSERT INTO page_text (documentId, page, text) VALUES (:documentId, :page, :text)")
    suspend fun insertPage(documentId: String, page: Int, text: String)

    // Filtering on an unindexed FTS column inside DELETE left rows behind on the tablet's
    // SQLite, so rows are matched in Kotlin and deleted by rowid.
    @Query("SELECT rowid AS rowId, documentId FROM page_text")
    suspend fun pageRows(): List<PageRow>

    @Query("DELETE FROM page_text WHERE rowid IN (:rowIds)")
    suspend fun deleteRows(rowIds: List<Long>)

    @Query("DELETE FROM indexed_documents WHERE documentId = :documentId")
    suspend fun clearIndexed(documentId: String)

    @Query("DELETE FROM indexed_documents")
    suspend fun clearAllIndexed()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markIndexed(entry: IndexedDocumentEntity)

    @Query("SELECT * FROM documents WHERE id NOT IN (SELECT documentId FROM indexed_documents)")
    suspend fun unindexedDocuments(): List<DocumentEntity>

    @Query("SELECT COUNT(*) FROM page_text WHERE documentId = :documentId")
    suspend fun indexedPageCount(documentId: String): Int

    @Query("SELECT id AS lectureId, title FROM lectures WHERE title LIKE :pattern ESCAPE '\\' ORDER BY title LIMIT :limit")
    suspend fun searchTitles(pattern: String, limit: Int): List<TitleHit>

    @Query(
        "SELECT d.lectureId AS lectureId, l.title AS title, page_text.page AS page, " +
            "snippet(page_text, '$SNIPPET_OPEN', '$SNIPPET_CLOSE', '…', 2, 16) AS snippet " +
            "FROM page_text " +
            "JOIN documents d ON d.id = page_text.documentId " +
            "JOIN lectures l ON l.id = d.lectureId " +
            "WHERE page_text MATCH :match " +
            "AND d.versionIndex = (SELECT MAX(versionIndex) FROM documents WHERE lectureId = d.lectureId) " +
            "ORDER BY l.title, page_text.page LIMIT :limit",
    )
    suspend fun searchPages(match: String, limit: Int): List<PageHit>
}

@Dao
interface HighlightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(highlight: HighlightEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(highlights: List<HighlightEntity>)

    @Query("SELECT * FROM highlights WHERE strokeId IN (:strokeIds)")
    suspend fun forStrokes(strokeIds: List<String>): List<HighlightEntity>

    @Query("DELETE FROM highlights WHERE strokeId = :strokeId")
    suspend fun deleteByStroke(strokeId: String)

    @Query(
        "SELECT h.*, l.title AS lectureTitle FROM highlights h " +
            "JOIN lectures l ON l.id = h.lectureId ORDER BY l.title, h.page, h.createdAt",
    )
    fun observeAll(): Flow<List<HighlightRow>>

    @Query(
        "SELECT h.*, l.title AS lectureTitle FROM highlights h " +
            "JOIN lectures l ON l.id = h.lectureId ORDER BY h.createdAt DESC LIMIT :limit",
    )
    fun observeRecent(limit: Int): Flow<List<HighlightRow>>

    @Query("SELECT COUNT(*) FROM highlights")
    fun observeCount(): Flow<Int>

    @Query(
        "SELECT h.*, l.title AS lectureTitle FROM highlights h " +
            "JOIN lectures l ON l.id = h.lectureId " +
            "WHERE h.text LIKE :pattern ESCAPE '\\' ORDER BY h.createdAt DESC LIMIT :limit",
    )
    suspend fun search(pattern: String, limit: Int): List<HighlightRow>
}
