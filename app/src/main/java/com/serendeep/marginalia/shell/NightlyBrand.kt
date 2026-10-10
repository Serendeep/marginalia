package com.serendeep.marginalia.shell

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.serendeep.marginalia.ui.theme.LimeInkLight
import com.serendeep.marginalia.ui.theme.LocalDarkTheme
import com.serendeep.marginalia.ui.theme.Violet
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SYNODIC_DAYS = 29.530588853
private const val DAY_MS = 86_400_000.0
// The new moon of 2000-01-06 18:14 UTC.
private const val REFERENCE_NEW_MOON_MS = 947_182_440_000L

private val SkyTop = Color(0xFF24204A)
private val SkyMid = Color(0xFF0F0E1C)
private val MoonLight = Color(0xFFF3EFD8)
private val MoonDark = Color(0xFF2A2740)
private val NightLime = Color(0xFFC6F432)

// Pre-dawn: the same sky on paper, in pale lavender and muted violet.
private val DawnTop = Color(0xFFD8D2FA)
private val DawnMid = Color(0xFFEAE6FB)
private val DawnMoonLit = Color(0xFFA99DF8)
private val DawnMoonShade = Color(0xFFE2DEF2)

/** [age] in days since the last new moon; [fraction] is the share of the cycle, 0 new and 0.5 full. */
data class MoonPhase(val age: Double, val fraction: Double, val illumination: Double, val name: String) {
    val waxing: Boolean get() = fraction < 0.5
    val label: String get() = "$name, ${(illumination * 100).roundToInt()}% lit"
}

/** Mean-cycle approximation, within about a day of the true phase; plenty for a glance at the sky. */
fun moonPhase(epochMs: Long): MoonPhase {
    val days = (epochMs - REFERENCE_NEW_MOON_MS) / DAY_MS
    val age = days - floor(days / SYNODIC_DAYS) * SYNODIC_DAYS
    val fraction = age / SYNODIC_DAYS
    val illumination = (1 - cos(2 * PI * fraction)) / 2
    val names = listOf(
        "New moon", "Waxing crescent", "First quarter", "Waxing gibbous",
        "Full moon", "Waning gibbous", "Last quarter", "Waning crescent",
    )
    return MoonPhase(age, fraction, illumination, names[((fraction * 8) + 0.5).toInt() % 8])
}

@Composable
private fun animationsOff(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

@Composable
private fun twinkle(): State<Float> {
    if (animationsOff()) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = "twinkle").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6_400, easing = LinearEasing), RepeatMode.Restart),
        label = "twinkle-phase",
    )
}

private class Star(val x: Float, val y: Dp, val r: Float, val offset: Float)

// Kept to the gaps above and below the header row (the name sits on the left, the moon and gear on the right),
// so no star lands on text or on the navigation list.
private val Stars = listOf(
    Star(0.58f, 8.dp, 1.1f, 0.0f), Star(0.74f, 14.dp, 0.8f, 0.45f), Star(0.90f, 6.dp, 0.9f, 0.7f),
    Star(0.66f, 52.dp, 0.8f, 0.2f), Star(0.84f, 56.dp, 0.7f, 0.85f), Star(0.44f, 4.dp, 0.6f, 0.3f),
    Star(0.97f, 44.dp, 0.6f, 0.6f),
)

