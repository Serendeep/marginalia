package com.serendeep.marginalia.highlights

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serendeep.marginalia.data.HighlightRow
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.data.MarkdownHighlight
import com.serendeep.marginalia.data.highlightsMarkdown
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class HighlightGroup(val lectureId: String, val title: String, val items: List<HighlightRow>)

@HiltViewModel
class HighlightsViewModel @Inject constructor(
    private val repository: MarginaliaRepository,
) : ViewModel() {

    /** Null until the first load, so the empty state never flashes. */
    val groups: StateFlow<List<HighlightGroup>?> = repository.observeHighlights()
        .map { rows ->
            rows.groupBy { it.highlight.lectureId }
                .map { (id, items) -> HighlightGroup(id, items.first().lectureTitle, items) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun markdown(group: HighlightGroup): String = highlightsMarkdown(
        group.title,
        group.items.map { MarkdownHighlight(it.highlight.page, it.highlight.text) },
        repository.anchorCount(group.lectureId),
        repository.getLecture(group.lectureId)?.bibtex,
    )
}
