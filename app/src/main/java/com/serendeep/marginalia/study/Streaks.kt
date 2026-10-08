package com.serendeep.marginalia.study

import com.serendeep.marginalia.data.StudySessionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val STREAK_MIN_MINUTES = 10
const val STREAK_MIN_REVIEWS = 10

/**
 * Whole minutes studied per local day. Overlapping sessions (a focus block
 * running during reading) count once; a session crossing midnight is split.
 */
fun minutesByDay(sessions: List<StudySessionEntity>, zone: ZoneId): Map<LocalDate, Int> {
    val millis = HashMap<LocalDate, Long>()
    var curStart = -1L
    var curEnd = -1L
    fun flush() {
        if (curEnd <= curStart) return
        var t = curStart
        while (t < curEnd) {
            val day = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
            val next = minOf(curEnd, day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            millis.merge(day, next - t, Long::plus)
            t = next
        }
    }
    for (s in sessions.sortedBy { it.startedAt }) {
        if (curEnd >= 0 && s.startedAt <= curEnd) {
            curEnd = maxOf(curEnd, s.endedAt)
        } else {
            flush()
            curStart = s.startedAt
            curEnd = s.endedAt
        }
    }
    flush()
    return millis.mapValues { (it.value / 60_000L).toInt() }
}

private fun counts(day: LocalDate, minutes: Map<LocalDate, Int>, reviews: Map<LocalDate, Int>) =
    (minutes[day] ?: 0) >= STREAK_MIN_MINUTES || (reviews[day] ?: 0) >= STREAK_MIN_REVIEWS

/** Consecutive counted days ending today, or yesterday while today is still open. */
fun streakDays(
    minutesByDay: Map<LocalDate, Int>,
    reviewsByDay: Map<LocalDate, Int>,
    today: LocalDate,
): Int {
    var day = if (counts(today, minutesByDay, reviewsByDay)) today else today.minusDays(1)
    var n = 0
    while (counts(day, minutesByDay, reviewsByDay)) {
        n++
        day = day.minusDays(1)
    }
    return n
}

fun bestStreak(minutesByDay: Map<LocalDate, Int>, reviewsByDay: Map<LocalDate, Int>): Int {
    val days = (minutesByDay.keys + reviewsByDay.keys)
        .filter { counts(it, minutesByDay, reviewsByDay) }
        .sorted()
    var best = 0
    var run = 0
    var prev: LocalDate? = null
    for (d in days) {
        run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
        best = maxOf(best, run)
        prev = d
    }
    return best
}
