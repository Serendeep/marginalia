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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.serendeep.marginalia.ink.InkTool
import com.serendeep.marginalia.ink.Pen
import com.serendeep.marginalia.ui.components.glassBorder
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
    onHighlighter: () -> Unit,
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
            .border(1.dp, glassBorder(), shape)
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
        HighlighterButton(selected = tool == InkTool.HIGHLIGHTER, iconColor = iconColor) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onHighlighter()
        }
        HorizontalDivider(
            Modifier.width(24.dp).padding(vertical = 4.dp),
            color = iconColor.copy(alpha = 0.16f),
        )
        EraserButton(selected = tool == InkTool.ERASER, iconColor = iconColor) {
            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
            onEraser()
        }
        HistoryButton(enabled = canUndo, glyph = Icons.AutoMirrored.Filled.Undo, tint = iconColor) {
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
            onUndo()
        }
        HistoryButton(enabled = canRedo, glyph = Icons.AutoMirrored.Filled.Redo, tint = iconColor) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onRedo()
        }
    }
}

@Composable
private fun HighlighterButton(selected: Boolean, iconColor: Color, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(TOUCH_TARGET_DP.dp)
            .semantics { contentDescription = "Highlighter" }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(34.dp).border(2.dp, accent, CircleShape))
        Canvas(Modifier.size(22.dp)) {
            drawRoundRect(
                color = Color(0xFFF2C84B),
                topLeft = Offset(size.width * 0.18f, size.height * 0.28f),
                size = Size(size.width * 0.64f, size.height * 0.28f),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
            drawLine(
                color = iconColor,
                start = Offset(size.width * 0.2f, size.height * 0.78f),
                end = Offset(size.width * 0.8f, size.height * 0.78f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun PenSwatch(color: Color, selected: Boolean, glow: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(TOUCH_TARGET_DP.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(SWATCH_DIAMETER_DP.dp + 10.dp)
                .then(if (selected) Modifier.border(2.dp, glow, CircleShape) else Modifier)
                .padding(5.dp)
                .clip(CircleShape)
                .background(color),
        )
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
            Box(Modifier.size(34.dp).border(2.dp, accent, CircleShape))
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
