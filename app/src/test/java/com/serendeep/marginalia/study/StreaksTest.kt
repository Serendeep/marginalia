package com.serendeep.marginalia.study

import com.serendeep.marginalia.data.StudySessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StreaksTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 8)

    private fun session(day: LocalDate, hour: Int, minutes: Int) = StudySessionEntity(
        id = "$day-$hour",
        lectureId = null,
        kind = "READING",
        startedAt = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
        endedAt = day.atTime(hour, 0).plusMinutes(minutes.toLong()).atZone(zone).toInstant().toEpochMilli(),
    )

    @Test
    fun minutesSumPerDayAndMergeOverlap() {
        val result = minutesByDay(
            listOf(session(today, 9, 30), session(today, 9, 20), session(today.minusDays(1), 10, 5)),
            zone,
        )
        assertEquals(30, result[today])
        assertEquals(5, result[today.minusDays(1)])
    }

    @Test
    fun sessionAcrossMidnightIsSplit() {
        val late = StudySessionEntity(
            "s", null, "READING",
            today.atTime(23, 30).atZone(zone).toInstant().toEpochMilli(),
            today.plusDays(1).atTime(0, 30).atZone(zone).toInstant().toEpochMilli(),
        )
        val result = minutesByDay(listOf(late), zone)
        assertEquals(30, result[today])
        assertEquals(30, result[today.plusDays(1)])
    }

    @Test
    fun streakCountsTodayWhenMet() {
        val m = mapOf(today to 10, today.minusDays(1) to 25, today.minusDays(2) to 9)
        assertEquals(2, streakDays(m, emptyMap(), today))
    }

    @Test
    fun streakMayStartYesterday() {
        val m = mapOf(today to 3, today.minusDays(1) to 40, today.minusDays(2) to 12)
        assertEquals(2, streakDays(m, emptyMap(), today))
    }

    @Test
    fun streakBrokenWhenYesterdayMissed() {
        assertEquals(0, streakDays(mapOf(today.minusDays(2) to 60), emptyMap(), today))
    }

    @Test
    fun reviewsAlsoCount() {
        assertEquals(1, streakDays(emptyMap(), mapOf(today to 10), today))
    }

    @Test
    fun bestStreakFindsLongestRun() {
        val m = mapOf(
            today to 20, today.minusDays(1) to 20,
            today.minusDays(5) to 20, today.minusDays(6) to 20, today.minusDays(7) to 20,
        )
        assertEquals(3, bestStreak(m, emptyMap()))
    }
}
