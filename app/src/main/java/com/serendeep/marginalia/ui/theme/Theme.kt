package com.serendeep.marginalia.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.serendeep.marginalia.ink.InkTheme

@Immutable
data class PenPalette(
    val graphite: Color,
    val indigo: Color,
    val rust: Color,
)

val LocalPenPalette = staticCompositionLocalOf {
    PenPalette(PenGraphiteLight, PenIndigoLight, PenRustLight)
}

/** Whether MarginaliaTheme is currently dark, regardless of the system setting. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** Tokens the Material scheme has no slot for. */
@Immutable
data class MarginaliaColors(
    val dimInk: Color,
    val chipText: Color,
    val rowDivider: Color,
    val chipBorder: Color,
    val limeInk: Color,
    val danger: Color,
    val errorInk: Color,
    val codeBg: Color,
    val heat: List<Color>,
    val goalTrack: Color,
    val cardsBorder: Color,
)

internal val DarkExtras = MarginaliaColors(
    dimInk = DimInkDark,
    chipText = Color(0xFFA0A0AB),
    rowDivider = Color(0xFF18181C),
    chipBorder = Color(0xFF2A2A30),
    limeInk = Lime,
    danger = Danger,
    errorInk = Color(0xFFFF8A80),
    codeBg = Color(0x228B7CF6),
    heat = listOf(Color(0xFF222227), Color(0xFF3D4A14), Color(0xFF6E8A1C), Lime),
    goalTrack = Color(0xFF26262B),
    cardsBorder = Color(0xFF2D2756),
)

internal val LightExtras = MarginaliaColors(
    dimInk = DimInkLight,
    chipText = Color(0xFF626170),
    rowDivider = Color(0xFFECE8DF),
    chipBorder = Color(0xFFDDD8CD),
    limeInk = LimeInkLight,
    danger = Color(0xFFC62F3E),
    errorInk = Color(0xFFB3261E),
    codeBg = Color(0x1A6A58E0),
    heat = listOf(Color(0xFFE8E4DA), Color(0xFFD9E8A6), Color(0xFF9DBF2E), Color(0xFF6F9100)),
    goalTrack = Color(0xFFE4E0D6),
    cardsBorder = Color(0xFFD9D3F7),
)

val LocalMarginaliaColors = staticCompositionLocalOf { LightExtras }

val MaterialTheme.marginalia: MarginaliaColors
    @Composable @ReadOnlyComposable get() = LocalMarginaliaColors.current

private val LightPens = PenPalette(PenGraphiteLight, PenIndigoLight, PenRustLight)
private val DarkPens = PenPalette(PenGraphiteDark, PenIndigoDark, PenRustDark)

private val LightColors = lightColorScheme(
    primary = VioletLight,
    onPrimary = Color.White,
    primaryContainer = VioletLight.copy(alpha = 0.14f).compositeOver(SheetLight),
    onPrimaryContainer = InkLight,
    secondary = SoftInkLight,
    onSecondary = Color.White,
    secondaryContainer = VioletLight.copy(alpha = 0.14f).compositeOver(SheetLight),
    onSecondaryContainer = InkLight,
    background = BgLight,
    onBackground = InkLight,
    surface = SheetLight,
    onSurface = InkLight,
    surfaceVariant = Surface2Light,
    onSurfaceVariant = SoftInkLight,
    outline = RuleLight,
    outlineVariant = DividerLight,
    inverseSurface = InkLight,
    inverseOnSurface = BgLight,
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Violet,
    onPrimary = OnViolet,
    primaryContainer = Violet.copy(alpha = 0.20f).compositeOver(SheetDark),
    onPrimaryContainer = InkDark,
    secondary = SoftInkDark,
    onSecondary = BgDark,
    secondaryContainer = Violet.copy(alpha = 0.20f).compositeOver(SheetDark),
    onSecondaryContainer = InkDark,
    background = BgDark,
    onBackground = InkDark,
    surface = SheetDark,
    onSurface = InkDark,
    surfaceVariant = Surface2Dark,
    onSurfaceVariant = SoftInkDark,
    outline = RuleDark,
    outlineVariant = DividerDark,
    inverseSurface = InkDark,
    inverseOnSurface = BgDark,
    error = Danger,
)

@Composable
fun MarginaliaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    InkTheme.dark = darkTheme
    CompositionLocalProvider(
        LocalPenPalette provides if (darkTheme) DarkPens else LightPens,
        LocalDarkTheme provides darkTheme,
        LocalMarginaliaColors provides if (darkTheme) DarkExtras else LightExtras,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = MarginaliaTypography,
            shapes = MarginaliaShapes,
        ) {
            // Without a root Surface, LocalContentColor stays at its default
            // (black) and every text outside a Card/Button ignores the theme.
            Surface(
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                content = content,
            )
        }
    }
}
