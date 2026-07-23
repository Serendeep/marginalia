package com.serendeep.marginalia.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import com.serendeep.marginalia.data.CourseEntity
import com.serendeep.marginalia.data.DocumentEntity
import com.serendeep.marginalia.data.LectureEntity
import com.serendeep.marginalia.data.MarginaliaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** One notebook on the shelf: a lecture plus its newest readable document. */
data class ShelfItem(
    val lecture: LectureEntity,
    val document: DocumentEntity?,
    val lastWrittenAt: Long? = null,
)

/** A shelf section; [course] is null for quick-imported, ungrouped notebooks. */
data class ShelfSection(
    val course: CourseEntity?,
    val items: List<ShelfItem>,
)

/** The library: the notebook to continue in, plus everything else. */
data class Shelf(
    val hero: ShelfItem? = null,
    val sections: List<ShelfSection> = emptyList(),
) {
    val isEmpty: Boolean get() = hero == null && sections.isEmpty()
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MarginaliaRepository,
    val imageLoader: ImageLoader,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val importer = PdfImporter(context, repository)

    val shelf: StateFlow<Shelf> = combine(
        repository.observeCourses(),
        repository.observeAllLectures(),
        repository.observeAllDocuments(),
        repository.observeLastWritten(),
    ) { courses, lectures, documents, touches ->
        val latestByLecture = documents
            .filter { it.localPath.isNotEmpty() && File(it.localPath).exists() }
            .groupBy { it.lectureId }
            .mapValues { (_, versions) -> versions.maxBy { it.versionIndex } }
        val touchByLecture = touches.associate { it.lectureId to it.lastAt }
        val byCourse = lectures.groupBy { it.courseId }
        fun items(courseId: String) = byCourse[courseId].orEmpty()
            .map { ShelfItem(it, latestByLecture[it.id], touchByLecture[it.id]) }

        val all = lectures.map { ShelfItem(it, latestByLecture[it.id], touchByLecture[it.id]) }
        val hero = all
            .filter { it.document != null }
            .maxByOrNull { it.lastWrittenAt ?: it.document!!.importedAt }

        val unsorted = courses.firstOrNull { it.name == UNSORTED_NAME }
        val sections = buildList {
            unsorted?.let {
                val rest = items(it.id).filterNot { item -> item.lecture.id == hero?.lecture?.id }
                if (rest.isNotEmpty()) add(ShelfSection(null, rest))
            }
            courses.filterNot { it.id == unsorted?.id }.forEach { course ->
                add(ShelfSection(course, items(course.id).filterNot { item -> item.lecture.id == hero?.lecture?.id }))
            }
        }
        Shelf(hero, sections)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Shelf())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Bumps once per successful import batch; the screen celebrates it. */
    private val _celebration = MutableStateFlow(0)
    val celebration: StateFlow<Int> = _celebration.asStateFlow()

    fun createCourse(name: String, colorIndex: Int, emoji: String?) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.createCourse(name.trim(), colorIndex, emoji) }
    }

    fun importPdf(lectureId: String, uri: Uri) {
        viewModelScope.launch {
            when (val result = importer.import(lectureId, uri)) {
                is PdfImporter.Result.Success -> _error.value = null
                is PdfImporter.Result.Failure -> _error.value = result.message
            }
        }
    }

    /**
     * One-tap import: each PDF becomes its own lecture titled after the file.
     * With no [courseId] the lectures land in the shared unsorted course, which
     * is created on first use. A failed PDF leaves no empty lecture behind.
     */
    fun quickImport(uris: List<Uri>, courseId: String? = null) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val target = courseId ?: unsortedCourse().id
            var imported = 0
            for (uri in uris) {
                val title = importer.displayName(uri).removeSuffix(".pdf").ifBlank { "Untitled" }
                val lecture = repository.createLecture(target, title)
                when (val result = importer.import(lecture.id, uri)) {
                    is PdfImporter.Result.Success -> {
                        _error.value = null
                        imported++
                    }

                    is PdfImporter.Result.Failure -> {
                        repository.deleteLecture(lecture)
                        _error.value = result.message
                    }
                }
            }
            if (imported > 0) _celebration.value += 1
        }
    }

    private suspend fun unsortedCourse(): CourseEntity =
        repository.observeCourses().first().firstOrNull { it.name == UNSORTED_NAME }
            ?: repository.createCourse(UNSORTED_NAME, colorIndex = 0, emoji = null)

    fun renameLecture(lectureId: String, title: String) {
        viewModelScope.launch { repository.renameLecture(lectureId, title) }
    }

    fun moveLecture(lectureId: String, courseId: String) {
        viewModelScope.launch { repository.moveLecture(lectureId, courseId) }
    }

    fun deleteLecture(lectureId: String) {
        viewModelScope.launch { repository.deleteLecture(lectureId) }
    }

    fun dismissError() {
        _error.value = null
    }

    companion object {
        const val UNSORTED_NAME = "Unsorted"
    }
}
