package com.serendeep.marginalia.data

import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarginaliaRepository @Inject constructor(
    private val courseDao: CourseDao,
    private val lectureDao: LectureDao,
    private val documentDao: DocumentDao,
    private val strokeDao: StrokeDao,
    private val anchorDao: AnchorDao,
    private val sessionDao: StudySessionDao,
    private val searchDao: SearchDao,
    private val highlightDao: HighlightDao,
    private val cardDao: CardDao,
    private val tagDao: TagDao,
) {
    fun observeCourses(): Flow<List<CourseEntity>> = courseDao.observeAll()

    fun observeLectures(courseId: String): Flow<List<LectureEntity>> =
        lectureDao.observeByCourse(courseId)

    fun observeAllLectures(): Flow<List<LectureEntity>> = lectureDao.observeAll()

    fun observeLecture(id: String): Flow<LectureEntity?> = lectureDao.observeById(id)

    fun observeAllDocuments(): Flow<List<DocumentEntity>> = documentDao.observeAll()

    fun observeLastWritten(): Flow<List<LectureTouch>> = strokeDao.observeLastWritten()

    fun observeDocuments(lectureId: String): Flow<List<DocumentEntity>> =
        documentDao.observeByLecture(lectureId)

    fun observeStrokes(lectureId: String): Flow<List<StrokeEntity>> =
        strokeDao.observeByLecture(lectureId)

    suspend fun createCourse(name: String, colorIndex: Int, emoji: String?): CourseEntity {
        val course = CourseEntity(
            id = newId(),
            name = name,
            createdAt = now(),
            orderIndex = now(),
            colorIndex = colorIndex,
            emoji = emoji,
        )
        courseDao.insert(course)
        return course
    }

    suspend fun createLecture(courseId: String, title: String): LectureEntity {
        val lecture = LectureEntity(
            id = newId(),
            courseId = courseId,
            title = title,
            createdAt = now(),
            orderIndex = now(),
        )
        lectureDao.insert(lecture)
        return lecture
    }

    suspend fun importDocument(
        lectureId: String,
        fileName: String,
        localPath: String,
        pageCount: Int,
    ): DocumentEntity {
        val versionIndex = documentDao.getByLecture(lectureId).size
        val document = DocumentEntity(
            id = newId(),
            lectureId = lectureId,
            fileName = fileName,
            localPath = localPath,
            pageCount = pageCount,
            importedAt = now(),
            versionIndex = versionIndex,
        )
        documentDao.insert(document)
        return document
    }

    suspend fun getLecture(id: String): LectureEntity? = lectureDao.getById(id)

    suspend fun markOpened(id: String) = lectureDao.markOpened(id, now())

    suspend fun setLastPage(id: String, page: Int) = lectureDao.setLastPage(id, page)

    suspend fun setReadingStatus(id: String, status: ReadingStatus) =
        lectureDao.setStatus(id, status.name)

    fun observeSessions(): Flow<List<StudySessionEntity>> = sessionDao.observeAll()

    suspend fun saveSession(lectureId: String?, kind: SessionKind, startedAt: Long, endedAt: Long) =
        sessionDao.insert(StudySessionEntity(newId(), lectureId, kind.name, startedAt, endedAt))

    fun observeTags(): Flow<List<TagEntity>> = tagDao.observeTags()

    fun observeTagLinks(): Flow<List<LectureTagEntity>> = tagDao.observeLinks()

    /** Tags the lecture with [name], creating the tag on first use (names compare case-insensitively). */
    suspend fun addTag(lectureId: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val tag = tagDao.byName(clean) ?: TagEntity(newId(), clean, now()).also { tagDao.insert(it) }
        tagDao.link(LectureTagEntity(lectureId, tag.id))
    }

    suspend fun setTagged(lectureId: String, tagId: String, tagged: Boolean) =
        if (tagged) tagDao.link(LectureTagEntity(lectureId, tagId)) else tagDao.unlink(lectureId, tagId)

    suspend fun deleteTag(tagId: String) = tagDao.delete(tagId)

    suspend fun fillIdentifiers(lectureId: String, doi: String?, arxivId: String?) {
        if (doi != null || arxivId != null) lectureDao.fillIdentifiers(lectureId, doi, arxivId)
    }

    suspend fun saveBibtex(lectureId: String, bibtex: String) = lectureDao.setBibtex(lectureId, bibtex)

    suspend fun latestDocument(lectureId: String): DocumentEntity? =
        documentDao.getByLecture(lectureId).maxByOrNull { it.versionIndex }

    suspend fun deleteLecture(lecture: LectureEntity) {
        deleteInkText(lecture.id)
        lectureDao.delete(lecture)
    }

    suspend fun renameLecture(lectureId: String, title: String) =
        lectureDao.rename(lectureId, title)

    suspend fun moveLecture(lectureId: String, courseId: String) =
        lectureDao.move(lectureId, courseId)

    suspend fun deleteLecture(lectureId: String) {
        val documents = documentDao.getByLecture(lectureId)
        val files = documents.map { File(it.localPath) } +
            cardDao.imagePathsForLecture(lectureId).map { File(it) }
        // The text index is virtual, so the cascade below cannot reach it.
        documents.forEach { dropIndex(it.id) }
        deleteInkText(lectureId)
        lectureDao.deleteById(lectureId) // FK CASCADE removes documents/strokes/anchors
        files.forEach { runCatching { it.delete() } } // best-effort; rows are gone already
    }

    fun observeCards(): Flow<List<CardEntity>> = cardDao.observeAll()

    suspend fun allCards(): List<CardEntity> = cardDao.getAll()

    fun observeReviewTimes(): Flow<List<Long>> = cardDao.observeReviewTimes()

    fun observeRetention(since: Long): Flow<RetentionRow> = cardDao.observeRetention(since)

    fun observeNewIntroducedSince(start: Long): Flow<Int> = cardDao.observeNewIntroducedSince(start)

    suspend fun newIntroducedSince(start: Long): Int = cardDao.newIntroducedSince(start)

    suspend fun createCard(
        source: CardSource,
        lectureId: String? = null,
        documentId: String? = null,
        page: Int? = null,
        frontText: String? = null,
        frontImagePath: String? = null,
        backText: String? = null,
        highlightId: String? = null,
        id: String = newId(),
    ): CardEntity {
        val card = CardEntity(
            id = id,
            lectureId = lectureId,
            documentId = documentId,
            page = page,
            frontText = frontText,
            frontImagePath = frontImagePath,
            backText = backText,
            source = source.name,
            highlightId = highlightId,
            dueAt = now(),
            createdAt = now(),
        )
        cardDao.insert(card)
        return card
    }

    /** Stores the rescheduled card and its log row together. */
    suspend fun saveGrade(updated: CardEntity, previous: CardEntity, grade: Int, at: Long) {
        val wasReview = previous.cardState == CardState.REVIEW
        cardDao.applyGrade(
            updated,
            ReviewLogEntity(
                id = newId(),
                cardId = updated.id,
                grade = grade,
                reviewedAt = at,
                prevIntervalDays = if (wasReview) previous.intervalDays else null,
                newIntervalDays = updated.intervalDays,
            ),
        )
    }

    suspend fun saveStroke(stroke: InkStroke) = strokeDao.insert(stroke.toEntity())

    suspend fun saveStrokes(strokes: List<InkStroke>) = strokeDao.insertAll(strokes.map { it.toEntity() })

    /** Removes a stroke along with any highlight it produced. */
    suspend fun deleteStroke(id: String) {
        highlightDao.deleteByStroke(id)
        strokeDao.deleteById(id)
    }

    suspend fun saveHighlight(highlight: HighlightEntity) = highlightDao.insert(highlight)

    suspend fun saveHighlights(highlights: List<HighlightEntity>) {
        if (highlights.isNotEmpty()) highlightDao.insertAll(highlights)
    }

    suspend fun highlightsForStrokes(strokeIds: List<String>): List<HighlightEntity> =
        if (strokeIds.isEmpty()) emptyList() else highlightDao.forStrokes(strokeIds)

    fun observeHighlights(): Flow<List<HighlightRow>> = highlightDao.observeAll()

    fun observeRecentHighlights(limit: Int): Flow<List<HighlightRow>> = highlightDao.observeRecent(limit)

    fun observeHighlightCount(): Flow<Int> = highlightDao.observeCount()

    suspend fun anchorCount(lectureId: String): Int = anchorDao.countByLecture(lectureId)

    suspend fun unindexedDocuments(): List<DocumentEntity> = searchDao.unindexedDocuments()

    /** Replaces a document's indexed pages; blank pages are skipped. */
    suspend fun indexPage(documentId: String, page: Int, text: String) {
        if (text.isNotBlank()) searchDao.insertPage(documentId, page, text)
    }

    private suspend fun deletePages(documentId: String) {
        searchDao.pageRows().filter { it.documentId == documentId }.map { it.rowId }
            .chunked(500).forEach { searchDao.deleteRows(it) }
    }

    /** Margin stroke ids per lecture. */
    suspend fun marginStrokeIds(): Map<String, List<String>> =
        strokeDao.marginStrokeIds().groupBy({ it.lectureId }, { it.strokeId })

    /** The stroke hash each lecture's handwriting index was built from. */
    suspend fun inkIndexHashes(): Map<String, String> =
        searchDao.inkStates().associate { it.lectureId to it.strokesHash }

    /** Swaps a lecture's recognised handwriting for [blocks] and records [hash] as current. */
    suspend fun replaceInkText(lectureId: String, hash: String, blocks: List<InkBlockText>) {
        deleteInkText(lectureId)
        blocks.forEach { searchDao.insertInk(lectureId, it.blockKey, it.page, it.text) }
        searchDao.putInkState(InkIndexStateEntity(lectureId, hash, now()))
    }

    private suspend fun deleteInkText(lectureId: String) {
        searchDao.inkRows().filter { it.lectureId == lectureId }.map { it.rowId }
            .chunked(500).forEach { searchDao.deleteInkRows(it) }
    }

    suspend fun beginIndexing(documentId: String) {
        searchDao.clearIndexed(documentId)
        deletePages(documentId)
    }

    /** Marks every document for re-indexing; each one's old pages are replaced as it is redone. */
    suspend fun invalidateIndex() = searchDao.clearAllIndexed()

    suspend fun finishIndexing(documentId: String) =
        searchDao.markIndexed(IndexedDocumentEntity(documentId, now()))

    private suspend fun dropIndex(documentId: String) {
        deletePages(documentId)
        searchDao.clearIndexed(documentId)
    }

    suspend fun search(query: String, includeInk: Boolean = true): SearchResults {
        val like = likePattern(query)
        val match = ftsQuery(query)
        return SearchResults(
            ink = if (includeInk) match?.let { searchDao.searchInk(it, SEARCH_PAGES) }.orEmpty() else emptyList(),
            documents = like?.let {
                (searchDao.searchTitles(it, SEARCH_DOCS) + searchDao.searchTagged(it, SEARCH_DOCS))
                    .distinctBy { hit -> hit.lectureId }
            }.orEmpty(),
            pages = match?.let { searchDao.searchPages(it, SEARCH_PAGES) }.orEmpty(),
            highlights = like?.let { highlightDao.search(it, SEARCH_HIGHLIGHTS) }.orEmpty(),
        )
    }

    fun observeAnchors(lectureId: String): Flow<List<AnchorEntity>> =
        anchorDao.observeByLecture(lectureId)

    suspend fun createAnchor(
        lectureId: String,
        documentId: String,
        pdfPage: Int,
        pageXFraction: Float,
        pageYFraction: Float,
    ): AnchorEntity {
        val anchor = AnchorEntity(
            id = newId(),
            lectureId = lectureId,
            documentId = documentId,
            pdfPage = pdfPage,
            pageXFraction = pageXFraction,
            pageYFraction = pageYFraction,
            label = anchorDao.countByLecture(lectureId) + 1,
            createdAt = now(),
        )
        anchorDao.insert(anchor)
        return anchor
    }

    suspend fun deleteAnchor(id: String) = anchorDao.deleteById(id)

    /** Removes an anchor and unbinds its strokes; returns the ids that were bound. */
    suspend fun removeAnchorAndUnbind(id: String): List<String> {
        val bound = strokeDao.idsBoundTo(id)
        strokeDao.unbindAnchor(id)
        anchorDao.deleteById(id)
        return bound
    }

    /** Restores a removed anchor and rebinds the strokes that pointed at it. */
    suspend fun restoreAnchor(anchor: AnchorEntity, strokeIds: List<String>) {
        anchorDao.insert(anchor)
        if (strokeIds.isNotEmpty()) strokeDao.bindToAnchor(anchor.id, strokeIds)
    }

    suspend fun loadStrokes(lectureId: String): List<InkStroke> =
        strokeDao.getByLecture(lectureId).map { it.toInkStroke() }

    private companion object {
        const val SEARCH_DOCS = 20
        const val SEARCH_PAGES = 50
        const val SEARCH_HIGHLIGHTS = 30
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private fun now(): Long = System.currentTimeMillis()
}

/** Recognised handwriting of one block; [page] is the PDF page it was written beside, if any. */
data class InkBlockText(val blockKey: String, val page: Int?, val text: String)

data class SearchResults(
    val documents: List<TitleHit> = emptyList(),
    val pages: List<PageHit> = emptyList(),
    val highlights: List<HighlightRow> = emptyList(),
    val ink: List<InkHit> = emptyList(),
) {
    val isEmpty: Boolean get() = documents.isEmpty() && pages.isEmpty() && highlights.isEmpty() && ink.isEmpty()
}
