package com.serendeep.marginalia.study

import com.serendeep.marginalia.data.CardEntity
import com.serendeep.marginalia.data.CardSource
import com.serendeep.marginalia.data.CardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerTest {
    private val now = 1_000_000_000L
    private val min = 60_000L
    private val day = 24 * 60 * min

    private fun card(
        state: CardState = CardState.NEW,
        step: Int = 0,
        ivl: Double = 0.0,
        ease: Double = 2.5,
    ) = CardEntity(
        id = "c", source = CardSource.TYPED.name, state = state.name, step = step,
        intervalDays = ivl, ease = ease, dueAt = 0, createdAt = 0,
    )

    @Test
    fun newCard_again_staysAtFirstStep() {
        val c = Scheduler.next(card(), Grade.AGAIN, now)
        assertEquals(CardState.LEARNING, c.cardState)
        assertEquals(0, c.step)
        assertEquals(now + min, c.dueAt)
        assertEquals(1, c.reps)
    }

    @Test
    fun newCard_good_movesToSecondStep() {
        val c = Scheduler.next(card(), Grade.GOOD, now)
        assertEquals(CardState.LEARNING, c.cardState)
        assertEquals(1, c.step)
        assertEquals(now + 10 * min, c.dueAt)
    }

    @Test
    fun newCard_hard_repeatsFirstStep() {
        val c = Scheduler.next(card(), Grade.HARD, now)
        assertEquals(0, c.step)
        assertEquals(now + min, c.dueAt)
    }

    @Test
    fun learning_goodOnLastStep_graduatesToOneDay() {
        val c = Scheduler.next(card(CardState.LEARNING, step = 1), Grade.GOOD, now)
        assertEquals(CardState.REVIEW, c.cardState)
        assertEquals(1.0, c.intervalDays, 0.0)
        assertEquals(now + day, c.dueAt)
    }

    @Test
    fun learning_again_resetsStep() {
        val c = Scheduler.next(card(CardState.LEARNING, step = 1), Grade.AGAIN, now)
        assertEquals(0, c.step)
        assertEquals(CardState.LEARNING, c.cardState)
    }

    @Test
    fun anyNewGrade_easy_graduatesToFourDays() {
        val c = Scheduler.next(card(), Grade.EASY, now)
        assertEquals(CardState.REVIEW, c.cardState)
        assertEquals(4.0, c.intervalDays, 0.0)
        assertEquals(now + 4 * day, c.dueAt)
    }

    @Test
    fun review_good_multipliesByEase() {
        val c = Scheduler.next(card(CardState.REVIEW, ivl = 10.0, ease = 2.5), Grade.GOOD, now)
        assertEquals(25.0, c.intervalDays, 1e-9)
        assertEquals(2.5, c.ease, 1e-9)
        assertEquals(now + 25 * day, c.dueAt)
    }

    @Test
    fun review_hard_growsSlowlyAndLowersEase() {
        val c = Scheduler.next(card(CardState.REVIEW, ivl = 10.0, ease = 2.5), Grade.HARD, now)
        assertEquals(12.0, c.intervalDays, 1e-9)
        assertEquals(2.35, c.ease, 1e-9)
    }

    @Test
    fun review_easy_appliesBonusAndRaisesEase() {
        val c = Scheduler.next(card(CardState.REVIEW, ivl = 10.0, ease = 2.5), Grade.EASY, now)
        assertEquals(32.5, c.intervalDays, 1e-9)
        assertEquals(2.65, c.ease, 1e-9)
    }

    @Test
    fun review_again_lapsesIntoRelearning() {
        val c = Scheduler.next(card(CardState.REVIEW, ivl = 10.0, ease = 2.5), Grade.AGAIN, now)
        assertEquals(CardState.RELEARNING, c.cardState)
        assertEquals(1, c.lapses)
        assertEquals(2.3, c.ease, 1e-9)
        assertEquals(5.0, c.intervalDays, 1e-9)
        assertEquals(now + 10 * min, c.dueAt)
    }

    @Test
    fun lapse_intervalNeverBelowOneDay() {
        val c = Scheduler.next(card(CardState.REVIEW, ivl = 1.0), Grade.AGAIN, now)
        assertEquals(1.0, c.intervalDays, 0.0)
    }

    @Test
    fun ease_neverDropsBelowFloor() {
        var c = card(CardState.REVIEW, ivl = 5.0, ease = 1.4)
        c = Scheduler.next(c, Grade.AGAIN, now)
        assertEquals(1.3, c.ease, 1e-9)
        c = card(CardState.REVIEW, ivl = 5.0, ease = 1.35)
        assertEquals(1.3, Scheduler.next(c, Grade.HARD, now).ease, 1e-9)
    }

    @Test
    fun relearning_goodGraduatesAtStoredInterval() {
        val lapsed = Scheduler.next(card(CardState.REVIEW, ivl = 10.0), Grade.AGAIN, now)
        val c = Scheduler.next(lapsed, Grade.GOOD, now + 10 * min)
        assertEquals(CardState.REVIEW, c.cardState)
        assertEquals(5.0, c.intervalDays, 1e-9)
        assertEquals(1, c.lapses)
    }

    @Test
    fun relearning_again_doesNotAddAnotherLapse() {
        val lapsed = Scheduler.next(card(CardState.REVIEW, ivl = 10.0), Grade.AGAIN, now)
        val c = Scheduler.next(lapsed, Grade.AGAIN, now)
        assertEquals(CardState.RELEARNING, c.cardState)
        assertEquals(1, c.lapses)
        assertEquals(now + 10 * min, c.dueAt)
    }

    @Test
    fun labels_matchButtonsForNewCard() {
        val c = card()
        assertEquals("1m", Scheduler.label(c, Grade.AGAIN, now))
        assertEquals("1m", Scheduler.label(c, Grade.HARD, now))
        assertEquals("10m", Scheduler.label(c, Grade.GOOD, now))
        assertEquals("4d", Scheduler.label(c, Grade.EASY, now))
    }

    @Test
    fun labels_graduatingGoodIsOneDay() {
        assertEquals("1d", Scheduler.label(card(CardState.LEARNING, step = 1), Grade.GOOD, now))
    }

    @Test
    fun formatInterval_unitsScale() {
        assertEquals("1m", formatInterval(1))
        assertEquals("45m", formatInterval(45 * min))
        assertEquals("3h", formatInterval(3 * 60 * min))
        assertEquals("12d", formatInterval(12 * day))
        assertEquals("1.5mo", formatInterval(45 * day))
        assertEquals("1y", formatInterval(365 * day))
        assertTrue(formatInterval(800 * day).endsWith("y"))
    }
}
