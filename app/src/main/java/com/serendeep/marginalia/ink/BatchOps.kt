@file:Suppress("RestrictedApi")

package com.serendeep.marginalia.ink

import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.StrokeInput
import androidx.ink.strokes.StrokeInputBatch
import com.serendeep.marginalia.data.Box
import com.serendeep.marginalia.data.InkStroke

fun StrokeInputBatch.toPoints(): List<InkPt> =
    List(size) { i -> get(i).let { InkPt(it.x, it.y, it.elapsedTimeMillis) } }

/** A stylus batch through [points] at a fixed [pressure], times kept non-decreasing. */
fun List<InkPt>.toBatch(pressure: Float): StrokeInputBatch {
    val batch = MutableStrokeInputBatch()
    var last = 0L
    for (p in this) {
        last = maxOf(last, p.t)
        batch.add(
            StrokeInput.create(
                p.x, p.y, last, InputToolType.STYLUS, StrokeInput.NO_STROKE_UNIT_LENGTH,
                pressure, StrokeInput.NO_TILT, StrokeInput.NO_ORIENTATION,
            ),
        )
    }
    return batch.toImmutable()
}

/** The same inputs with positions mapped through [t]; pressure, tilt and timing are kept. */
fun StrokeInputBatch.transformed(t: StrokeTransform): StrokeInputBatch {
    val out = MutableStrokeInputBatch()
    for (i in 0 until size) {
        val p = get(i)
        out.add(
            StrokeInput.create(
                t.x(p.x), t.y(p.y), p.elapsedTimeMillis, p.toolType,
                p.strokeUnitLengthCm, p.pressure, p.tiltRadians, p.orientationRadians,
            ),
        )
    }
    return out.toImmutable()
}

fun StrokeInputBatch.bounds(): Box {
    if (size == 0) return Box(0f, 0f, 0f, 0f)
    var l = Float.MAX_VALUE
    var t = Float.MAX_VALUE
    var r = -Float.MAX_VALUE
    var b = -Float.MAX_VALUE
    for (i in 0 until size) {
        val p = get(i)
        if (p.x < l) l = p.x
        if (p.y < t) t = p.y
        if (p.x > r) r = p.x
        if (p.y > b) b = p.y
    }
    return Box(l, t, r, b)
}

/** The stroke moved and scaled by [t]; the brush scales with it so handwriting keeps its weight. */
fun InkStroke.transformed(t: StrokeTransform): InkStroke {
    val moved = batch.transformed(t)
    return copy(
        batch = moved,
        bounds = moved.bounds(),
        brushSizeDp = (brushSizeDp * t.scale).coerceAtLeast(MIN_BRUSH_PX),
    )
}

/** Keeps the stroke's alpha (highlighters are translucent) and swaps its colour. */
fun InkStroke.recolored(rgb: Int): InkStroke =
    copy(brushColor = ((brushColor and 0xFF000000L) or (rgb.toLong() and 0x00FFFFFFL)))

private const val MIN_BRUSH_PX = 1.5f
