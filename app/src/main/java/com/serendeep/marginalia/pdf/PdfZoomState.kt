package com.serendeep.marginalia.pdf

/**
 * Zoom transform for the PDF pane. A content point maps to the screen as
 * content * scale + offset, origin at the viewport's top-left. Immutable so it
 * can sit in a single Compose state holder and stay trivially testable.
 */
data class PdfZoomState(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    val zoomed: Boolean get() = scale > MIN_SCALE

    /** One pinch/pan step: scale around the centroid, then pan. */
    fun applyGesture(
        centroidX: Float,
        centroidY: Float,
        panX: Float,
        panY: Float,
        zoomChange: Float,
    ): PdfZoomState {
        val newScale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        // Keep the content point under the centroid fixed while the scale changes.
        val factor = newScale / scale
        return PdfZoomState(
            scale = newScale,
            offsetX = (offsetX - centroidX) * factor + centroidX + panX,
            offsetY = (offsetY - centroidY) * factor + centroidY + panY,
        )
    }

    /** Clamp the offset so scaled content never leaves a gap at a viewport edge. */
    fun clampToContent(
        contentWidth: Float,
        contentHeight: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ): PdfZoomState {
        val minX = minOf(0f, viewportWidth - contentWidth * scale)
        val minY = minOf(0f, viewportHeight - contentHeight * scale)
        return copy(
            offsetX = offsetX.coerceIn(minX, 0f),
            offsetY = offsetY.coerceIn(minY, 0f),
        )
    }

    /** Double tap: zoom in around the tap point, or back out to 1x. */
    fun doubleTap(x: Float, y: Float): PdfZoomState =
        if (zoomed) PdfZoomState() else applyGesture(x, y, 0f, 0f, DOUBLE_TAP_SCALE)

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 5f
        const val DOUBLE_TAP_SCALE = 2.5f
    }
}
