package com.serendeep.marginalia.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(course: CourseEntity)

    @Query("SELECT * FROM courses ORDER BY orderIndex, createdAt")
    fun observeAll(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses ORDER BY orderIndex, createdAt")
    suspend fun getAll(): List<CourseEntity>

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteById(id: String)

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

    @Query("SELECT COUNT(*) FROM lectures WHERE courseId = :courseId")
    suspend fun countInCourse(courseId: String): Int

    @Query("SELECT * FROM lectures WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LectureEntity?

    @Query("SELECT * FROM lectures WHERE lastOpenedAt >= :from AND lastOpenedAt < :to ORDER BY lastOpenedAt")
    suspend fun openedBetween(from: Long, to: Long): List<LectureEntity>

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

    // Fills only what is still empty, so an identifier the user already has is never overwritten.
    @Query("UPDATE lectures SET doi = COALESCE(doi, :doi), arxivId = COALESCE(arxivId, :arxivId) WHERE id = :id")
    suspend fun fillIdentifiers(id: String, doi: String?, arxivId: String?)

    @Query("UPDATE lectures SET bibtex = :bibtex WHERE id = :id")
    suspend fun setBibtex(id: String, bibtex: String)
}

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(link: LectureTagEntity)

    @Query("DELETE FROM lecture_tags WHERE lectureId = :lectureId AND tagId = :tagId")
    suspend fun unlink(lectureId: String, tagId: String)

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): TagEntity?

    @Query("SELECT tagId FROM lecture_tags WHERE lectureId = :lectureId")
    suspend fun tagIdsOf(lectureId: String): List<String>

    @Query("SELECT * FROM tags ORDER BY name")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM lecture_tags")
    fun observeLinks(): Flow<List<LectureTagEntity>>

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface StudySessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: StudySessionEntity)

    @Query("SELECT * FROM study_sessions ORDER BY startedAt")
    fun observeAll(): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions WHERE startedAt >= :from AND startedAt < :to ORDER BY startedAt")
    suspend fun between(from: Long, to: Long): List<StudySessionEntity>
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

    // Handwriting to recognise: all margin ink, plus opaque pen ink on PDF pages (highlighters are translucent).
    @Query("SELECT lectureId, id AS strokeId FROM strokes WHERE surface = 'MARGIN' OR ((brushColor >> 24) & 255) = 255")
    suspend fun handwritingStrokeIds(): List<StrokeRef>

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

data class StrokeRef(val lectureId: String, val strokeId: String)

data class InkRow(val rowId: Long, val lectureId: String)

data class InkPageText(val page: Int?, val text: String)

data class InkHit(val lectureId: String, val title: String, val blockKey: String, val page: Int?, val snippet: String)

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

    @Query("INSERT INTO ink_text (lectureId, blockKey, page, text) VALUES (:lectureId, :blockKey, :page, :text)")
    suspend fun insertInk(lectureId: String, blockKey: String, page: Int?, text: String)

    // Same unindexed-column caveat as pageRows: filter in Kotlin, delete by rowid.
    @Query("SELECT rowid AS rowId, lectureId FROM ink_text")
    suspend fun inkRows(): List<InkRow>

    @Query("DELETE FROM ink_text WHERE rowid IN (:rowIds)")
    suspend fun deleteInkRows(rowIds: List<Long>)

    @Query("SELECT * FROM ink_index_state")
    suspend fun inkStates(): List<InkIndexStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putInkState(state: InkIndexStateEntity)

    @Query(
        "SELECT ink_text.lectureId AS lectureId, l.title AS title, ink_text.blockKey AS blockKey, " +
            "ink_text.page AS page, " +
            "snippet(ink_text, '$SNIPPET_OPEN', '$SNIPPET_CLOSE', '…', 3, 16) AS snippet " +
            "FROM ink_text JOIN lectures l ON l.id = ink_text.lectureId " +
            "WHERE ink_text MATCH :match ORDER BY l.title LIMIT :limit",
    )
    suspend fun searchInk(match: String, limit: Int): List<InkHit>

    @Query("SELECT page, text FROM ink_text WHERE lectureId = :lectureId AND (:page IS NULL OR page = :page)")
    suspend fun inkFor(lectureId: String, page: Int?): List<InkPageText>

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
        "SELECT DISTINCT l.id AS lectureId, l.title AS title FROM lectures l " +
            "JOIN lecture_tags lt ON lt.lectureId = l.id JOIN tags t ON t.id = lt.tagId " +
            "WHERE t.name LIKE :pattern ESCAPE '\\' ORDER BY l.title LIMIT :limit",
    )
    suspend fun searchTagged(pattern: String, limit: Int): List<TitleHit>

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

    @Query(
        "SELECT page_text.text FROM page_text JOIN documents d ON d.id = page_text.documentId " +
            "WHERE d.lectureId = :lectureId AND page_text.page = :page " +
            "AND d.versionIndex = (SELECT MAX(versionIndex) FROM documents WHERE lectureId = d.lectureId) LIMIT 1",
    )
    suspend fun pageText(lectureId: String, page: Int): String?
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

    @Query(
        "SELECT h.*, l.title AS lectureTitle FROM highlights h " +
            "JOIN lectures l ON l.id = h.lectureId WHERE h.lectureId = :lectureId " +
            "ORDER BY h.page, h.createdAt LIMIT :limit",
    )
    suspend fun forLecture(lectureId: String, limit: Int): List<HighlightRow>

    @Query("SELECT COUNT(*) FROM highlights")
    fun observeCount(): Flow<Int>

    @Query(
        "SELECT h.*, l.title AS lectureTitle FROM highlights h " +
            "JOIN lectures l ON l.id = h.lectureId WHERE h.createdAt >= :from AND h.createdAt < :to ORDER BY h.createdAt",
    )
    suspend fun between(from: Long, to: Long): List<HighlightRow>

    @Query(
        "SELECT h.*, l.title AS lectureTitle FROM highlights h " +
            "JOIN lectures l ON l.id = h.lectureId " +
            "WHERE h.text LIKE :pattern ESCAPE '\\' ORDER BY h.createdAt DESC LIMIT :limit",
    )
    suspend fun search(pattern: String, limit: Int): List<HighlightRow>
}

