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
) {
    fun observeCourses(): Flow<List<CourseEntity>> = courseDao.observeAll()

    fun observeLectures(courseId: String): Flow<List<LectureEntity>> =
        lectureDao.observeByCourse(courseId)

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

    suspend fun saveStroke(stroke: InkStroke) = strokeDao.insert(stroke.toEntity())

    suspend fun loadStrokes(lectureId: String): List<InkStroke> =
        strokeDao.getByLecture(lectureId).map { it.toInkStroke() }

    private fun newId(): String = UUID.randomUUID().toString()

    private fun now(): Long = System.currentTimeMillis()
}
