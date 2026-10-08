package com.serendeep.marginalia.library

import androidx.compose.runtime.Immutable
import com.serendeep.marginalia.data.CourseEntity
import com.serendeep.marginalia.data.DocumentEntity
import com.serendeep.marginalia.data.LectureEntity
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.data.ReadingStatus
import com.serendeep.marginalia.data.TagEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.io.File

/** Which slice of the library a list shows. */
sealed interface LibraryFilter {
    data object All : LibraryFilter
    data class Course(val courseId: String) : LibraryFilter
    data class Status(val status: ReadingStatus) : LibraryFilter
    data class Tag(val tagId: String) : LibraryFilter
}

/** One notebook as the list screens see it. */
@Immutable
data class RowModel(
    val lecture: LectureEntity,
    val document: DocumentEntity?,
    val course: CourseEntity?,
    val lastWrittenAt: Long?,
    val tags: List<TagEntity> = emptyList(),
) {
    val status: ReadingStatus
        get() = runCatching { ReadingStatus.valueOf(lecture.readingStatus) }.getOrDefault(ReadingStatus.TO_READ)
    val touchedAt: Long get() = lecture.lastOpenedAt ?: lastWrittenAt ?: lecture.createdAt
    val colorIndex: Int get() = course?.colorIndex ?: 0
}

@Immutable
data class ShelfData(
    val courses: List<CourseEntity>,
    val rows: List<RowModel>,
    val tags: List<TagEntity> = emptyList(),
)

/** A shelf section; [course] is null for quick-imported, ungrouped notebooks. */
@Immutable
data class ShelfSection(val course: CourseEntity?, val items: List<RowModel>)

/** Everything the list screens need, built off the main thread (it checks files on disk). */
fun MarginaliaRepository.observeShelf(): Flow<ShelfData> = combine(
    observeCourses(),
    observeAllLectures(),
    observeAllDocuments(),
    observeLastWritten(),
    combine(observeTags(), observeTagLinks()) { tags, links -> tags to links },
) { courses, lectures, documents, touches, (tags, links) ->
    val latestByLecture = documents
        .filter { it.localPath.isNotEmpty() && File(it.localPath).exists() }
        .groupBy { it.lectureId }
        .mapValues { (_, versions) -> versions.maxBy { it.versionIndex } }
    val touchByLecture = touches.associate { it.lectureId to it.lastAt }
    val courseById = courses.associateBy { it.id }
    val tagById = tags.associateBy { it.id }
    val tagsByLecture = links.groupBy({ it.lectureId }, { tagById[it.tagId] })
        .mapValues { (_, list) -> list.filterNotNull().sortedBy { it.name.lowercase() } }
    ShelfData(
        courses,
        lectures.map {
            RowModel(it, latestByLecture[it.id], courseById[it.courseId], touchByLecture[it.id], tagsByLecture[it.id].orEmpty())
        },
        tags,
    )
}.flowOn(Dispatchers.IO)

fun ShelfData.sections(filter: LibraryFilter): List<ShelfSection> {
    val byCourse = rows.groupBy { it.lecture.courseId }
    val unsorted = courses.firstOrNull { it.name == LibraryViewModel.UNSORTED_NAME }
    fun section(course: CourseEntity): ShelfSection? {
        val items = byCourse[course.id].orEmpty().filter {
            when (filter) {
                is LibraryFilter.Status -> it.status == filter.status
                is LibraryFilter.Tag -> it.tags.any { t -> t.id == filter.tagId }
                else -> true
            }
        }
        val shown = when (filter) {
            LibraryFilter.All -> true
            is LibraryFilter.Course -> filter.courseId == course.id
            is LibraryFilter.Status, is LibraryFilter.Tag -> items.isNotEmpty()
        }
        if (!shown) return null
        // Ungrouped notebooks only get a section once they exist.
        if (course.id == unsorted?.id && items.isEmpty() && filter !is LibraryFilter.Course) return null
        return ShelfSection(if (course.id == unsorted?.id) null else course, items)
    }
    return buildList {
        unsorted?.let { section(it)?.let(::add) }
        courses.filterNot { it.id == unsorted?.id }.forEach { section(it)?.let(::add) }
    }
}
