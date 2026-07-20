package com.serendeep.marginalia.ink

import androidx.ink.strokes.Stroke
import com.serendeep.marginalia.data.InkStroke

/** Rebuild a drawable stroke from stored ink and brush, optionally recolored. */
fun InkStroke.toStroke(colorOverride: Int? = null): Stroke =
    Stroke(Pens.pen(colorOverride ?: brushColor.toInt(), brushSizeDp), batch)
