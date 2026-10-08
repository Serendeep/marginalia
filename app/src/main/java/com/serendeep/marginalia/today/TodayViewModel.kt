package com.serendeep.marginalia.today

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
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

@HiltViewModel
class TodayViewModel @Inject constructor(
    repository: MarginaliaRepository,
    private val focusTimer: FocusTimer,
    val imageLoader: ImageLoader,
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
}
