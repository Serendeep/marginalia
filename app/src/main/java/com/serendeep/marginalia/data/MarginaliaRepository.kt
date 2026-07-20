package com.serendeep.marginalia.data

import kotlinx.coroutines.flow.Flow
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
) {
    fun observeCourses(): Flow<List<CourseEntity>> = courseDao.observeAll()

    fun observeLectures(courseId: String): Flow<List<LectureEntity>> =
        lectureDao.observeByCourse(courseId)

    fun observeAllLectures(): Flow<List<LectureEntity>> = lectureDao.observeAll()

    fun observeAllDocuments(): Flow<List<DocumentEntity>> = documentDao.observeAll()

    fun observeLastWritten(): Flow<List<LectureTouch>> = strokeDao.observeLastWritten()

    fun observeDocuments(lectureId: String): Flow<List<DocumentEntity>> =
        documentDao.observeByLecture(lectureId)

    fun observeStrokes(lectureId: String): Flow<List<StrokeEntity>> =
        strokeDao.observeByLecture(lectureId)

    suspend fun createCourse(name: String): CourseEntity {
        val course = CourseEntity(id = newId(), name = name, createdAt = now(), orderIndex = now())
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

    suspend fun deleteLecture(lecture: LectureEntity) = lectureDao.delete(lecture)

    suspend fun saveStroke(stroke: InkStroke) = strokeDao.insert(stroke.toEntity())

    suspend fun saveStrokes(strokes: List<InkStroke>) = strokeDao.insertAll(strokes.map { it.toEntity() })

    suspend fun deleteStroke(id: String) = strokeDao.deleteById(id)

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

    private fun newId(): String = UUID.randomUUID().toString()

    private fun now(): Long = System.currentTimeMillis()
}
