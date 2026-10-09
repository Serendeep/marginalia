package com.serendeep.marginalia.ai

import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import android.util.Log
import com.serendeep.marginalia.data.CourseEntity
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.library.LibraryViewModel
import com.serendeep.marginalia.shell.PREFS
import com.serendeep.marginalia.ui.theme.CoursePalette
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/** What a sort changed, with enough to put everything back. */
data class SortResult(
    val lectureId: String,
    val title: String,
    val previousTitle: String,
    val fromCourseId: String,
    val courseId: String,
    val courseName: String,
    val createdCourse: Boolean,
    val addedTagIds: List<String>,
) {
    val renamed: Boolean get() = title != previousTitle
}

/** Where a decision files a notebook: an existing course, or a new one to create. */
internal data class CoursePick(val existing: CourseEntity?, val newName: String?, val emoji: String?, val colorIndex: Int)

internal fun pickCourse(decision: SortDecision, courses: List<CourseEntity>): CoursePick? {
    val filing = courses.filter { it.name != LibraryViewModel.UNSORTED_NAME }
    fun byName(name: String?) = name?.let { n -> filing.firstOrNull { it.name.equals(n, ignoreCase = true) } }
    byName(decision.course)?.let { return CoursePick(it, null, null, it.colorIndex) }
    val name = decision.newCourse ?: return null
    byName(name)?.let { return CoursePick(it, null, null, it.colorIndex) }
    if (name.equals(LibraryViewModel.UNSORTED_NAME, ignoreCase = true)) return null
    val emoji = decision.newCourseEmoji?.takeIf { it.length <= MAX_EMOJI_CHARS }
    return CoursePick(null, name.take(MAX_COURSE_NAME), emoji, nextColorIndex(filing.map { it.colorIndex }.toSet(), CoursePalette.swatches.size))
}

internal fun nextColorIndex(used: Set<Int>, size: Int): Int =
    (0 until size).firstOrNull { it !in used } ?: (used.size % size)

private const val MAX_EMOJI_CHARS = 8
private const val MAX_COURSE_NAME = 60

/** Whether the active provider can take a request right now. */
fun sortReady(config: AiConfig, status: ChatGptStatus): Boolean = when (config.provider) {
    ProviderChoice.CHATGPT -> status is ChatGptStatus.Connected
    ProviderChoice.COMPATIBLE -> config.baseUrl.isNotBlank() && config.model.isNotBlank()
}

/**
 * Files notebooks from the Unsorted shelf into courses with one model request each.
 * Requests run one at a time; failures leave the notebook where it is.
 */
