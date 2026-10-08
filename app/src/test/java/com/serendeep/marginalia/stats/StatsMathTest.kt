package com.serendeep.marginalia.stats

import com.serendeep.marginalia.data.CardEntity
import com.serendeep.marginalia.data.CardState
import com.serendeep.marginalia.data.StudySessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatsMathTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 8)

    private fun at(day: LocalDate, hour: Int) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun session(day: LocalDate, minutes: Int, lecture: String?) =
        StudySessionEntity("s${day}${lecture}", lecture, "READING", at(day, 10), at(day, 10) + minutes * 60_000L)

    private fun card(dueAt: Long, state: CardState) =
        CardEntity(id = "c$dueAt$state", source = "TYPED", state = state.name, dueAt = dueAt, createdAt = 0)

    @Test
    fun lastDays_oldestFirstEndsToday() {
        val days = lastDays(mapOf(today to 30, today.minusDays(13) to 5, today.minusDays(14) to 99), today)
        assertEquals(14, days.size)
        assertEquals(5, days.first())
        assertEquals(30, days.last())
    }

    @Test
    fun niceMax_roundsUpOnLadder() {
        assertEquals(30, niceMax(0))
        assertEquals(30, niceMax(30))
        assertEquals(60, niceMax(31))
        assertEquals(180, niceMax(121))
        assertEquals(780, niceMax(721))
    }

    @Test
    fun courseMinutes_sumsPerCourseWithinWindow() {
        val sessions = listOf(
            session(today, 20, "l1"),
            session(today.minusDays(1), 15, "l2"),
            session(today.minusDays(2), 10, "l1"),
            session(today, 5, null),
            session(today.minusDays(9), 60, "l1"),
        )
        val byCourse = courseMinutes(sessions, mapOf("l1" to "c1", "l2" to "c1"), today, zone)
        assertEquals(mapOf<String?, Int>("c1" to 45, null to 5), byCourse)
    }

    @Test
    fun dueTomorrow_countsOnlyTomorrowAndNotNew() {
        val cards = listOf(
            card(at(today.plusDays(1), 9), CardState.REVIEW),
            card(at(today.plusDays(1), 23), CardState.LEARNING),
            card(at(today.plusDays(1), 9) + 1, CardState.NEW),
            card(at(today, 20), CardState.REVIEW),
            card(at(today.plusDays(2), 0), CardState.REVIEW),
        )
        assertEquals(2, dueTomorrow(cards, today, zone))
    }

    @Test
    fun reviewedSince_windowIsSevenDaysInclusive() {
        val byDay = mapOf(today to 4, today.minusDays(6) to 3, today.minusDays(7) to 50)
        assertEquals(7, reviewedSince(byDay, today))
    }

    @Test
    fun hoursLabel_oneDecimal() {
        assertEquals("0.0", hoursLabel(0))
        assertEquals("1.5", hoursLabel(90))
        assertEquals("12.4", hoursLabel(745))
    }
}
