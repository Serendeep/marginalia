package com.serendeep.marginalia.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes

object Pens {
    const val DEFAULT_COLOR: Int = 0xFF1A1A1A.toInt()
    const val DEFAULT_SIZE_PX: Float = 6f

    private const val EPSILON = 0.1f

    fun pen(colorArgb: Int, sizePx: Float): Brush =
        Brush(StockBrushes.pressurePen(), sizePx, EPSILON).copyWithColorIntArgb(colorArgb)
}

enum class Pen { GRAPHITE, INDIGO, RUST }
