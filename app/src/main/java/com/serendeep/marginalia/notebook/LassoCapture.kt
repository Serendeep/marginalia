package com.serendeep.marginalia.notebook

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.compose.ui.geometry.Rect
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Longest side of a captured card image. */
const val MAX_CROP_PX = 2048

/** Capture scale: twice the on-screen size, shrunk when that would pass [MAX_CROP_PX]. */
fun cropScale(widthPx: Float, heightPx: Float): Float =
    min(2f, MAX_CROP_PX / max(widthPx, heightPx).coerceAtLeast(1f))

/**
 * Renders the ink inside [area] (note-canvas space) onto a sheet-coloured bitmap at 2x.
 * Only strokes whose bounds touch the area are drawn.
 */
fun renderMarginCrop(strokes: List<RenderedStroke>, area: Rect, sheetColor: Int): Bitmap {
    val scale = cropScale(area.width, area.height)
    val bitmap = Bitmap.createBitmap(
        ceil(area.width * scale).toInt().coerceAtLeast(1),
        ceil(area.height * scale).toInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    canvas.drawColor(sheetColor)
    canvas.scale(scale, scale)
    canvas.translate(-area.left, -area.top)
    val renderer = CanvasStrokeRenderer.create(false)
    val identity = Matrix()
    val pad = 24f
    for (item in strokes) {
        val b = item.record.bounds
        if (b.right < area.left - pad || b.left > area.right + pad || b.bottom < area.top - pad || b.top > area.bottom + pad) continue
        renderer.draw(canvas, item.stroke, identity)
    }
    return bitmap
}
