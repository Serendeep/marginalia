package com.serendeep.marginalia.review

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import com.serendeep.marginalia.data.CardEntity
import com.serendeep.marginalia.data.CardState
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.data.SessionKind
import com.serendeep.marginalia.study.Grade
import com.serendeep.marginalia.study.Scheduler
import com.serendeep.marginalia.study.dueQueue
import com.serendeep.marginalia.study.startOfDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

@Immutable
data class ReviewCard(
    val id: String,
    val frontText: String?,
    val frontImagePath: String?,
    val backText: String?,
    val lectureId: String?,
    val page: Int?,
    // "LECTURE 7 · P.14"; null when the card isn't tied to a lecture.
    val source: String?,
    // Next-interval captions in Grade order.
    val labels: List<String>,
)

@Immutable
sealed interface ReviewUi {
    data object Loading : ReviewUi
    data object Empty : ReviewUi
    data class Card(val card: ReviewCard, val remaining: Int) : ReviewUi
    data class Summary(val reviewed: Int, val goodPct: Int, val seconds: Int) : ReviewUi
}

// Learning cards due within this window of an answer stay in the session instead of waiting for the next one.
private const val LEARN_AHEAD_MS = 20 * 60_000L

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repository: MarginaliaRepository,
    val imageLoader: ImageLoader,
) : ViewModel() {

    private val _ui = MutableStateFlow<ReviewUi>(ReviewUi.Loading)
    val ui: StateFlow<ReviewUi> = _ui.asStateFlow()

    private val queue = ArrayDeque<CardEntity>()
    private var current: CardEntity? = null
    private var titles = emptyMap<String, String>()
    private var startedAt = 0L
    private var reviewed = 0
    private var good = 0
    private var logged = true

    /** Begins a session from whatever is due now. */
    fun start() {
        viewModelScope.launch {
            _ui.value = ReviewUi.Loading
            val now = System.currentTimeMillis()
            val loaded = withContext(Dispatchers.IO) {
                val cards = repository.allCards()
                val introduced = repository.newIntroducedSince(startOfDay(now))
                val lectureTitles = repository.observeAllLectures().first().associate { it.id to it.title }
                dueQueue(cards, now, introduced) to lectureTitles
            }
            queue.clear()
            queue.addAll(loaded.first)
            titles = loaded.second
            startedAt = now
            reviewed = 0
            good = 0
            logged = false
            advance()
        }
    }

    /** Picks up a card created while the screen was idle. */
    fun reloadIfEmpty() {
        if (_ui.value == ReviewUi.Empty) start()
    }

    fun grade(grade: Grade) {
        val card = current ?: return
        val now = System.currentTimeMillis()
        val updated = Scheduler.next(card, grade, now)
        reviewed++
        if (grade == Grade.GOOD || grade == Grade.EASY) good++
        val learning = updated.cardState == CardState.LEARNING || updated.cardState == CardState.RELEARNING
        if (learning && updated.dueAt - now <= LEARN_AHEAD_MS) queue.addLast(updated)
        viewModelScope.launch(Dispatchers.IO) { repository.saveGrade(updated, card, grade.ordinal, now) }
        advance()
    }

    /** Logs the time on screen as a review session; safe to call more than once. */
    fun endSession() {
        if (logged || reviewed == 0) return
        logged = true
        val from = startedAt
        val to = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) { repository.saveSession(null, SessionKind.REVIEW, from, to) }
    }

    private fun advance() {
        val next = queue.removeFirstOrNull()
        current = next
        _ui.value = when {
            next != null -> ReviewUi.Card(toUi(next), queue.size + 1)
            reviewed == 0 -> ReviewUi.Empty
            else -> {
                endSession()
                ReviewUi.Summary(
                    reviewed = reviewed,
                    goodPct = good * 100 / reviewed,
                    seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt(),
                )
            }
        }
    }

    private fun toUi(card: CardEntity): ReviewCard {
        val now = System.currentTimeMillis()
        val title = card.lectureId?.let { titles[it] }
        return ReviewCard(
            id = card.id,
            frontText = card.frontText,
            frontImagePath = card.frontImagePath,
            backText = card.backText,
            lectureId = card.lectureId,
            page = card.page,
            source = title?.let { t ->
                t.uppercase(Locale.ROOT) + (card.page?.let { " · P.${it + 1}" } ?: "")
            },
            labels = Grade.entries.map { Scheduler.label(card, it, now) },
        )
    }
}
