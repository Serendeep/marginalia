package com.serendeep.marginalia.ink

import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.StrokeInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StrokeEraserTest {

    @Test
    fun missLeavesStrokeUntouched() {
        val result = StrokeEraser.erase(line(0f, 100f), eraserX = 500f, eraserY = 500f, radius = 10f)
        assertTrue(result.untouched)
    }

    @Test
    fun eraseMiddleSplitsIntoTwoSegments() {
        val result = StrokeEraser.erase(line(0f, 100f), eraserX = 50f, eraserY = 0f, radius = 8f)
        assertFalse(result.untouched)
        assertEquals(2, result.segments.size)
        result.segments.forEach { assertTrue(it.size >= 2) }
    }

    @Test
    fun eraseEndTrimsToOneSegment() {
        val result = StrokeEraser.erase(line(0f, 100f), eraserX = 100f, eraserY = 0f, radius = 15f)
        assertFalse(result.untouched)
        assertEquals(1, result.segments.size)
    }

    @Test
    fun eraseEverythingLeavesNoSegments() {
        val result = StrokeEraser.erase(line(0f, 100f), eraserX = 50f, eraserY = 0f, radius = 200f)
        assertFalse(result.untouched)
        assertTrue(result.segments.isEmpty())
    }

    @Test
    fun segmentsKeepOriginalTiming() {
        val result = StrokeEraser.erase(line(0f, 100f), eraserX = 50f, eraserY = 0f, radius = 8f)
        val second = result.segments[1]
        assertTrue("later segment keeps its original elapsed times", second.get(0).elapsedTimeMillis > 0)
    }

    private fun line(fromX: Float, toX: Float, points: Int = 21): MutableStrokeInputBatch {
        val batch = MutableStrokeInputBatch()
        val step = (toX - fromX) / (points - 1)
        repeat(points) { i ->
            batch.add(
                StrokeInput.create(
                    fromX + step * i,
                    0f,
                    i * 10L,
                    InputToolType.STYLUS,
                    StrokeInput.NO_STROKE_UNIT_LENGTH,
                ),
            )
        }
        return batch
    }
}
