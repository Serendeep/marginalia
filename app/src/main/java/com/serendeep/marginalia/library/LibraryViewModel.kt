package com.serendeep.marginalia.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import com.serendeep.marginalia.data.CourseEntity
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.search.TextIndexer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MarginaliaRepository,
    val imageLoader: ImageLoader,
    private val indexer: TextIndexer,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val importer = PdfImporter(context, repository)

    /** Null until the first load, so the screen never flashes its empty state. */
    val shelf: StateFlow<ShelfData?> = repository.observeShelf()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Bumps once per successful import batch; the screen celebrates it. */
    private val _celebration = MutableStateFlow(0)
    val celebration: StateFlow<Int> = _celebration.asStateFlow()

    fun createCourse(name: String, colorIndex: Int, emoji: String?) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.createCourse(name.trim(), colorIndex, emoji) }
    }

    /** Creates a blank notebook in the shared unsorted section. */
    fun createNotebook(title: String, onCreated: (String) -> Unit = {}) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val lecture = repository.createLecture(unsortedCourse().id, title.trim())
            onCreated(lecture.id)
        }
    }

    fun importPdf(lectureId: String, uri: Uri) {
        viewModelScope.launch {
            when (val result = importer.import(lectureId, uri)) {
                is PdfImporter.Result.Success -> {
                    _error.value = null
                    indexer.schedule()
                }
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
            if (imported > 0) {
                _celebration.value += 1
                indexer.schedule()
            }
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
