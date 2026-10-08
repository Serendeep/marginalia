package com.serendeep.marginalia.study

import com.serendeep.marginalia.data.CardEntity
import com.serendeep.marginalia.data.CardState
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

enum class Grade { AGAIN, HARD, GOOD, EASY }

private const val MINUTE_MS = 60_000L
private const val DAY_MS = 24 * 60 * MINUTE_MS

private val LEARNING_STEPS_MIN = longArrayOf(1, 10)
private const val RELEARN_STEP_MIN = 10L
private const val GRADUATE_DAYS = 1.0
private const val EASY_DAYS = 4.0
private const val MIN_EASE = 1.3
private const val EASY_BONUS = 1.3
private const val HARD_FACTOR = 1.2
private const val LAPSE_FACTOR = 0.5

/**
 * Anki-style SM-2 with four grades. Pure: [next] returns the rescheduled copy of a card.
 */
// ponytail: SM-2, swap to FSRS if retention stats look poor.
object Scheduler {

    fun next(card: CardEntity, grade: Grade, now: Long): CardEntity = when (card.cardState) {
        CardState.NEW, CardState.LEARNING -> learning(card, grade, now)
        CardState.REVIEW -> review(card, grade, now)
        CardState.RELEARNING -> relearning(card, grade, now)
    }.copy(reps = card.reps + 1)

    private fun learning(card: CardEntity, grade: Grade, now: Long): CardEntity {
        val step = if (card.cardState == CardState.NEW) 0 else card.step.coerceIn(0, LEARNING_STEPS_MIN.size - 1)
        fun stay(s: Int) = card.copy(
            state = CardState.LEARNING.name,
            step = s,
            dueAt = now + LEARNING_STEPS_MIN[s] * MINUTE_MS,
        )
        fun graduate(days: Double) = card.copy(
            state = CardState.REVIEW.name,
            step = 0,
            intervalDays = days,
            dueAt = now + (days * DAY_MS).toLong(),
        )
        return when (grade) {
            Grade.AGAIN -> stay(0)
            Grade.HARD -> stay(step)
            Grade.GOOD -> if (step + 1 < LEARNING_STEPS_MIN.size) stay(step + 1) else graduate(GRADUATE_DAYS)
            Grade.EASY -> graduate(EASY_DAYS)
        }
    }

    private fun review(card: CardEntity, grade: Grade, now: Long): CardEntity {
        val ivl = card.intervalDays
        if (grade == Grade.AGAIN) {
            return card.copy(
                state = CardState.RELEARNING.name,
                step = 0,
                lapses = card.lapses + 1,
                ease = max(MIN_EASE, card.ease - 0.2),
                intervalDays = max(1.0, ivl * LAPSE_FACTOR),
                dueAt = now + RELEARN_STEP_MIN * MINUTE_MS,
            )
        }
        val (days, ease) = when (grade) {
            Grade.HARD -> ivl * HARD_FACTOR to max(MIN_EASE, card.ease - 0.15)
            Grade.GOOD -> ivl * card.ease to card.ease
            else -> ivl * card.ease * EASY_BONUS to card.ease + 0.15
        }
        return card.copy(
            step = 0,
            ease = ease,
            intervalDays = days,
            dueAt = now + (days * DAY_MS).toLong(),
        )
    }

    private fun relearning(card: CardEntity, grade: Grade, now: Long): CardEntity = when (grade) {
        Grade.AGAIN, Grade.HARD -> card.copy(dueAt = now + RELEARN_STEP_MIN * MINUTE_MS)
        else -> {
            // The lapse already stored the reduced interval; easy adds a day on top.
            val days = card.intervalDays + if (grade == Grade.EASY) 1.0 else 0.0
            card.copy(
                state = CardState.REVIEW.name,
                step = 0,
                intervalDays = days,
                dueAt = now + (days * DAY_MS).toLong(),
            )
        }
    }

    /** Button caption for how far out grading [grade] would push the card. */
    fun label(card: CardEntity, grade: Grade, now: Long): String =
        formatInterval(next(card, grade, now).dueAt - now)
}

/** "1m", "10m", "3h", "4d", "1.5mo", "1.2y". */
fun formatInterval(ms: Long): String {
    val minutes = ceil(ms / MINUTE_MS.toDouble()).toLong().coerceAtLeast(1)
    return when {
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${(minutes / 60.0).roundToInt()}h"
        else -> {
            val days = ms / DAY_MS.toDouble()
            when {
                days < 30 -> "${days.roundToInt().coerceAtLeast(1)}d"
                days < 365 -> trim(days / 30.0) + "mo"
                else -> trim(days / 365.0) + "y"
            }
        }
    }
}

private fun trim(v: Double): String = "%.1f".format(Locale.ROOT, v).removeSuffix(".0")
