package com.serendeep.marginalia.ai

import android.content.Context
import com.serendeep.marginalia.data.HighlightRow
import com.serendeep.marginalia.data.LectureEntity
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.data.RetentionRow
import com.serendeep.marginalia.data.StudySessionEntity
import com.serendeep.marginalia.shell.PREFS
import com.serendeep.marginalia.study.dueQueue
import com.serendeep.marginalia.study.minutesByDay
import com.serendeep.marginalia.study.startOfDay
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_DOCUMENTS = 5
private const val MAX_HIGHLIGHTS = 8
private const val MAX_HIGHLIGHT_CHARS = 160

/** [lastPage] is one-based: the page the notebook was left on after being opened that day. */
data class OpenedDocument(val title: String, val lastPage: Int)

/** The facts of one study day that the digest is written from. */
data class DigestInput(
    val studyMinutes: Int,
    val byDocument: List<Pair<String, Int>>,
    val opened: List<OpenedDocument>,
    val highlights: List<String>,
    val highlightCount: Int,
    val cardsCreated: Int,
    val reviews: Int,
    val retentionPct: Int?,
    val dueToday: Int,
)

/** Null when the day had no activity, so there is nothing to recap. */
fun buildDigestInput(
    sessions: List<StudySessionEntity>,
    opened: List<LectureEntity>,
    highlights: List<HighlightRow>,
    titles: Map<String, String>,
    cardsCreated: Int,
    reviews: Int,
    retention: RetentionRow,
    dueToday: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): DigestInput? {
    if (sessions.isEmpty() && opened.isEmpty() && highlights.isEmpty() && cardsCreated == 0 && reviews == 0) return null
    val byDocument = sessions.filter { it.lectureId in titles }.groupBy { it.lectureId }
        .map { (id, group) -> titles.getValue(id!!) to minutesByDay(group, zone).values.sum() }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(MAX_DOCUMENTS)
    return DigestInput(
        studyMinutes = minutesByDay(sessions, zone).values.sum(),
        byDocument = byDocument,
        opened = opened.map { OpenedDocument(it.title, it.lastPage + 1) },
        highlights = highlights.take(MAX_HIGHLIGHTS).map { it.highlight.text.trim().take(MAX_HIGHLIGHT_CHARS) },
        highlightCount = highlights.size,
        cardsCreated = cardsCreated,
        reviews = reviews,
        retentionPct = retention.total.takeIf { it > 0 }?.let { retention.good * 100 / it },
        dueToday = dueToday,
    )
}

/** Gathers [day]'s facts from the repository; null when nothing happened that day. */
suspend fun MarginaliaRepository.digestInput(day: LocalDate, now: Long, zone: ZoneId = ZoneId.systemDefault()): DigestInput? {
    val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val sessions = sessionsBetween(from, to)
    val titles = sessions.mapNotNull { it.lectureId }.distinct().mapNotNull { id -> getLecture(id)?.let { id to it.title } }.toMap()
    return buildDigestInput(
        sessions = sessions,
        opened = openedBetween(from, to),
        highlights = highlightsBetween(from, to),
        titles = titles,
        cardsCreated = cardsCreatedBetween(from, to),
        reviews = reviewsBetween(from, to),
        retention = retentionBetween(from, to),
        dueToday = dueQueue(allCards(), now, newIntroducedSince(startOfDay(now, zone))).size,
        zone = zone,
    )
}

fun dayKey(day: LocalDate): String = day.toString()

/** The one digest kept at a time: the day it was written for, its text, and the day the user dismissed it. */
data class DigestCache(val day: String? = null, val text: String? = null, val dismissedDay: String? = null) {
    fun cached(today: String): String? = text?.takeIf { day == today }

    /** What Today shows: the cached text unless it was dismissed today. */
    fun visible(today: String): String? = cached(today)?.takeIf { dismissedDay != today }

    /** Whether a digest still has to be written for [today]. */
    fun needed(today: String): Boolean = cached(today) == null && dismissedDay != today

    fun with(today: String, text: String) = copy(day = today, text = text)
    fun dismissed(today: String) = copy(dismissedDay = today)
}

/** The opt-in switch and the cached digest, in preferences. */
@Singleton
class DigestStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(ENABLED, on).apply()
        _enabled.value = on
    }

    fun cache() = DigestCache(prefs.getString(DAY, null), prefs.getString(TEXT, null), prefs.getString(DISMISSED, null))

    fun save(cache: DigestCache) {
        prefs.edit().putString(DAY, cache.day).putString(TEXT, cache.text).putString(DISMISSED, cache.dismissedDay).apply()
    }

    private companion object {
        const val ENABLED = "morning_digest"
        const val DAY = "digest_day"
        const val TEXT = "digest_text"
        const val DISMISSED = "digest_dismissed_day"
    }
}
