package com.serendeep.marginalia.today

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import coil3.ImageLoader
import com.serendeep.marginalia.ai.AiEvent
import com.serendeep.marginalia.ai.AiRequest
import com.serendeep.marginalia.ai.AiRouter
import com.serendeep.marginalia.ai.AiSettings
import com.serendeep.marginalia.ai.ChatGptAuth
import com.serendeep.marginalia.ai.DigestStore
import com.serendeep.marginalia.ai.Prompts
import com.serendeep.marginalia.ai.dayKey
import com.serendeep.marginalia.ai.digestInput
import com.serendeep.marginalia.ai.ui.aiReady
import com.serendeep.marginalia.update.RemoteConfigStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.serendeep.marginalia.data.HighlightRow
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.data.ReadingStatus
import com.serendeep.marginalia.library.RowModel
import com.serendeep.marginalia.library.observeShelf
import com.serendeep.marginalia.study.DueSplit
import com.serendeep.marginalia.study.FocusState
import com.serendeep.marginalia.study.FocusTimer
import com.serendeep.marginalia.study.bestStreak
import com.serendeep.marginalia.study.minutesByDay
import com.serendeep.marginalia.study.dueSplit
import com.serendeep.marginalia.study.estimateMinutes
import com.serendeep.marginalia.study.observeDue
import com.serendeep.marginalia.study.observeReviewActivity
import com.serendeep.marginalia.study.streakDays
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

const val HEAT_DAYS = 42
private const val RECENT_HIGHLIGHTS = 4

@Immutable
data class NextUp(val title: String, val page: Int)

@Immutable
data class CourseDue(val name: String, val colorIndex: Int, val due: Int)

@Immutable
data class ReviewTileState(
    val due: Int = 0,
    val split: DueSplit = DueSplit(0, 0, 0, 0),
    val courses: List<CourseDue> = emptyList(),
    val reviewedToday: Int = 0,
    val hasCards: Boolean = false,
) {
    val minutes: Int get() = estimateMinutes(due)
    val progress: Float get() = if (reviewedToday + due == 0) 0f else reviewedToday.toFloat() / (reviewedToday + due)
}

@Immutable
data class TodayState(
    val loaded: Boolean = false,
    val hasLectures: Boolean = false,
    val continueRows: List<RowModel> = emptyList(),
    val toRead: List<RowModel> = emptyList(),
    val toReadCount: Int = 0,
    val next: NextUp? = null,
    val streak: Int = 0,
    val best: Int = 0,
    val retention: Int? = null,
    val review: ReviewTileState = ReviewTileState(),
    // Oldest first, the last cell is today; 0 none, 1 >= 10 min, 2 >= 30, 3 >= 60.
    val heat: List<Int> = emptyList(),
    val highlights: List<HighlightRow> = emptyList(),
)

