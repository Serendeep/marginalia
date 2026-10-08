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
import com.serendeep.marginalia.study.FocusState
import com.serendeep.marginalia.study.FocusTimer
import com.serendeep.marginalia.study.bestStreak
import com.serendeep.marginalia.study.minutesByDay
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
data class TodayState(
    val loaded: Boolean = false,
    val hasLectures: Boolean = false,
    val continueRows: List<RowModel> = emptyList(),
    val toRead: List<RowModel> = emptyList(),
    val toReadCount: Int = 0,
    val next: NextUp? = null,
    val streak: Int = 0,
    val best: Int = 0,
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

    val state: StateFlow<TodayState> = combine(
        repository.observeShelf(),
        repository.observeSessions(),
        repository.observeRecentHighlights(RECENT_HIGHLIGHTS),
    ) { shelf, sessions, highlights ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val minutes = minutesByDay(sessions, zone)
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
            streak = streakDays(minutes, emptyMap(), today),
            best = bestStreak(minutes, emptyMap()),
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
