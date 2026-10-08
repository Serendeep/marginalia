package com.serendeep.marginalia.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PenPrefsTest {

    private val themed = intArrayOf(0xFF111112.toInt(), 0xFF222222.toInt(), 0xFF333333.toInt())

    @Test
    fun widthsAreThreeAscendingSizes() {
        assertEquals(listOf(3f, 6f, 10f), PenWidth.entries.map { it.px })
        assertEquals(Pens.DEFAULT_SIZE_PX, PenWidth.MEDIUM.px, 0f)
    }

    @Test
    fun widthRoundTripsAndFallsBack() {
        PenWidth.entries.forEach { assertEquals(it, PenColors.widthFrom(it.name)) }
        assertEquals(PenWidth.MEDIUM, PenColors.widthFrom(null))
        assertEquals(PenWidth.MEDIUM, PenColors.widthFrom("HUGE"))
    }

    @Test
    fun themedChoicesFollowThePalette() {
        for (i in 0 until 3) assertEquals(themed[i], PenColors.resolve(i, themed))
    }

    @Test
    fun fixedChoicesAreTheSpecifiedColours() {
        assertEquals(0xFFE5484D.toInt(), PenColors.resolve(3, themed))
        assertEquals(0xFF3DBE74.toInt(), PenColors.resolve(4, themed))
        assertEquals(0xFF111111.toInt(), PenColors.resolve(5, themed))
        assertEquals(0xFFF2F2F5.toInt(), PenColors.resolve(6, themed))
        assertEquals(0xFFF5C842.toInt(), PenColors.resolve(7, themed))
        assertEquals(8, PenColors.choiceCount)
    }

    @Test
    fun badSwatchValuesFallBackToTheSlotDefault() {
        assertEquals(1, PenColors.choiceFrom(null, 1))
        assertEquals(2, PenColors.choiceFrom(99, 2))
        assertEquals(0, PenColors.choiceFrom(-1, 0))
        assertEquals(6, PenColors.choiceFrom(6, 0))
    }

    @Test
    fun outOfRangeChoiceDoesNotCrash() {
        assertEquals(PenColors.resolve(7, themed), PenColors.resolve(50, themed))
    }

    @Test
    fun doubleTapActionsRoundTrip() {
        PencilAction.entries.forEach { assertEquals(it, PenColors.actionFrom(it.name)) }
        assertEquals(PencilAction.TOGGLE_ERASER, PenColors.actionFrom(null))
    }

    @Test
    fun doubleTapDispatch() {
        assertEquals(InkTool.ERASER, doubleTapTool(PencilAction.TOGGLE_ERASER, InkTool.PEN, InkTool.HIGHLIGHTER))
        assertEquals(InkTool.PEN, doubleTapTool(PencilAction.TOGGLE_ERASER, InkTool.ERASER, InkTool.PEN))
        assertEquals(InkTool.HIGHLIGHTER, doubleTapTool(PencilAction.LAST_TOOL, InkTool.PEN, InkTool.HIGHLIGHTER))
        assertEquals(InkTool.HIGHLIGHTER, doubleTapTool(PencilAction.HIGHLIGHTER, InkTool.PEN, InkTool.PEN))
        assertEquals(InkTool.PEN, doubleTapTool(PencilAction.HIGHLIGHTER, InkTool.HIGHLIGHTER, InkTool.PEN))
        assertNull(doubleTapTool(PencilAction.UNDO, InkTool.PEN, InkTool.PEN))
    }

    @Test
    fun swatchKeysAreDistinct() {
        assertTrue((0 until 3).map { PenColors.swatchKey(it) }.toSet().size == 3)
    }
}
