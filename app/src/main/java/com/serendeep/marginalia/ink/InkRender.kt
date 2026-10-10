package com.serendeep.marginalia.ink

import androidx.compose.ui.graphics.toArgb
import androidx.ink.strokes.Stroke
import com.serendeep.marginalia.data.InkStroke
import com.serendeep.marginalia.ui.theme.PenGraphiteDark
import com.serendeep.marginalia.ui.theme.PenGraphiteLight
import com.serendeep.marginalia.ui.theme.PenIndigoDark
import com.serendeep.marginalia.ui.theme.PenIndigoLight
import com.serendeep.marginalia.ui.theme.PenRustDark
import com.serendeep.marginalia.ui.theme.PenRustLight

/** Rebuild a drawable stroke from stored ink and brush, optionally recolored. */
fun InkStroke.toStroke(colorOverride: Int? = null): Stroke =
    Stroke(Pens.pen(colorOverride ?: InkTheme.adapt(brushColor.toInt()), brushSizeDp), batch)

/**
 * Strokes keep the colour they were written in. Theme-palette pens are drawn in the current
 * theme's shade instead, so notes written on dark paper stay legible on light paper and back.
 */
object InkTheme {
    @Volatile var dark = true

    private val pairs = listOf(
        PenGraphiteLight to PenGraphiteDark,
        PenIndigoLight to PenIndigoDark,
        PenRustLight to PenRustDark,
    ).map { (light, dark) -> (light.toArgb() and RGB) to (dark.toArgb() and RGB) }

    fun adapt(argb: Int, dark: Boolean = this.dark): Int {
        val rgb = argb and RGB
        val pair = pairs.firstOrNull { rgb == it.first || rgb == it.second } ?: return argb
        return (argb and RGB.inv()) or (if (dark) pair.second else pair.first)
    }

    private const val RGB = 0x00FFFFFF
}
