package com.serendeep.marginalia.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfZoomStateTest {

    @Test
    fun scaleClampsToRange() {
        val tooFar = PdfZoomState().applyGesture(0f, 0f, 0f, 0f, 100f)
        assertEquals(PdfZoomState.MAX_SCALE, tooFar.scale, 0.001f)
        val tooClose = tooFar.applyGesture(0f, 0f, 0f, 0f, 0.0001f)
        assertEquals(PdfZoomState.MIN_SCALE, tooClose.scale, 0.001f)
    }

    @Test
    fun pinchKeepsCentroidPointFixed() {
        val centroidX = 300f
        val centroidY = 400f
        val start = PdfZoomState()
        val zoomed = start.applyGesture(centroidX, centroidY, 0f, 0f, 2f)
        // The layout point under the centroid before must land there after.
        val layoutX = (centroidX - start.offsetX) / start.scale
        val layoutY = (centroidY - start.offsetY) / start.scale
        assertEquals(centroidX, layoutX * zoomed.scale + zoomed.offsetX, 0.5f)
        assertEquals(centroidY, layoutY * zoomed.scale + zoomed.offsetY, 0.5f)
    }

    @Test
    fun clampNeverLeavesGapAtEdges() {
        val state = PdfZoomState(scale = 2f, offsetX = 50f, offsetY = -5000f)
            .clampToContent(1000f, 2000f, 1000f, 2000f)
        assertTrue("no gap on the left", state.offsetX <= 0f)
        assertTrue("no gap past the right", state.offsetX >= 1000f - 1000f * 2f)
        assertTrue("no gap past the bottom", state.offsetY >= 2000f - 2000f * 2f)
    }

    @Test
    fun doubleTapTogglesBetweenBaseAndZoomed() {
        val zoomedIn = PdfZoomState().doubleTap(200f, 200f)
        assertEquals(PdfZoomState.DOUBLE_TAP_SCALE, zoomedIn.scale, 0.001f)
        val backOut = zoomedIn.doubleTap(200f, 200f)
        assertFalse(backOut.zoomed)
        assertEquals(0f, backOut.offsetX, 0.001f)
        assertEquals(0f, backOut.offsetY, 0.001f)
    }
}
