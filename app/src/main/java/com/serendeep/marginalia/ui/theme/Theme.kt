package com.serendeep.marginalia.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

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

private val LightPens = PenPalette(PenGraphiteLight, PenIndigoLight, PenRustLight)
private val DarkPens = PenPalette(PenGraphiteDark, PenIndigoDark, PenRustDark)

private val LightColors = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    primaryContainer = AccentLight.copy(alpha = 0.14f).compositeOver(SheetLight),
    onPrimaryContainer = InkLight,
    secondary = SoftInkLight,
    onSecondary = Color.White,
    secondaryContainer = AccentLight.copy(alpha = 0.14f).compositeOver(SheetLight),
    onSecondaryContainer = InkLight,
    background = BgLight,
    onBackground = InkLight,
    surface = SheetLight,
    onSurface = InkLight,
    surfaceVariant = BgLight,
    onSurfaceVariant = SoftInkLight,
    outline = RuleLight,
    outlineVariant = DividerLight,
    inverseSurface = InkLight,
    inverseOnSurface = BgLight,
)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = BgDark,
    primaryContainer = AccentDark.copy(alpha = 0.20f).compositeOver(SheetDark),
    onPrimaryContainer = InkDark,
    secondary = SoftInkDark,
    onSecondary = BgDark,
    secondaryContainer = AccentDark.copy(alpha = 0.20f).compositeOver(SheetDark),
    onSecondaryContainer = InkDark,
    background = BgDark,
    onBackground = InkDark,
    surface = SheetDark,
    onSurface = InkDark,
    surfaceVariant = Color(0xFF1C212A),
    onSurfaceVariant = SoftInkDark,
    outline = RuleDark,
    outlineVariant = DividerDark,
    inverseSurface = InkDark,
    inverseOnSurface = BgDark,
)

@Composable
fun MarginaliaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPenPalette provides if (darkTheme) DarkPens else LightPens,
        LocalDarkTheme provides darkTheme,
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
