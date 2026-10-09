package com.serendeep.marginalia.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import com.serendeep.marginalia.ai.AiSettings
import com.serendeep.marginalia.ai.AutoSorter
import com.serendeep.marginalia.ai.ChatGptAuth
import com.serendeep.marginalia.ai.SortResult
import com.serendeep.marginalia.ai.sortReadyFlow
import com.serendeep.marginalia.data.CourseEntity
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.research.Citation
import com.serendeep.marginalia.research.CitationService
import com.serendeep.marginalia.search.TextIndexer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MarginaliaRepository,
    val imageLoader: ImageLoader,
    private val indexer: TextIndexer,
    private val citations: CitationService,
    private val sorter: AutoSorter,
    settings: AiSettings,
    auth: ChatGptAuth,
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

    /** True while a provider is connected and sorting can run. */
    val canSort: StateFlow<Boolean> = sortReadyFlow(settings, auth)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** (done, total) while a manual sort runs. */
    private val _sorting = MutableStateFlow<Pair<Int, Int>?>(null)
    val sorting: StateFlow<Pair<Int, Int>?> = _sorting.asStateFlow()

    private val _batches = Channel<List<SortResult>>(Channel.BUFFERED)

    /** Each automatic sort arrives alone; a manual run arrives as one batch. */
    val sorted: Flow<List<SortResult>> = merge(sorter.results.map { listOf(it) }, _batches.receiveAsFlow())

    fun sortUnsorted() {
        if (_sorting.value != null) return
        viewModelScope.launch {
            val unsorted = repository.courses().firstOrNull { it.name == UNSORTED_NAME } ?: return@launch
            val ids = repository.observeLectures(unsorted.id).first().map { it.id }
            val results = ArrayList<SortResult>()
            try {
                ids.forEachIndexed { i, id ->
                    _sorting.value = i + 1 to ids.size
                    sorter.sort(id, force = true)?.let(results::add)
                }
            } finally {
                _sorting.value = null
            }
            if (results.isEmpty()) _error.value = "Couldn't sort with AI" else _batches.send(results)
        }
    }

    fun undoSort(results: List<SortResult>) {
        viewModelScope.launch { results.asReversed().forEach { sorter.undo(it) } }
    }

    private val _citation = Channel<Citation>(Channel.BUFFERED)

    /** One event per "Copy citation" request, once the text is ready to put on the clipboard. */
    val citation: Flow<Citation> = _citation.receiveAsFlow()

    fun copyCitation(lectureId: String) {
        viewModelScope.launch { _citation.send(citations.citationFor(lectureId)) }
    }

    fun addTag(lectureId: String, name: String) {
        viewModelScope.launch { repository.addTag(lectureId, name) }
    }

    fun setTagged(lectureId: String, tagId: String, tagged: Boolean) {
        viewModelScope.launch { repository.setTagged(lectureId, tagId, tagged) }
    }

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

    fun reorderLectures(ids: List<String>) {
        viewModelScope.launch { repository.reorderLectures(ids) }
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
