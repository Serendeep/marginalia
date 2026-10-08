package com.serendeep.marginalia.stats

import com.serendeep.marginalia.data.CardEntity
import com.serendeep.marginalia.data.CardState
import com.serendeep.marginalia.data.StudySessionEntity
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

const val FOCUS_DAYS = 14
private val Y_STEPS = intArrayOf(30, 60, 90, 120, 180, 240, 360, 480, 720)

/** Minutes for the [days] days ending at [today], oldest first. */
fun lastDays(minutes: Map<LocalDate, Int>, today: LocalDate, days: Int = FOCUS_DAYS): List<Int> =
    List(days) { minutes[today.minusDays((days - 1 - it).toLong())] ?: 0 }

/** The smallest axis ceiling from a fixed ladder that fits [max]; beyond the ladder, whole hours. */
fun niceMax(max: Int): Int =
    Y_STEPS.firstOrNull { it >= max } ?: ((max + 59) / 60 * 60)

/** Session minutes per course over the last [days] days; sessions with no known course land under null. */
fun courseMinutes(
    sessions: List<StudySessionEntity>,
    courseOfLecture: Map<String, String>,
    today: LocalDate,
    zone: ZoneId,
    days: Int = 7,
): Map<String?, Int> {
    val from = today.minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
    val millis = HashMap<String?, Long>()
    for (s in sessions) {
        val start = maxOf(s.startedAt, from)
        if (s.endedAt > start) millis.merge(s.lectureId?.let { courseOfLecture[it] }, s.endedAt - start, Long::plus)
    }
    return millis.mapValues { (it.value / 60_000L).toInt() }.filterValues { it > 0 }
}

/** Cards already in play whose next due time falls on the day after [today]. */
fun dueTomorrow(cards: List<CardEntity>, today: LocalDate, zone: ZoneId): Int {
    val from = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val to = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
    return cards.count { it.cardState != CardState.NEW && it.dueAt in from until to }
}

fun reviewedSince(byDay: Map<LocalDate, Int>, today: LocalDate, days: Int = 7): Int =
    (0 until days).sumOf { byDay[today.minusDays(it.toLong())] ?: 0 }

fun hoursLabel(totalMinutes: Int): String = "%.1f".format(Locale.ROOT, totalMinutes / 60.0)
