package com.serendeep.marginalia.study

import com.serendeep.marginalia.data.CardEntity
import com.serendeep.marginalia.data.CardState
import kotlin.math.ceil

const val NEW_PER_DAY = 20
const val SECONDS_PER_CARD = 8

/** Due cards in study order: learning first, then reviews, then new cards up to what is left of the daily cap. */
fun dueQueue(
    cards: List<CardEntity>,
    now: Long,
    newIntroducedToday: Int,
    newPerDay: Int = NEW_PER_DAY,
): List<CardEntity> {
    val due = cards.filter { it.dueAt <= now }
    val learning = due.filter { it.cardState == CardState.LEARNING || it.cardState == CardState.RELEARNING }
        .sortedBy { it.dueAt }
    val review = due.filter { it.cardState == CardState.REVIEW }.sortedBy { it.dueAt }
    val fresh = due.filter { it.cardState == CardState.NEW }.sortedBy { it.createdAt }
        .take((newPerDay - newIntroducedToday).coerceAtLeast(0))
    return learning + review + fresh
}

data class DueSplit(val newCards: Int, val learning: Int, val lapsed: Int, val review: Int) {
    val total: Int get() = newCards + learning + lapsed + review
}

fun dueSplit(queue: List<CardEntity>): DueSplit = DueSplit(
    newCards = queue.count { it.cardState == CardState.NEW },
    learning = queue.count { it.cardState == CardState.LEARNING },
    lapsed = queue.count { it.cardState == CardState.RELEARNING },
    review = queue.count { it.cardState == CardState.REVIEW },
)

/** Minutes for the "Start" button label. */
fun estimateMinutes(due: Int): Int = ceil(due * SECONDS_PER_CARD / 60.0).toInt()

private const val MIN_CLOZE_WORD = 5

/** The highlight with its longest word of five or more letters blanked out; unchanged when no word qualifies. */
fun clozeFront(text: String): String {
    val words = Regex("[\\p{L}\\p{N}]+").findAll(text).toList()
    val target = words.filter { it.value.length >= MIN_CLOZE_WORD }.maxByOrNull { it.value.length } ?: return text
    return text.replaceRange(target.range, "_____")
}
