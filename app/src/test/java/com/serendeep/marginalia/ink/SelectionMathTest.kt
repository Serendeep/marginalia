package com.serendeep.marginalia.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionMathTest {

    private val square = listOf(0f to 0f, 100f to 0f, 100f to 100f, 0f to 100f)
    private val ell = listOf(0f to 0f, 100f to 0f, 100f to 40f, 40f to 40f, 40f to 100f, 0f to 100f)

    @Test
    fun pointInPolygonHandlesConcaveShapes() {
        assertTrue(insidePolygon(50f, 50f, square))
        assertFalse(insidePolygon(150f, 50f, square))
        assertTrue(insidePolygon(20f, 80f, ell))
        assertFalse(insidePolygon(80f, 80f, ell))
    }

    @Test
    fun strokeIsSelectedAtSixtyPercent() {
        val inside = (0 until 6).map { InkPt(10f + it * 10f, 50f) }
        val outside = (0 until 4).map { InkPt(300f, 50f) }
        val stroke = inside + outside
        assertEquals(0.6f, fractionInsidePolygon(stroke, square), 0.001f)
        assertTrue(fractionInsidePolygon(stroke, square) >= 0.6f)
        assertTrue(fractionInsidePolygon(stroke.dropLast(1) + InkPt(300f, 50f), square) >= 0.6f)
        assertTrue(fractionInsidePolygon(inside.take(5) + outside + InkPt(300f, 60f), square) < 0.6f)
    }

    @Test
    fun degeneratePolygonSelectsNothing() {
        assertEquals(0f, fractionInsidePolygon(listOf(InkPt(1f, 1f)), listOf(0f to 0f, 5f to 5f)), 0f)
        assertEquals(0f, fractionInsidePolygon(emptyList(), square), 0f)
    }

    @Test
    fun moveShiftsBothAxes() {
        val t = StrokeTransform.move(12f, -5f)
        assertEquals(22f, t.x(10f), 0f)
        assertEquals(5f, t.y(10f), 0f)
    }

    @Test
    fun scaleKeepsTheAnchorFixed() {
        val t = StrokeTransform.scaleAbout(2f, 40f, 60f)
        assertEquals(40f, t.x(40f), 0.001f)
        assertEquals(60f, t.y(60f), 0.001f)
        assertEquals(60f, t.x(50f), 0.001f)
        assertEquals(80f, t.y(70f), 0.001f)
    }

    @Test
    fun identityChangesNothing() {
        assertEquals(7f, StrokeTransform.IDENTITY.x(7f), 0f)
        assertEquals(-3f, StrokeTransform.IDENTITY.y(-3f), 0f)
    }
}
