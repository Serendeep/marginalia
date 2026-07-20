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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.serendeep.marginalia.ink.InkTool
import com.serendeep.marginalia.ink.Pen
import com.serendeep.marginalia.ui.theme.ChromeDark
import com.serendeep.marginalia.ui.theme.ChromeLight
import com.serendeep.marginalia.ui.theme.LocalDarkTheme
import com.serendeep.marginalia.ui.theme.LocalPenPalette

private val IconColor = Color(0xFFEDEFF2)
private val RingColor = Color.White.copy(alpha = 0.85f)
private val DimSpec = tween<Float>(180)
private const val TOUCH_TARGET_DP = 44
private const val SWATCH_DIAMETER_DP = 18

/** Floating vertical icon rail: pen swatches, eraser, undo/redo. */
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
        HistoryButton(enabled = canUndo, glyph = UndoGlyph, onClick = onUndo)
        HistoryButton(enabled = canRedo, glyph = RedoGlyph, onClick = onRedo)
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

// Standard Material undo/redo outlines, embedded as path data so no icon
// library is needed.
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
        fill = SolidColor(IconColor),
    ).build()

@Composable
private fun HistoryButton(enabled: Boolean, glyph: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(TOUCH_TARGET_DP.dp).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            glyph,
            contentDescription = glyph.name,
            tint = Color.Unspecified,
            modifier = Modifier.graphicsLayer { this.alpha = if (enabled) 1f else 0.35f },
        )
    }
}