/** Indigo wash fading into the sidebar, with faint twinkling stars and one hairline constellation. */
@Composable
fun Modifier.nightSky(height: Dp = 220.dp): Modifier {
    val phase by twinkle()
    val dark = LocalDarkTheme.current
    return drawBehind {
        val h = height.toPx().coerceAtMost(size.height)
        val band = Rect(0f, 0f, size.width, h)
        drawIntoCanvas { canvas ->
            // The wash is masked by a vertical fade so it melts into the sidebar instead of ending in an edge.
            canvas.saveLayer(band, Paint())
            drawRect(
                Brush.radialGradient(if (dark) listOf(SkyTop, SkyMid) else listOf(DawnTop, DawnMid), center = Offset(size.width * 0.6f, -h * 0.15f), radius = size.width * 1.05f),
                size = band.size,
            )
            // Eased falloff (roughly 1 - t^2) over the whole band, so there is no visible line where it ends.
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Black, 0.3f to Color.Black.copy(alpha = 0.9f), 0.55f to Color.Black.copy(alpha = 0.6f),
                    0.75f to Color.Black.copy(alpha = 0.3f), 0.9f to Color.Black.copy(alpha = 0.08f), 1f to Color.Transparent,
                    endY = h,
                ),
                size = band.size,
                blendMode = BlendMode.DstIn,
            )
            // Softens the right edge, where the sidebar meets the plain content area.
            drawRect(
                Brush.horizontalGradient(0f to Color.Black, 0.7f to Color.Black, 1f to Color.Black.copy(alpha = 0.35f), endX = size.width),
                size = band.size,
                blendMode = BlendMode.DstIn,
            )
            canvas.restore()
        }
        fun at(s: Star) = Offset(s.x * size.width, s.y.toPx())
        val line = Path().apply {
            moveTo(at(Stars[0]).x, at(Stars[0]).y)
            lineTo(at(Stars[1]).x, at(Stars[1]).y)
            lineTo(at(Stars[2]).x, at(Stars[2]).y)
        }
        drawPath(line, if (dark) Color.White.copy(alpha = 0.12f) else Violet.copy(alpha = 0.22f), style = Stroke(width = 0.6.dp.toPx()))
        Stars.forEach { s ->
            // Each star dims once per cycle at its own offset, so only one or two change at a time.
            val d = abs(((phase + s.offset) % 1f) - 0.5f) * 2f
            drawCircle((if (dark) Color.White else Violet).copy(alpha = if (dark) 0.3f + 0.6f * d else 0.22f + 0.45f * d), radius = s.r.dp.toPx(), center = at(s))
        }
    }
}

/** Four-point star that replaces the square brand dot in Nightly. */
@Composable
fun NightlyStar(size: Dp = 12.dp) {
    val phase by twinkle()
    val ink = if (LocalDarkTheme.current) NightLime else LimeInkLight
    Canvas(Modifier.size(size)) {
        val c = this.size.width / 2
        val inner = c * 0.22f
        val path = Path().apply {
            moveTo(c, 0f)
            lineTo(c + inner, c - inner); lineTo(2 * c, c); lineTo(c + inner, c + inner)
            lineTo(c, 2 * c); lineTo(c - inner, c + inner); lineTo(0f, c); lineTo(c - inner, c - inner)
            close()
        }
        val alpha = 0.75f + 0.25f * sin(phase * 2 * PI).toFloat()
        drawPath(path, ink.copy(alpha = alpha))
    }
}

/** Tonight's moon, drawn from [moonPhase] and refreshed every half hour so it turns over at midnight. */
@Composable
fun NightlyMoon(size: Dp = 18.dp) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30 * 60_000L)
            value = System.currentTimeMillis()
        }
    }
    val moon = remember(now / 3_600_000) { moonPhase(now) }
    val dark = LocalDarkTheme.current
    val lit = if (dark) MoonLight else DawnMoonLit
    val shade = if (dark) MoonDark else DawnMoonShade
    Canvas(Modifier.size(size).semantics { contentDescription = moon.label }) {
        val r = this.size.minDimension / 2 * 0.72f
        val cx = this.size.width / 2
        val cy = this.size.height / 2
        drawCircle(lit.copy(alpha = (0.06 + 0.16 * moon.illumination).toFloat()), radius = r * 1.38f, center = Offset(cx, cy))
        drawCircle(shade, radius = r, center = Offset(cx, cy))
        val disc = Rect(cx - r, cy - r, cx + r, cy + r)
        // The lit half on the sunward side, then the terminator ellipse added (gibbous) or cut away (crescent).
        val half = Path().apply {
            arcTo(disc, if (moon.waxing) -90f else 90f, 180f, true)
            close()
        }
        val rx = abs(cos(2 * PI * moon.fraction)).toFloat() * r
        val terminator = Path().apply { addOval(Rect(cx - rx, cy - r, cx + rx, cy + r)) }
        val litPath = Path().apply {
            op(half, terminator, if (moon.illumination > 0.5) PathOperation.Union else PathOperation.Difference)
        }
        drawPath(litPath, lit)
    }
}
