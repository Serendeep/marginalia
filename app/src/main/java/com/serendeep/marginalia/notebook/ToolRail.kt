package com.serendeep.marginalia.notebook

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.serendeep.marginalia.ink.EraserSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.serendeep.marginalia.ink.LASER_ARGB
import com.serendeep.marginalia.ink.PenWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
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
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

private val DimSpec = tween<Float>(180)
private const val TOUCH_TARGET_DP = 44
private const val SWATCH_DIAMETER_DP = 18

/** Floating frosted-glass rail: pens, highlighter, laser, eraser, select, card lasso, undo/redo. */
@Composable
fun ToolRail(
    tool: InkTool,
    selectedPen: Pen,
    swatchColors: List<Color>,
    choiceColors: List<Int>,
    penWidth: PenWidth,
    onPenWidth: (PenWidth) -> Unit,
    onSwatchChoice: (slot: Int, choice: Int) -> Unit,
    penDown: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onSelectPen: (Pen) -> Unit,
    onHighlighter: () -> Unit,
    onLaser: () -> Unit,
    onEraser: () -> Unit,
    eraserSize: EraserSize,
    onEraserSize: (EraserSize) -> Unit,
    onSelect: () -> Unit,
    onLasso: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val dark = LocalDarkTheme.current
    val iconColor = if (dark) Color(0xFFE8EAEE) else InkLight
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
        Pen.entries.forEach { pen ->
            PenSwatch(
                color = swatchColors[pen.ordinal],
                selected = tool == InkTool.PEN && selectedPen == pen,
                glow = accent,
                ringColor = iconColor.copy(alpha = 0.4f),
                description = "Pen ${pen.ordinal + 1}",
                choiceColors = choiceColors,
                onPick = { onSwatchChoice(pen.ordinal, it) },
            ) { selectPen(pen) }
        }
        HighlighterButton(selected = tool == InkTool.HIGHLIGHTER, iconColor = iconColor) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onHighlighter()
        }
        LaserButton(selected = tool == InkTool.LASER) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onLaser()
        }
        HorizontalDivider(
            Modifier.width(24.dp).padding(vertical = 4.dp),
            color = iconColor.copy(alpha = 0.16f),
        )
        EraserButton(selected = tool == InkTool.ERASER, iconColor = iconColor) {
            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
            onEraser()
        }
        SelectButton(selected = tool == InkTool.SELECT, iconColor = iconColor) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onSelect()
        }
        LassoButton(selected = tool == InkTool.LASSO, iconColor = iconColor) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onLasso()
        }
        HistoryButton(enabled = canUndo, glyph = Icons.AutoMirrored.Filled.Undo, label = "Undo", tint = iconColor) {
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
            onUndo()
        }
        HistoryButton(enabled = canRedo, glyph = Icons.AutoMirrored.Filled.Redo, label = "Redo", tint = iconColor) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onRedo()
        }
        HorizontalDivider(
            Modifier.width(24.dp).padding(vertical = 4.dp),
            color = iconColor.copy(alpha = 0.16f),
        )
        // One fixed slot for the active tool's sizes, so no rail button ever moves.
        Column(Modifier.height(SIZE_SLOT_DP.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (tool) {
                InkTool.PEN -> {
                PenWidth.entries.forEach { width ->
                    SizeDot(
                        dot = when (width) {
                            PenWidth.FINE -> 4.dp
                            PenWidth.MEDIUM -> 8.dp
                            PenWidth.BOLD -> 13.dp
                        },
                        selected = width == penWidth,
                        iconColor = iconColor,
                        description = "Pen width ${width.name.lowercase()}",
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onPenWidth(width)
                    }
                }
                }
                InkTool.ERASER -> {
                EraserSize.entries.forEach { size ->
                    SizeDot(
                        dot = when (size) {
                            EraserSize.SMALL -> 6.dp
                            EraserSize.MEDIUM -> 11.dp
                            EraserSize.LARGE -> 18.dp
                        },
                        selected = size == eraserSize,
                        iconColor = iconColor,
                        description = "Eraser size ${size.name.lowercase()}",
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onEraserSize(size)
                    }
                }
                }
                else -> Unit
            }
        }
    }
}

