package com.serendeep.marginalia.shell

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.data.ReadingStatus
import com.serendeep.marginalia.handwriting.HANDWRITING_SEARCH_KEY
import com.serendeep.marginalia.handwriting.InkIndexer
import com.serendeep.marginalia.handwriting.InkRecognizer
import com.serendeep.marginalia.handwriting.ModelState
import com.serendeep.marginalia.library.observeShelf
import com.serendeep.marginalia.reminder.DEFAULT_REMINDER_MIN
import com.serendeep.marginalia.reminder.REMINDER_ENABLED_KEY
import com.serendeep.marginalia.reminder.REMINDER_TIME_KEY
import com.serendeep.marginalia.reminder.ReminderScheduler
import com.serendeep.marginalia.study.minutesByDay
import com.serendeep.marginalia.study.observeDue
import com.serendeep.marginalia.ink.PenColors
import com.serendeep.marginalia.ink.PencilAction
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
data class TagNav(val id: String, val name: String, val count: Int)

@Immutable
data class SidebarState(
    val libraryCount: Int = 0,
    val reviewDue: Int = 0,
    val highlights: Int = 0,
    val courses: List<CourseNav> = emptyList(),
    val tags: List<TagNav> = emptyList(),
    val toRead: Int = 0,
    val reading: Int = 0,
    val done: Int = 0,
)

@Immutable
data class ReminderSettings(val enabled: Boolean = true, val minuteOfDay: Int = DEFAULT_REMINDER_MIN)

const val DEFAULT_GOAL_MIN = 60
const val PREFS = "marginalia"
const val GOAL_KEY = "daily_goal_min"

@HiltViewModel
class ShellViewModel @Inject constructor(
    repository: MarginaliaRepository,
    private val recognizer: InkRecognizer,
    private val inkIndexer: InkIndexer,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val sidebar: StateFlow<SidebarState> = combine(
        repository.observeShelf(),
        repository.observeHighlightCount(),
        repository.observeDue().map { it.queue.size },
    ) { data, highlightCount, due ->
        val byCourse = data.rows.groupingBy { it.lecture.courseId }.eachCount()
        SidebarState(
            libraryCount = data.rows.size,
            highlights = highlightCount,
            reviewDue = due,
            courses = data.courses.map { CourseNav(it.id, it.name, it.colorIndex, byCourse[it.id] ?: 0) },
            tags = data.tags
                .map { tag -> TagNav(tag.id, tag.name, data.rows.count { r -> r.tags.any { it.id == tag.id } }) }
                .filter { it.count > 0 },
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

    private val _reminder = MutableStateFlow(ReminderSettings())
    val reminder: StateFlow<ReminderSettings> = _reminder.asStateFlow()

    private val _pencilAction = MutableStateFlow(PencilAction.TOGGLE_ERASER)
    val pencilAction: StateFlow<PencilAction> = _pencilAction.asStateFlow()

    private val _handwritingSearch = MutableStateFlow(true)
    val handwritingSearch: StateFlow<Boolean> = _handwritingSearch.asStateFlow()

    val modelState: StateFlow<ModelState> = recognizer.state

    /** Downloads the handwriting model now (foreground, any network), then indexes what is waiting. */
    fun downloadModel() {
        viewModelScope.launch {
            if (recognizer.ensureModel()) inkIndexer.schedule()
        }
    }

    /** Looks the model up on the device so the settings line is accurate before the first conversion. */
    fun refreshModel() {
        viewModelScope.launch { recognizer.refresh() }
    }

    init {
        viewModelScope.launch {
            val prefs = withContext(Dispatchers.IO) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
            _pencilAction.value = PenColors.actionFrom(prefs.getString(PenColors.ACTION_KEY, null))
            _goalMin.value = prefs.getInt(GOAL_KEY, DEFAULT_GOAL_MIN)
            _handwritingSearch.value = prefs.getBoolean(HANDWRITING_SEARCH_KEY, true)
            _reminder.value = ReminderSettings(
                prefs.getBoolean(REMINDER_ENABLED_KEY, true),
                prefs.getInt(REMINDER_TIME_KEY, DEFAULT_REMINDER_MIN),
            )
        }
    }

    fun saveSettings(goalMin: Int, reminder: ReminderSettings, pencilAction: PencilAction, handwritingSearch: Boolean) {
        _handwritingSearch.value = handwritingSearch
        _pencilAction.value = pencilAction
        _goalMin.value = goalMin
        _reminder.value = reminder
        viewModelScope.launch(Dispatchers.IO) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(GOAL_KEY, goalMin)
                .putBoolean(HANDWRITING_SEARCH_KEY, handwritingSearch)
                .putString(PenColors.ACTION_KEY, pencilAction.name)
                .putBoolean(REMINDER_ENABLED_KEY, reminder.enabled)
                .putInt(REMINDER_TIME_KEY, reminder.minuteOfDay)
                .apply()
            ReminderScheduler.arm(context)
            if (handwritingSearch) inkIndexer.schedule()
        }
    }

    fun setGoal(minutes: Int) {
        _goalMin.value = minutes
        viewModelScope.launch(Dispatchers.IO) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(GOAL_KEY, minutes).apply()
        }
    }
}
