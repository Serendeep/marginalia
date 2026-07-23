package com.serendeep.marginalia.ui.theme

import androidx.compose.ui.graphics.Color

// Liquid Glass. Dark is the primary mode; light is its frosted-porcelain twin.

// Dark mode
val BgDark = Color(0xFF101318)
val SheetDark = Color(0xFF161A21)
val InkDark = Color(0xFFE8EAEE)
val AccentDark = Color(0xFF7C9BD9)
val SoftInkDark = Color(0xFF8A93A0)
val RuleDark = Color(0xFF262C35)
val DividerDark = Color(0x1FFFFFFF)
val GlassTintDark = Color(0x14FFFFFF)
val GlassBorderDark = Color(0x24FFFFFF)

// Chrome floating over content of unknown brightness (a white PDF page) needs
// a tint that stays dark regardless of what the blur samples.
val GlassSmokeDark = Color(0xB3161A21)

// Light mode
val BgLight = Color(0xFFEEF1F5)
val SheetLight = Color(0xFFF9FAFC)
val InkLight = Color(0xFF1B2027)
val AccentLight = Color(0xFF4A6FB5)
val SoftInkLight = Color(0xFF66707C)
val RuleLight = Color(0xFFD5DBE3)
val DividerLight = Color(0x1F000000)
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