sealed interface DigestUi {
    data object Hidden : DigestUi
    data object Writing : DigestUi
    data class Ready(val text: String) : DigestUi
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: MarginaliaRepository,
    private val focusTimer: FocusTimer,
    val imageLoader: ImageLoader,
    private val digestStore: DigestStore,
    private val router: AiRouter,
    private val settings: AiSettings,
    private val auth: ChatGptAuth,
    private val remote: RemoteConfigStore,
) : ViewModel() {

    private val reviewInputs = combine(repository.observeDue(), repository.observeReviewActivity()) { due, activity ->
        due to activity
    }

    val state: StateFlow<TodayState> = combine(
        repository.observeShelf(),
        repository.observeSessions(),
        repository.observeRecentHighlights(RECENT_HIGHLIGHTS),
        reviewInputs,
    ) { shelf, sessions, highlights, (due, activity) ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val minutes = minutesByDay(sessions, zone)
        val courseOfLecture = shelf.rows.associate { it.lecture.id to it.course }
        val byCourse = due.queue.groupBy { courseOfLecture[it.lectureId]?.name ?: "" }
            .map { (name, cards) ->
                val course = cards.firstNotNullOfOrNull { courseOfLecture[it.lectureId] }
                CourseDue(name.ifEmpty { "General" }, course?.colorIndex ?: 0, cards.size)
            }
            .sortedByDescending { it.due }
            .take(3)
        val reading = shelf.rows.filter { it.status == ReadingStatus.READING }.sortedByDescending { it.touchedAt }
        val toRead = shelf.rows.filter { it.status == ReadingStatus.TO_READ }.sortedByDescending { it.touchedAt }
        val last = shelf.rows.filter { it.lecture.lastOpenedAt != null }.maxByOrNull { it.lecture.lastOpenedAt!! }
            ?: reading.firstOrNull()
        TodayState(
            loaded = true,
            hasLectures = shelf.rows.isNotEmpty(),
            continueRows = reading.take(4),
            toRead = toRead.take(3),
            toReadCount = toRead.size,
            next = last?.let { NextUp(it.lecture.title, it.lecture.lastPage + 1) },
            highlights = highlights,
            streak = streakDays(minutes, activity.byDay, today),
            best = bestStreak(minutes, activity.byDay),
            retention = activity.retentionPct,
            review = ReviewTileState(
                due = due.queue.size,
                split = dueSplit(due.queue),
                courses = byCourse,
                reviewedToday = activity.byDay[today] ?: 0,
                hasCards = due.totalCards > 0,
            ),
            heat = List(HEAT_DAYS) { i ->
                val m = minutes[today.minusDays((HEAT_DAYS - 1 - i).toLong())] ?: 0
                when {
                    m >= 60 -> 3
                    m >= 30 -> 2
                    m >= 10 -> 1
                    else -> 0
                }
            },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    val focus: StateFlow<FocusState> = focusTimer.state

    fun toggleFocus() = focusTimer.toggle()

    private val _digest = MutableStateFlow<DigestUi>(DigestUi.Hidden)
    val digest: StateFlow<DigestUi> = _digest.asStateFlow()

    /**
     * Shows today's digest of yesterday, writing it first when it is switched on, the AI is ready and yesterday had activity.
     * Runs only while Today is on screen; leaving cancels a write in progress.
     */
    suspend fun loadDigest() {
        val today = LocalDate.now()
        val key = dayKey(today)
        val cache = digestStore.cache()
        val shown = cache.visible(key)
        if (!digestStore.enabled.value || shown == null && !cache.needed(key)) {
            _digest.value = DigestUi.Hidden
            return
        }
        if (shown != null) {
            _digest.value = DigestUi.Ready(shown)
            return
        }
        if (!aiReady(settings.config.value, auth.status.value, remote.config.value.aiAllowed)) return
        try {
            val input = repository.digestInput(today.minusDays(1), System.currentTimeMillis()) ?: return
            _digest.value = DigestUi.Writing
            val text = write(Prompts.digest(input))
            if (text == null) {
                _digest.value = DigestUi.Hidden
                return
            }
            digestStore.save(digestStore.cache().with(key, text))
            _digest.value = DigestUi.Ready(text)
        } catch (e: CancellationException) {
            _digest.value = DigestUi.Hidden
            throw e
        }
    }

    fun dismissDigest() {
        digestStore.save(digestStore.cache().dismissed(dayKey(LocalDate.now())))
        _digest.value = DigestUi.Hidden
    }

    private suspend fun write(request: AiRequest): String? {
        val text = StringBuilder()
        var done = false
        try {
            router.active().stream(request).collect { event ->
                when (event) {
                    is AiEvent.Delta -> text.append(event.text)
                    AiEvent.Completed -> done = true
                    is AiEvent.Failed -> Log.w(TAG, "digest failed: ${event.error.kind}")
                    else -> Unit
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "digest failed: ${e.javaClass.simpleName}")
        }
        return if (done) text.toString().trim().ifEmpty { null } else null
    }

    private companion object {
        const val TAG = "Digest"
    }
}
