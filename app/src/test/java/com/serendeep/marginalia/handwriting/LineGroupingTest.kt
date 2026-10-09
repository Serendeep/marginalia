package com.serendeep.marginalia.handwriting

import com.serendeep.marginalia.data.Box
import com.serendeep.marginalia.ink.InkPt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineGroupingTest {

    private class S(val name: String, val box: Box, val anchor: String? = null)

    private fun s(name: String, l: Float, t: Float, r: Float, b: Float, anchor: String? = null) = S(name, Box(l, t, r, b), anchor)

    private fun lines(items: List<S>) = groupLines(items) { it.box }.map { line -> line.map { it.name } }

    @Test
    fun overlappingStrokesShareALineAndAreOrderedLeftToRight() {
        val got = lines(listOf(s("b", 60f, 10f, 90f, 50f), s("a", 0f, 12f, 40f, 48f), s("c", 100f, 20f, 130f, 44f)))
        assertEquals(listOf(listOf("a", "b", "c")), got)
    }

    @Test
    fun lowStrokeSittingWithinTheCentreToleranceJoinsTheLine() {
        // Barely overlaps vertically but its centre is within 0.6x the median height.
        val got = lines(listOf(s("a", 0f, 0f, 40f, 40f), s("dot", 50f, 38f, 60f, 48f), s("c", 70f, 0f, 100f, 40f)))
        assertEquals(1, got.size)
    }

    @Test
    fun separatedRowsBecomeSeparateLinesTopFirst() {
        val got = lines(listOf(s("low", 0f, 200f, 50f, 240f), s("high", 0f, 0f, 50f, 40f), s("mid", 0f, 100f, 50f, 140f)))
        assertEquals(listOf(listOf("high"), listOf("mid"), listOf("low")), got)
    }

    @Test
    fun emptyInputGivesNoLines() {
        assertTrue(lines(emptyList()).isEmpty())
    }

    private fun blocks(items: List<S>) =
        groupBlocks(groupLines(items) { it.box }, { it.box }, { it.anchor }).map { b -> b.map { l -> l.map { it.name } } }

    @Test
    fun closeLinesFormOneBlock() {
        val got = blocks(listOf(s("a", 0f, 0f, 50f, 40f), s("b", 0f, 55f, 50f, 95f), s("c", 0f, 110f, 50f, 150f)))
        assertEquals(listOf(listOf(listOf("a"), listOf("b"), listOf("c"))), got)
    }

    @Test
    fun aLargeGapStartsANewBlock() {
        val got = blocks(listOf(s("a", 0f, 0f, 50f, 40f), s("b", 0f, 200f, 50f, 240f)))
        assertEquals(2, got.size)
    }

    @Test
    fun linesBeyondTheWindowSplitUnlessTheyShareAnAnchor() {
        fun column(anchor: String?, other: String?) = (0 until 20).map { i ->
            s("l$i", 0f, i * 45f, 50f, i * 45f + 40f, if (i < 10) anchor else other)
        }
        val unanchored = blocks(column(null, null))
        assertTrue("window splits a long run: ${unanchored.size}", unanchored.size > 1)
        val anchored = blocks(column("x", "x"))
        assertEquals(1, anchored.size)
    }

    @Test
    fun hashChangesWithMembershipAndIgnoresOrder() {
        assertEquals(strokesHash(listOf("a", "b")), strokesHash(listOf("b", "a")))
        assertNotEquals(strokesHash(listOf("a", "b")), strokesHash(listOf("a")))
        assertNotEquals(strokesHash(listOf("a", "b")), strokesHash(listOf("a", "c")))
        assertNotEquals(strokesHash(emptyList()), strokesHash(listOf("a")))
    }

    @Test
    fun timelineIsMonotonicAcrossOverlappingStrokes() {
        val first = TimedStroke(1000, listOf(InkPt(0f, 0f, 0), InkPt(1f, 1f, 50), InkPt(2f, 2f, 120)))
        val tie = TimedStroke(1000, listOf(InkPt(5f, 5f, 7), InkPt(6f, 6f, 40)))
        val earlier = TimedStroke(10, listOf(InkPt(9f, 9f, 3)))
        val out = timeline(listOf(first, tie, earlier)).flatten()
        assertEquals(listOf(1000L, 1050L, 1120L), out.take(3).map { it.t })
        assertTrue(out.zipWithNext().all { (a, b) -> b.t > a.t || b.t == a.t })
        assertTrue("strokes do not overlap in time", out[3].t > out[2].t && out[5].t > out[4].t)
        assertEquals(listOf(0f, 1f, 2f, 5f, 6f, 9f), out.map { it.x })
    }

    @Test
    fun timelineKeepsRealGapsBetweenStrokes() {
        val a = TimedStroke(0, listOf(InkPt(0f, 0f, 0), InkPt(1f, 0f, 10)))
        val b = TimedStroke(500, listOf(InkPt(2f, 0f, 0)))
        assertEquals(500L, timeline(listOf(a, b))[1].first().t)
    }
}
