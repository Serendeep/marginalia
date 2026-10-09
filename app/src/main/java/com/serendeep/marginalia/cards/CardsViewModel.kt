package com.serendeep.marginalia.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import com.serendeep.marginalia.data.CardSource
import com.serendeep.marginalia.data.HighlightRow
import com.serendeep.marginalia.data.LectureEntity
import com.serendeep.marginalia.data.MarginaliaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Creates cards that don't come from the notebook lasso. */
@HiltViewModel
class CardsViewModel @Inject constructor(
    private val repository: MarginaliaRepository,
    val imageLoader: ImageLoader,
) : ViewModel() {

    val lectures: StateFlow<List<LectureEntity>> = repository.observeAllLectures()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveHighlightCard(row: HighlightRow, front: String, back: String, backInk: ByteArray?) {
        val h = row.highlight
        viewModelScope.launch(Dispatchers.IO) {
            repository.createCard(
                source = CardSource.HIGHLIGHT,
                lectureId = h.lectureId,
                documentId = h.documentId,
                page = h.page,
                frontText = front,
                backText = back,
                backInk = backInk,
                highlightId = h.id,
            )
        }
    }

    fun saveTyped(front: String, back: String, lectureId: String?, backInk: ByteArray?) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.createCard(
                source = CardSource.TYPED,
                lectureId = lectureId,
                frontText = front,
                backText = back,
                backInk = backInk,
            )
        }
    }
}
