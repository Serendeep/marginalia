package com.serendeep.marginalia.ui.theme

import androidx.compose.ui.graphics.Color

// Liquid Glass. Dark is the primary mode; light is its warm-paper twin.

// Dark mode
val BgDark = Color(0xFF0C0C0E)
val SheetDark = Color(0xFF141417)
val Surface2Dark = Color(0xFF1B1B1F)
val InkDark = Color(0xFFECECF0)
val Violet = Color(0xFF8B7CF6)
val OnViolet = Color(0xFF0E0B1F)
val SoftInkDark = Color(0xFF8A8A94)
val DimInkDark = Color(0xFF55555E)
val RuleDark = Color(0xFF222227)
val DividerDark = Color(0x1FFFFFFF)
val GlassTintDark = Color(0x14FFFFFF)
val GlassBorderDark = Color(0x24FFFFFF)

// Chrome floating over content of unknown brightness (a white PDF page) needs
// a tint that stays dark regardless of what the blur samples.
val GlassSmokeDark = Color(0xB3161A21)

// Light mode: warm paper
val BgLight = Color(0xFFF7F5F0)
val SheetLight = Color(0xFFFFFEFC)
val Surface2Light = Color(0xFFF0EDE6)
val InkLight = Color(0xFF1C1B22)
val VioletLight = Color(0xFF6A58E0)
val SoftInkLight = Color(0xFF626170)
val DimInkLight = Color(0xFF6B6A75)
val RuleLight = Color(0xFFE3DFD6)
val DividerLight = Color(0x1F1C1B22)
val GlassTintLight = Color(0x99FFFFFF)
val GlassBorderLight = Color(0xCCFFFFFF)

// Dot grid overlays for the note sheet
val DotGridLight = InkLight.copy(alpha = 0.10f)
val DotGridDark = Color.White.copy(alpha = 0.07f)

// Pen colors: silver-graphite, periwinkle-indigo, amber-rust families,
// each remapped per mode so ink stays legible on its sheet.
val PenGraphiteLight = Color(0xFF2B333B)
val PenGraphiteDark = Color(0xFFC9CFD8)
val PenIndigoLight = Color(0xFF4A6FB5)
val PenIndigoDark = Color(0xFF7C9BD9)
val PenRustLight = Color(0xFFA9663A)
val PenRustDark = Color(0xFFC98A5E)

// Time and habit UI only: focus tile, streak heatmap, daily-goal ring, brand dot.
val Lime = Color(0xFFC6F432)

// Lime as text or a thin stroke on a light surface.
val LimeInkLight = Color(0xFF5C7600)

val Danger = Color(0xFFFF6B6B)

object CoursePalette {
    val swatches = listOf(
        Color(0xFF43E0F8), // cyan
        Color(0xFFA78BFA), // violet
        Color(0xFFFB7185), // coral
        Color(0xFFFBBF24), // amber
        Color(0xFFA3E635), // lime
        Color(0xFF2DD4BF), // teal
        Color(0xFFF472B6), // pink
        Color(0xFFFB923C), // orange
    )

    fun color(index: Int): Color = swatches[index.mod(swatches.size)]
}
