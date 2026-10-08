package com.serendeep.marginalia.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScratchOutTest {

    @Test
    fun zigzagIsAScratch() {
        val box = ScratchOut.detect(Traces.zigzag(strokes = 6))
        assertNotNull(box)
        assertEquals(120f, box!!.width, 6f)
    }

    @Test
    fun denseVerticalScratchIsAScratch() {
        val verts = (0..8).map { (100f + it * 4f) to (if (it % 2 == 0) 100f else 180f) }
        assertNotNull(ScratchOut.detect(Traces.walk(verts, noise = 1f)))
    }

    @Test
    fun fewSweepsAreNotEnough() {
        assertNull(ScratchOut.detect(Traces.zigzag(strokes = 3, height = 30f)))
    }

    @Test
    fun handwrittenMmIsNotAScratch() {
        assertNull(ScratchOut.detect(Traces.humps(4)))
    }

    @Test
    fun handwrittenWwwIsNotAScratch() {
        assertNull(ScratchOut.detect(Traces.humps(6, pitch = 18f, height = 30f, cusp = 4f)))
    }

    @Test
    fun longRunOfLoopsIsNotAScratch() {
        assertNull(ScratchOut.detect(Traces.humps(10, pitch = 14f, height = 24f)))
    }

    @Test
    fun straightUnderlineIsNotAScratch() {
        assertNull(ScratchOut.detect(Traces.walk(listOf(100f to 100f, 300f to 102f))))
    }

    @Test
    fun flatBackAndForthIsTooThin() {
        val verts = (0..8).map { (if (it % 2 == 0) 100f else 220f) to 100f + (it % 3) }
        assertNull(ScratchOut.detect(Traces.walk(verts, noise = 0.5f)))
    }

    @Test
    fun circleIsNotAScratch() {
        assertNull(ScratchOut.detect(Traces.circle(200f, 200f, 50f)))
    }

    @Test
    fun reversalsIgnoreSmallWobble() {
        val v = floatArrayOf(0f, 10f, 9f, 11f, 10f, 30f, 29f, 31f)
        assertEquals(0, ScratchOut.reversals(v, 8f))
        assertEquals(2, ScratchOut.reversals(floatArrayOf(0f, 50f, 0f, 50f), 20f))
    }

    @Test
    fun coverageNeedsFortyPercentInsideTheInflatedBox() {
        val area = Extent(100f, 100f, 200f, 140f)
        val inside = (0..9).map { InkPt(110f + it * 8f, 120f) }
        assertTrue(ScratchOut.covers(area, inside))
        val half = inside.take(4) + (0..5).map { InkPt(400f, 400f) }
        assertTrue(ScratchOut.covers(area, half))
        val barely = inside.take(3) + (0..6).map { InkPt(400f, 400f) }
        assertFalse(ScratchOut.covers(area, barely))
    }

    @Test
    fun inflationCatchesStrokesJustOutsideTheBox() {
        val area = Extent(100f, 100f, 200f, 140f)
        val edge = (0..9).map { InkPt(110f + it * 8f, 97f) }
        assertTrue(ScratchOut.covers(area, edge))
        val off = (0..9).map { InkPt(110f + it * 8f, 90f) }
        assertFalse(ScratchOut.covers(area, off))
    }
}