data class RetentionRow(val total: Int, val good: Int)

@Dao
interface CardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: CardEntity)

    @Update
    suspend fun update(card: CardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ReviewLogEntity)

    @Transaction
    suspend fun applyGrade(card: CardEntity, log: ReviewLogEntity) {
        update(card)
        insertLog(log)
    }

    @Query("SELECT * FROM cards WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CardEntity?

    @Query("SELECT * FROM cards")
    suspend fun getAll(): List<CardEntity>

    @Query("SELECT * FROM cards")
    fun observeAll(): Flow<List<CardEntity>>

    @Query("SELECT frontImagePath FROM cards WHERE lectureId = :lectureId AND frontImagePath IS NOT NULL")
    suspend fun imagePathsForLecture(lectureId: String): List<String>

    @Query("SELECT COUNT(*) FROM cards WHERE createdAt >= :from AND createdAt < :to")
    suspend fun createdBetween(from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM review_log WHERE reviewedAt >= :from AND reviewedAt < :to")
    suspend fun reviewsBetween(from: Long, to: Long): Int

    @Query(
        "SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN grade >= 2 THEN 1 ELSE 0 END), 0) AS good " +
            "FROM review_log WHERE prevIntervalDays IS NOT NULL AND reviewedAt >= :from AND reviewedAt < :to",
    )
    suspend fun retentionBetween(from: Long, to: Long): RetentionRow

    @Query("SELECT reviewedAt FROM review_log ORDER BY reviewedAt")
    fun observeReviewTimes(): Flow<List<Long>>

    @Query(
        "SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN grade >= 2 THEN 1 ELSE 0 END), 0) AS good " +
            "FROM review_log WHERE prevIntervalDays IS NOT NULL AND reviewedAt >= :since",
    )
    fun observeRetention(since: Long): Flow<RetentionRow>

    // Cards whose very first answer landed since [start].
    @Query(
        "SELECT COUNT(DISTINCT r.cardId) FROM review_log r WHERE r.reviewedAt >= :start " +
            "AND NOT EXISTS (SELECT 1 FROM review_log p WHERE p.cardId = r.cardId AND p.reviewedAt < :start)",
    )
    suspend fun newIntroducedSince(start: Long): Int

    @Query(
        "SELECT COUNT(DISTINCT r.cardId) FROM review_log r WHERE r.reviewedAt >= :start " +
            "AND NOT EXISTS (SELECT 1 FROM review_log p WHERE p.cardId = r.cardId AND p.reviewedAt < :start)",
    )
    fun observeNewIntroducedSince(start: Long): Flow<Int>
}

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putChat(chat: ChatEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTurn(turn: ChatTurnEntity)

    @Transaction
    suspend fun save(chat: ChatEntity, turn: ChatTurnEntity) {
        putChat(chat)
        putTurn(turn)
    }

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun chat(id: String): ChatEntity?

    @Query("SELECT * FROM chats WHERE scope = :scope ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latest(scope: String): ChatEntity?

    @Query("SELECT * FROM chats WHERE scope = :scope ORDER BY updatedAt DESC")
    fun observeScope(scope: String): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chat_turns WHERE chatId = :chatId ORDER BY idx")
    suspend fun turns(chatId: String): List<ChatTurnEntity>

    @Query("UPDATE chats SET title = :title WHERE id = :id")
    suspend fun rename(id: String, title: String)

    @Query("DELETE FROM chat_turns WHERE chatId = :id")
    suspend fun deleteTurns(id: String)

    @Query("DELETE FROM chats WHERE id = :id")
    suspend fun deleteChat(id: String)

    @Transaction
    suspend fun delete(id: String) {
        deleteTurns(id)
        deleteChat(id)
    }
}
