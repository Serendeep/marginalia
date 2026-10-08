package com.serendeep.marginalia.search

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serendeep.marginalia.data.HighlightRow
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.data.SnippetSpan
import com.serendeep.marginalia.data.markTerms
import com.serendeep.marginalia.data.parseSnippet
import com.serendeep.marginalia.data.pushRecent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Immutable
data class DocumentResult(val key: String, val lectureId: String, val spans: List<SnippetSpan>)

@Immutable
data class PageResult(
    val key: String,
    val lectureId: String,
    val title: String,
    val page: Int,
    val spans: List<SnippetSpan>,
)

@Immutable
data class HighlightResult(val key: String, val row: HighlightRow, val spans: List<SnippetSpan>)

@Immutable
data class SearchUi(
    val query: String = "",
    val documents: List<DocumentResult> = emptyList(),
    val pages: List<PageResult> = emptyList(),
    val highlights: List<HighlightResult> = emptyList(),
) {
    val isEmpty: Boolean get() = documents.isEmpty() && pages.isEmpty() && highlights.isEmpty()
}

private const val PREFS = "marginalia"
private const val RECENT_KEY = "recent_searches"
private const val DEBOUNCE_MS = 200L

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MarginaliaRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _recent = MutableStateFlow<List<String>>(emptyList())
    val recent: StateFlow<List<String>> = _recent

    /** Null while the query is blank, so the screen shows recent searches instead. */
    val results: StateFlow<SearchUi?> = _query
        .debounce { if (it.isBlank()) 0L else DEBOUNCE_MS }
        .distinctUntilChanged()
        .mapLatest { q -> if (q.isBlank()) null else buildUi(q.trim()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            _recent.value = withContext(Dispatchers.IO) {
                prefs().getString(RECENT_KEY, "").orEmpty().split('\n').filter { it.isNotBlank() }
            }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    /** Remembers the current query once the user acts on it. */
    fun commit() {
        val next = pushRecent(_recent.value, _query.value)
        if (next == _recent.value) return
        _recent.value = next
        viewModelScope.launch(Dispatchers.IO) {
            prefs().edit().putString(RECENT_KEY, next.joinToString("\n")).apply()
        }
    }

    private suspend fun buildUi(q: String): SearchUi {
        val found = repository.search(q)
        return SearchUi(
            query = q,
            documents = found.documents.map { DocumentResult("d:${it.lectureId}", it.lectureId, markTerms(it.title, q)) },
            pages = found.pages.map {
                PageResult("p:${it.lectureId}:${it.page}", it.lectureId, it.title, it.page, parseSnippet(it.snippet))
            },
            highlights = found.highlights.map { HighlightResult("h:${it.highlight.id}", it, markTerms(it.highlight.text, q)) },
        )
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
