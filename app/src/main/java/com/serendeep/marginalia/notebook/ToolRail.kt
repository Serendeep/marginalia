package com.serendeep.marginalia.notebook

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.serendeep.marginalia.ink.InkTool
import com.serendeep.marginalia.ink.Pen
import com.serendeep.marginalia.ui.theme.GlassBorderDark
import com.serendeep.marginalia.ui.theme.GlassBorderLight
import com.serendeep.marginalia.ui.theme.GlassTintDark
import com.serendeep.marginalia.ui.theme.GlassTintLight
import com.serendeep.marginalia.ui.theme.InkLight
import com.serendeep.marginalia.ui.theme.LocalDarkTheme
import com.serendeep.marginalia.ui.theme.LocalPenPalette
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

private val DimSpec = tween<Float>(180)
private const val TOUCH_TARGET_DP = 44
private const val SWATCH_DIAMETER_DP = 18

/** Floating frosted-glass rail: pen swatches, eraser, undo/redo. */
@Composable
fun ToolRail(
    tool: InkTool,
    selectedPen: Pen,
    penDown: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onSelectPen: (Pen) -> Unit,
    onEraser: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val dark = LocalDarkTheme.current
    val iconColor = if (dark) Color(0xFFE8EAEE) else InkLight
    val palette = LocalPenPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val haptics = LocalHapticFeedback.current
    val alpha by animateFloatAsState(if (penDown) 0.25f else 1f, DimSpec, label = "toolRailAlpha")
    val shape = RoundedCornerShape(29.dp)

    Column(
        modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(shape)
            .then(
                // Every wet-ink invalidation would recompute the blur; while the
                // pen is down the rail freezes to a flat glass tint instead.
                if (penDown) {
                    Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                } else {
                    Modifier.hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            tint = HazeTint(if (dark) GlassTintDark else GlassTintLight),
                            blurRadius = 24.dp,
                            noiseFactor = 0.02f,
                        ),
                    ) {
                        inputScale = HazeInputScale.Fixed(0.5f)
                    }
                },
            )
            .border(1.dp, if (dark) GlassBorderDark else GlassBorderLight, shape)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val selectPen: (Pen) -> Unit = {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onSelectPen(it)
        }
        PenSwatch(
            palette.graphite,
            selected = tool == InkTool.PEN && selectedPen == Pen.GRAPHITE,
            glow = accent,
        ) { selectPen(Pen.GRAPHITE) }
        PenSwatch(
            palette.indigo,
            selected = tool == InkTool.PEN && selectedPen == Pen.INDIGO,
            glow = accent,
        ) { selectPen(Pen.INDIGO) }
        PenSwatch(
            palette.rust,
            selected = tool == InkTool.PEN && selectedPen == Pen.RUST,
            glow = accent,
        ) { selectPen(Pen.RUST) }
        HorizontalDivider(
            Modifier.width(24.dp).padding(vertical = 4.dp),
            color = iconColor.copy(alpha = 0.16f),
        )
        EraserButton(selected = tool == InkTool.ERASER, iconColor = iconColor) {
            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
            onEraser()
        }
        HistoryButton(enabled = canUndo, glyph = UndoGlyph, tint = iconColor) {
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
            onUndo()
        }
        HistoryButton(enabled = canRedo, glyph = RedoGlyph, tint = iconColor) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onRedo()
        }
    }
}

@Composable
private fun PenSwatch(color: Color, selected: Boolean, glow: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(TOUCH_TARGET_DP.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(TOUCH_TARGET_DP.dp)) {
            val fillRadius = SWATCH_DIAMETER_DP.dp.toPx() / 2f
            if (selected) {
                // Soft luminous halo plus a crisp ring; the glow never touches ink.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = 0.45f), Color.Transparent),
                        center = center,
                        radius = fillRadius * 2.4f,
                    ),
                    radius = fillRadius * 2.4f,
                )
                drawCircle(glow.copy(alpha = 0.9f), radius = fillRadius + 4.dp.toPx(), style = Stroke(2.dp.toPx()))
            }
            drawCircle(color, radius = fillRadius)
        }
    }
}

@Composable
private fun EraserButton(selected: Boolean, iconColor: Color, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier.size(TOUCH_TARGET_DP.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.25f)),
            )
        }
        Canvas(Modifier.size(22.dp)) {
            val strokeWidth = 2.dp.toPx()
            rotate(-20f) {
                drawRoundRect(
                    color = iconColor,
                    topLeft = Offset(size.width * 0.2f, size.height * 0.22f),
                    size = Size(size.width * 0.6f, size.height * 0.4f),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(strokeWidth, cap = StrokeCap.Round),
                )
            }
            drawLine(
                color = iconColor,
                start = Offset(size.width * 0.15f, size.height * 0.85f),
                end = Offset(size.width * 0.85f, size.height * 0.85f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

// Standard Material undo/redo outlines, embedded as path data so no icon
// library is needed. Fill is white; the Icon tint applies the themed color.
private val UndoGlyph = historyGlyph(
    "undo",
    "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z",
)
private val RedoGlyph = historyGlyph(
    "redo",
    "M18.4,10.6C16.55,8.99 14.15,8 11.5,8c-4.65,0 -8.58,3.03 -9.96,7.22L3.9,16c1.05,-3.19 4.05,-5.5 7.6,-5.5 1.95,0 3.73,0.72 5.12,1.88L13,16h9V7l-3.6,3.6z",
)

private fun historyGlyph(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 22.dp,
        defaultHeight = 22.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(pathData),
        fill = SolidColor(Color.White),
    ).build()

@Composable
private fun HistoryButton(enabled: Boolean, glyph: ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(TOUCH_TARGET_DP.dp).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            glyph,
            contentDescription = glyph.name,
            tint = tint,
            modifier = Modifier.graphicsLayer { this.alpha = if (enabled) 1f else 0.35f },
        )
    }
}
