package com.serendeep.marginalia.shell

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.data.ReadingStatus
import com.serendeep.marginalia.library.observeShelf
import com.serendeep.marginalia.study.minutesByDay
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@Immutable
data class CourseNav(val id: String, val name: String, val colorIndex: Int, val count: Int)

@Immutable
data class SidebarState(
    val libraryCount: Int = 0,
    val reviewDue: Int = 0,
    val highlights: Int = 0,
    val courses: List<CourseNav> = emptyList(),
    val toRead: Int = 0,
    val reading: Int = 0,
    val done: Int = 0,
)

const val DEFAULT_GOAL_MIN = 60
private const val PREFS = "marginalia"
private const val GOAL_KEY = "daily_goal_min"

@HiltViewModel
class ShellViewModel @Inject constructor(
    repository: MarginaliaRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val sidebar: StateFlow<SidebarState> = combine(
        repository.observeShelf(),
        repository.observeHighlightCount(),
    ) { data, highlightCount ->
        val byCourse = data.rows.groupingBy { it.lecture.courseId }.eachCount()
        SidebarState(
            libraryCount = data.rows.size,
            highlights = highlightCount,
            courses = data.courses.map { CourseNav(it.id, it.name, it.colorIndex, byCourse[it.id] ?: 0) },
            toRead = data.rows.count { it.status == ReadingStatus.TO_READ },
            reading = data.rows.count { it.status == ReadingStatus.READING },
            done = data.rows.count { it.status == ReadingStatus.DONE },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SidebarState())

    val minutesToday: StateFlow<Int> = repository.observeSessions().map { sessions ->
        val zone = ZoneId.systemDefault()
        minutesByDay(sessions, zone)[LocalDate.now(zone)] ?: 0
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _goalMin = MutableStateFlow(DEFAULT_GOAL_MIN)
    val goalMin: StateFlow<Int> = _goalMin.asStateFlow()

    init {
        viewModelScope.launch {
            _goalMin.value = withContext(Dispatchers.IO) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(GOAL_KEY, DEFAULT_GOAL_MIN)
            }
        }
    }

    fun setGoal(minutes: Int) {
        _goalMin.value = minutes
        viewModelScope.launch(Dispatchers.IO) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(GOAL_KEY, minutes).apply()
        }
    }
}
