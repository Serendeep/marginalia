@file:Suppress("RestrictedApi")

package com.serendeep.marginalia.notebook

import androidx.compose.runtime.Immutable
import androidx.ink.strokes.Stroke
import com.serendeep.marginalia.data.InkSurface
import com.serendeep.marginalia.ink.Extent
import com.serendeep.marginalia.ink.Pens

/**
 * Strokes picked up by the select tool on one surface. [glow] is a wider violet copy of
 * each stroke drawn beneath it; [bounds] is in the surface's own coordinates.
 */
@Immutable
data class SelectionState(
    val surface: InkSurface,
    val page: Int?,
    val items: List<RenderedStroke>,
    val glow: List<Stroke>,
    val bounds: Extent,
) {
    val ids: Set<String> = items.mapTo(HashSet()) { it.record.id }

    /** Identity of this particular pick, so views can reset gesture state when it changes. */
    val token = Any()

    companion object {
        const val GLOW_ARGB = 0x668B7CF6
        private const val GLOW_EXTRA_PX = 7f

        fun of(surface: InkSurface, page: Int?, items: List<RenderedStroke>): SelectionState {
            var l = Float.MAX_VALUE
            var t = Float.MAX_VALUE
            var r = -Float.MAX_VALUE
            var b = -Float.MAX_VALUE
            for (it in items) {
                val pad = it.record.brushSizeDp / 2f
                val bb = it.record.bounds
                l = minOf(l, bb.left - pad)
                t = minOf(t, bb.top - pad)
                r = maxOf(r, bb.right + pad)
                b = maxOf(b, bb.bottom + pad)
            }
            val glow = items.map { Stroke(Pens.pen(GLOW_ARGB, it.record.brushSizeDp + GLOW_EXTRA_PX), it.record.batch) }
            return SelectionState(surface, page, items, glow, Extent(l, t, r, b))
        }
    }
}
