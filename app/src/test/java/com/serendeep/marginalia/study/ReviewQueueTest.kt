package com.serendeep.marginalia.study

import com.serendeep.marginalia.data.CardEntity
import com.serendeep.marginalia.data.CardSource
import com.serendeep.marginalia.data.CardState
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewQueueTest {
    private fun card(id: String, state: CardState, due: Long, created: Long = 0) = CardEntity(
        id = id, source = CardSource.TYPED.name, state = state.name, dueAt = due, createdAt = created,
    )

    @Test
    fun order_learningThenReviewThenNew() {
        val cards = listOf(
            card("new", CardState.NEW, 0),
            card("rev", CardState.REVIEW, 5),
            card("relearn", CardState.RELEARNING, 9),
            card("learn", CardState.LEARNING, 7),
        )
        assertEquals(listOf("learn", "relearn", "rev", "new"), dueQueue(cards, 100, 0).map { it.id })
    }

    @Test
    fun notYetDue_isExcluded() {
        val cards = listOf(card("later", CardState.REVIEW, 200), card("now", CardState.REVIEW, 100))
        assertEquals(listOf("now"), dueQueue(cards, 100, 0).map { it.id })
    }

    @Test
    fun newCards_cappedPerDay() {
        val cards = (1..30).map { card("n$it", CardState.NEW, 0, created = it.toLong()) }
        assertEquals(20, dueQueue(cards, 1, 0).size)
        assertEquals(5, dueQueue(cards, 1, 15).size)
        assertEquals(0, dueQueue(cards, 1, 25).size)
    }

    @Test
    fun newCap_doesNotLimitReviews() {
        val cards = (1..25).map { card("r$it", CardState.REVIEW, 0) }
        assertEquals(25, dueQueue(cards, 1, 20).size)
    }

    @Test
    fun newCards_oldestFirst() {
        val cards = listOf(card("b", CardState.NEW, 0, 2), card("a", CardState.NEW, 0, 1))
        assertEquals(listOf("a", "b"), dueQueue(cards, 1, 0).map { it.id })
    }

    @Test
    fun split_countsEachState() {
        val q = listOf(
            card("1", CardState.NEW, 0), card("2", CardState.NEW, 0),
            card("3", CardState.LEARNING, 0), card("4", CardState.RELEARNING, 0), card("5", CardState.REVIEW, 0),
        )
        val s = dueSplit(q)
        assertEquals(DueSplit(2, 1, 1, 1), s)
        assertEquals(5, s.total)
    }

    @Test
    fun estimate_roundsUpEightSecondsEach() {
        assertEquals(0, estimateMinutes(0))
        assertEquals(1, estimateMinutes(1))
        assertEquals(1, estimateMinutes(7))
        assertEquals(2, estimateMinutes(8))
        assertEquals(8, estimateMinutes(60))
    }

    @Test
    fun cloze_blanksLongestWord() {
        assertEquals("Entropy never _____ in a closed system", clozeFront("Entropy never decreases in a closed system"))
    }

    @Test
    fun cloze_keepsPunctuation() {
        assertEquals("The _____, said Bob.", clozeFront("The theorem, said Bob."))
        assertEquals("It is _____.", clozeFront("It is photosynthesis."))
    }

    @Test
    fun cloze_withoutLongWord_isUnchanged() {
        assertEquals("a b c d", clozeFront("a b c d"))
    }
}
