@file:Suppress("RestrictedApi")

package com.serendeep.marginalia.ink

import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.StrokeInput
import androidx.ink.strokes.StrokeInputBatch

/**
 * Partial erase: removes the parts of a stroke within the eraser radius and
 * returns the surviving pieces as separate input batches, timing intact.
 */
object StrokeEraser {

    /** Result of erasing against one stroke. Unchanged strokes return [untouched] = true. */
    data class EraseResult(val untouched: Boolean, val segments: List<StrokeInputBatch>)

    fun erase(batch: StrokeInputBatch, eraserX: Float, eraserY: Float, radius: Float): EraseResult {
        val radiusSq = radius * radius
        val size = batch.size
        val survivors = ArrayList<StrokeInput>(size)
        var hit = false

        val segments = ArrayList<StrokeInputBatch>()
        fun flush() {
            if (survivors.size >= 2) {
                val piece = MutableStrokeInputBatch()
                survivors.forEach { piece.add(it) }
                segments.add(piece.toImmutable())
            }
            survivors.clear()
        }

        for (i in 0 until size) {
            val p = batch.get(i)
            val dx = p.x - eraserX
            val dy = p.y - eraserY
            if (dx * dx + dy * dy <= radiusSq) {
                hit = true
                flush()
            } else {
                survivors.add(p)
            }
        }
        flush()

        return if (!hit) EraseResult(untouched = true, segments = emptyList())
        else EraseResult(untouched = false, segments = segments)
    }
}