private const val SIZE_SLOT_DP = 108

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PenSwatch(
    color: Color,
    selected: Boolean,
    glow: Color,
    ringColor: Color,
    description: String,
    choiceColors: List<Int>,
    onPick: (Int) -> Unit,
    onClick: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val gap = with(LocalDensity.current) { 12.dp.roundToPx() }
    Box(
        Modifier
            .size(TOUCH_TARGET_DP.dp)
            .semantics { contentDescription = "$description, long press to change colour" }
            .combinedClickable(onClick = onClick, onLongClick = { open = true }),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(SWATCH_DIAMETER_DP.dp + 10.dp)
                .then(if (selected) Modifier.border(2.dp, glow, CircleShape) else Modifier)
                .padding(5.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, ringColor, CircleShape),
        )
        if (open) {
            val position = remember(gap) {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ) = IntOffset(
                        (anchorBounds.left - popupContentSize.width - gap).coerceAtLeast(gap),
                        (anchorBounds.center.y - popupContentSize.height / 2)
                            .coerceIn(gap, (windowSize.height - popupContentSize.height - gap).coerceAtLeast(gap)),
                    )
                }
            }
            Popup(popupPositionProvider = position, onDismissRequest = { open = false }) {
                val shape = RoundedCornerShape(18.dp)
                Column(
                    Modifier
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, glassBorder(), shape)
                        .padding(8.dp),
                ) {
                    ColorDots(choiceColors, perRow = 4) {
                        open = false
                        onPick(it)
                    }
                }
            }
        }
    }
}

/** Colour choices as tappable dots; [onPick] receives the index into [colors]. */
@Composable
internal fun ColorDots(colors: List<Int>, perRow: Int = colors.size, onPick: (Int) -> Unit) {
    val ring = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    Column {
        colors.indices.chunked(perRow).forEach { row ->
            Row {
                row.forEach { i ->
                    Box(
                        Modifier
                            .size(44.dp)
                            .semantics { contentDescription = "Colour ${i + 1}" }
                            .clickable { onPick(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(colors[i]))
                                .border(1.dp, ring, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LaserButton(selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(TOUCH_TARGET_DP.dp)
            .semantics { contentDescription = "Laser pointer" }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(34.dp).border(2.dp, accent, CircleShape))
        Canvas(Modifier.size(22.dp)) {
            val red = Color(LASER_ARGB)
            val c = center
            drawCircle(red.copy(alpha = 0.3f), size.minDimension * 0.42f, c)
            drawCircle(red, size.minDimension * 0.22f, c)
            for (i in 0 until 8) {
                val a = i * (Math.PI / 4).toFloat()
                val inner = size.minDimension * 0.34f
                val outer = size.minDimension * 0.5f
                drawLine(
                    red,
                    Offset(c.x + inner * kotlin.math.cos(a), c.y + inner * kotlin.math.sin(a)),
                    Offset(c.x + outer * kotlin.math.cos(a), c.y + outer * kotlin.math.sin(a)),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun SelectButton(selected: Boolean, iconColor: Color, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(TOUCH_TARGET_DP.dp)
            .semantics { contentDescription = "Select and move ink" }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(34.dp).border(2.dp, accent, CircleShape))
        Canvas(Modifier.size(22.dp)) {
            val w = size.width
            val h = size.height
            val loop = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.22f, h * 0.55f)
                cubicTo(w * 0.05f, h * 0.25f, w * 0.55f, h * 0.0f, w * 0.82f, h * 0.22f)
                cubicTo(w * 1.02f, h * 0.45f, w * 0.7f, h * 0.78f, w * 0.38f, h * 0.72f)
            }
            drawPath(loop, iconColor, style = Stroke(1.75.dp.toPx(), cap = StrokeCap.Round))
            drawLine(iconColor, Offset(w * 0.38f, h * 0.72f), Offset(w * 0.22f, h * 0.95f), strokeWidth = 1.75.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun EraserButton(selected: Boolean, iconColor: Color, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(TOUCH_TARGET_DP.dp)
            .semantics { contentDescription = "Eraser" }
            .clickable(onClick = onClick),
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
private fun LassoButton(selected: Boolean, iconColor: Color, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(TOUCH_TARGET_DP.dp)
            .semantics { contentDescription = "Lasso: make a card" }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(34.dp).border(2.dp, accent, CircleShape))
        Canvas(Modifier.size(22.dp)) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx()))
            drawRoundRect(
                color = iconColor,
                topLeft = Offset(size.width * 0.14f, size.height * 0.2f),
                size = Size(size.width * 0.72f, size.height * 0.6f),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(1.75.dp.toPx(), pathEffect = dash),
            )
        }
    }
}

@Composable
private fun HistoryButton(enabled: Boolean, glyph: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(TOUCH_TARGET_DP.dp).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            glyph,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.graphicsLayer { this.alpha = if (enabled) 1f else 0.35f },
        )
    }
}

@Composable
private fun SizeDot(dot: Dp, selected: Boolean, iconColor: Color, description: String, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(width = TOUCH_TARGET_DP.dp, height = 34.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(dot)
                .clip(CircleShape)
                .background(if (selected) accent else iconColor.copy(alpha = 0.45f)),
        )
    }
}