@Singleton
class AutoSorter internal constructor(
    private val repository: MarginaliaRepository,
    private val prefs: SharedPreferences,
    private val provider: () -> AiProvider?,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        repository: MarginaliaRepository,
        router: AiRouter,
        settings: AiSettings,
        auth: ChatGptAuth,
    ) : this(
        repository,
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
        { if (sortReady(settings.config.value, auth.status.value)) router.active() else null },
    )

    private val dispatcher = Executors.newSingleThreadExecutor { task ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        }, "auto-sorter")
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val queue = Mutex()

    private val _enabled = MutableStateFlow(prefs.getBoolean(ENABLED_KEY, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _results = MutableSharedFlow<SortResult>(extraBufferCapacity = 16)

    /** Results of automatic sorts after an import; manual sorts are returned to their caller instead. */
    val results: SharedFlow<SortResult> = _results.asSharedFlow()

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(ENABLED_KEY, on).apply()
        _enabled.value = on
    }

    /** Queues an automatic sort for a freshly indexed notebook. */
    fun onIndexed(lectureId: String) {
        if (!_enabled.value) return
        scope.launch { sort(lectureId)?.let { _results.emit(it) } }
    }

    suspend fun sort(lectureId: String, force: Boolean = false): SortResult? = queue.withLock {
        try {
            sortLocked(lectureId, force)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "sort failed: ${e.javaClass.simpleName}")
            null
        }
    }

    private suspend fun sortLocked(lectureId: String, force: Boolean): SortResult? {
        val ai = provider() ?: return null
        if (!force && lectureId in attempted()) return null
        val lecture = repository.getLecture(lectureId) ?: return null
        val courses = repository.courses()
        val unsorted = courses.firstOrNull { it.name == LibraryViewModel.UNSORTED_NAME } ?: return null
        if (lecture.courseId != unsorted.id) return null
        markAttempted(lectureId)

        val fileName = repository.latestDocument(lectureId)?.fileName.orEmpty()
        val pages = listOfNotNull(repository.indexedPageText(lectureId, 0), repository.indexedPageText(lectureId, 1))
        val request = Prompts.sortDocument(
            courses = courses.filter { it.id != unsorted.id }.map { it.name },
            title = lecture.title,
            fileName = fileName,
            identifier = lecture.arxivId ?: lecture.doi,
            pages = pages,
        )
        val decision = Prompts.parseSort(complete(ai, request) ?: return null) ?: return null
        return apply(lecture.title, lectureId, unsorted.id, decision, courses)
    }

    private suspend fun complete(ai: AiProvider, request: AiRequest): String? {
        val text = StringBuilder()
        var done = false
        try {
            ai.stream(request).collect { event ->
                when (event) {
                    is AiEvent.Delta -> text.append(event.text)
                    AiEvent.Completed -> done = true
                    is AiEvent.Incomplete -> done = false
                    is AiEvent.Failed -> {
                        Log.w(TAG, "model request failed: ${event.error.kind}")
                        done = false
                    }
                }
            }
        } catch (e: AiException) {
            Log.w(TAG, "model request failed: ${e.error.kind}")
            return null
        }
        return if (done) text.toString() else null
    }

    private suspend fun apply(
        currentTitle: String,
        lectureId: String,
        unsortedId: String,
        decision: SortDecision,
        courses: List<CourseEntity>,
    ): SortResult? {
        val pick = pickCourse(decision, courses) ?: return null
        val course = pick.existing ?: repository.createCourse(pick.newName.orEmpty(), pick.colorIndex, pick.emoji)
        repository.moveLecture(lectureId, course.id)

        val newTitle = decision.title?.take(MAX_TITLE)?.takeIf { looksLikeFilename(currentTitle) }
        if (newTitle != null) repository.renameLecture(lectureId, newTitle)

        val before = repository.tagIdsOf(lectureId).toSet()
        decision.tags.forEach { repository.addTag(lectureId, it) }
        val added = repository.tagIdsOf(lectureId).filter { it !in before }

        return SortResult(
            lectureId = lectureId,
            title = newTitle ?: currentTitle,
            previousTitle = currentTitle,
            fromCourseId = unsortedId,
            courseId = course.id,
            courseName = course.name,
            createdCourse = pick.existing == null,
            addedTagIds = added,
        )
    }

    /** Puts the notebook back in Unsorted with its old title, drops the tags this sort added and any course it created. */
    suspend fun undo(result: SortResult) {
        if (repository.getLecture(result.lectureId) != null) {
            repository.moveLecture(result.lectureId, result.fromCourseId)
            if (result.renamed) repository.renameLecture(result.lectureId, result.previousTitle)
            result.addedTagIds.forEach { repository.setTagged(result.lectureId, it, false) }
        }
        if (result.createdCourse) repository.deleteCourseIfEmpty(result.courseId)
    }

    private fun attempted(): Set<String> = prefs.getStringSet(ATTEMPTED_KEY, null).orEmpty()

    private fun markAttempted(lectureId: String) {
        prefs.edit().putStringSet(ATTEMPTED_KEY, attempted() + lectureId).apply()
    }

    private companion object {
        const val TAG = "AutoSorter"
        const val ENABLED_KEY = "auto_sort_imports"
        const val ATTEMPTED_KEY = "auto_sort_attempted"
        const val MAX_TITLE = 200
    }
}

/** Emits true while the active provider can take sort requests. */
fun sortReadyFlow(settings: AiSettings, auth: ChatGptAuth): Flow<Boolean> =
    combine(settings.config, auth.status, ::sortReady)
