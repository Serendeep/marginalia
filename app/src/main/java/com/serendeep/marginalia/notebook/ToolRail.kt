package com.serendeep.marginalia.notebook

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.serendeep.marginalia.ink.InkTool
import com.serendeep.marginalia.ink.Pen
import com.serendeep.marginalia.ui.theme.ChromeDark
import com.serendeep.marginalia.ui.theme.ChromeLight
import com.serendeep.marginalia.ui.theme.LocalDarkTheme
import com.serendeep.marginalia.ui.theme.LocalPenPalette
import kotlin.math.cos
import kotlin.math.sin

private val IconColor = Color(0xFFEDEFF2)
private val RingColor = Color.White.copy(alpha = 0.85f)
private val DimSpec = tween<Float>(180)
private const val TOUCH_TARGET_DP = 44
private const val SWATCH_DIAMETER_DP = 18

/** Floating vertical icon rail: pen swatches, eraser, undo. */
@Composable
fun ToolRail(
    tool: InkTool,
    selectedPen: Pen,
    penDown: Boolean,
    onSelectPen: (Pen) -> Unit,
    onEraser: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = if (LocalDarkTheme.current) ChromeDark else ChromeLight
    val palette = LocalPenPalette.current
    val alpha by animateFloatAsState(if (penDown) 0.25f else 1f, DimSpec, label = "toolRailAlpha")
    val shape = RoundedCornerShape(22.dp)

    Column(
        modifier
            .graphicsLayer { this.alpha = alpha }
            .shadow(6.dp, shape)
            .background(chrome, shape)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PenSwatch(palette.graphite, selected = tool == InkTool.PEN && selectedPen == Pen.GRAPHITE) {
            onSelectPen(Pen.GRAPHITE)
        }
        PenSwatch(palette.indigo, selected = tool == InkTool.PEN && selectedPen == Pen.INDIGO) {
            onSelectPen(Pen.INDIGO)
        }
        PenSwatch(palette.rust, selected = tool == InkTool.PEN && selectedPen == Pen.RUST) {
            onSelectPen(Pen.RUST)
        }
        HorizontalDivider(
            Modifier.width(24.dp).padding(vertical = 4.dp),
            color = Color.White.copy(alpha = 0.14f),
        )
        EraserButton(selected = tool == InkTool.ERASER, onClick = onEraser)
        UndoButton(onClick = onUndo)
    }
}

@Composable
private fun PenSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(TOUCH_TARGET_DP.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(TOUCH_TARGET_DP.dp)) {
            val fillRadius = SWATCH_DIAMETER_DP.dp.toPx() / 2f
            if (selected) {
                drawCircle(RingColor, radius = fillRadius + 3.dp.toPx(), style = Stroke(2.dp.toPx()))
            }
            drawCircle(color, radius = fillRadius)
        }
    }
}

@Composable
private fun EraserButton(selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier.size(TOUCH_TARGET_DP.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(32.dp)
                    .background(accent.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
            )
        }
        Canvas(Modifier.size(22.dp)) {
            val strokeWidth = 2.dp.toPx()
            rotate(-20f) {
                drawRoundRect(
                    color = IconColor,
                    topLeft = Offset(size.width * 0.2f, size.height * 0.22f),
                    size = Size(size.width * 0.6f, size.height * 0.4f),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(strokeWidth, cap = StrokeCap.Round),
                )
            }
            drawLine(
                color = IconColor,
                start = Offset(size.width * 0.15f, size.height * 0.85f),
                end = Offset(size.width * 0.85f, size.height * 0.85f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun UndoButton(onClick: () -> Unit) {
    Box(
        Modifier.size(TOUCH_TARGET_DP.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val strokeWidth = 2.dp.toPx()
            val inset = 3.dp.toPx()
            val diameter = size.width - inset * 2
            val startAngle = -30f
            drawArc(
                color = IconColor,
                startAngle = startAngle,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(diameter, diameter),
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
            )
            val radius = diameter / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val angleRad = Math.toRadians(startAngle.toDouble())
            val tip = Offset(
                center.x + radius * cos(angleRad).toFloat(),
                center.y + radius * sin(angleRad).toFloat(),
            )
            drawLine(IconColor, tip, Offset(tip.x - 5.dp.toPx(), tip.y - 2.dp.toPx()), strokeWidth, cap = StrokeCap.Round)
            drawLine(IconColor, tip, Offset(tip.x - 1.dp.toPx(), tip.y + 5.dp.toPx()), strokeWidth, cap = StrokeCap.Round)
        }
    }
}
