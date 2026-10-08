package com.serendeep.marginalia.stats

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.study.bestStreak
import com.serendeep.marginalia.study.minutesByDay
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

@Immutable
data class CourseBar(val name: String, val colorIndex: Int, val minutes: Int)

@Immutable
data class StatsState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    // Oldest first; the last entry is today.
    val focus: List<Int> = List(FOCUS_DAYS) { 0 },
    val courses: List<CourseBar> = emptyList(),
    val reviewed7: Int = 0,
    val retention: Int? = null,
    val dueTomorrow: Int = 0,
    val streak: Int = 0,
    val best: Int = 0,
    val totalMinutes: Int = 0,
)

@HiltViewModel
class StatsViewModel @Inject constructor(repository: MarginaliaRepository) : ViewModel() {

    val state: StateFlow<StatsState> = combine(
        repository.observeSessions(),
        repository.observeCourses(),
        repository.observeAllLectures(),
        repository.observeCards(),
        repository.observeReviewActivity(),
    ) { sessions, courses, lectures, cards, activity ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val minutes = minutesByDay(sessions, zone)
        val courseById = courses.associateBy { it.id }
        val perCourse = courseMinutes(sessions, lectures.associate { it.id to it.courseId }, today, zone)
        StatsState(
            loaded = true,
            today = today,
            focus = lastDays(minutes, today),
            courses = perCourse.map { (id, min) ->
                val course = courseById[id]
                CourseBar(course?.name ?: "Other", course?.colorIndex ?: 0, min)
            }.sortedByDescending { it.minutes },
            reviewed7 = reviewedSince(activity.byDay, today),
            retention = activity.retentionPct,
            dueTomorrow = dueTomorrow(cards, today, zone),
            streak = streakDays(minutes, activity.byDay, today),
            best = bestStreak(minutes, activity.byDay),
            totalMinutes = minutes.values.sum(),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())
}
