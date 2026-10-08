@file:Suppress("RestrictedApi")

package com.serendeep.marginalia.notebook

import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import com.serendeep.marginalia.ink.Extent
import com.serendeep.marginalia.ink.StrokeTransform
import com.serendeep.marginalia.ui.components.glassBorder
import com.serendeep.marginalia.ui.theme.MonoFamily
import com.serendeep.marginalia.ui.theme.Violet
import kotlin.math.abs
import kotlin.math.roundToInt

/** What the selection layer can ask of the notebook. Coordinates are the surface's own. */
class SelectionActions(
    val onLasso: (page: Int?, polygon: List<Pair<Float, Float>>) -> Unit,
    val onTransform: (StrokeTransform) -> Unit,
    val onRecolor: (rgb: Int) -> Unit,
    val onDuplicate: () -> Unit,
    val onDelete: () -> Unit,
    val onClear: () -> Unit,
)

private const val MIN_SCALE = 0.2f
private const val MAX_SCALE = 6f
private const val MIN_LASSO_POINTS = 3

private enum class Drag { MOVE, SCALE, LASSO }

/**
 * Freeform lasso, selection box and mini toolbar for one surface. Everything it draws
 * lives in this layer, so a drag redraws only this canvas; the strokes underneath are
 * touched once, when the gesture ends.
 *
 * [offsetY] is how far the surface is scrolled (0 for a PDF page); layer y plus
 * [offsetY] is the surface y that strokes are stored in.
 */
@Composable
fun SelectionLayer(
    selection: SelectionState?,
    page: Int?,
    offsetY: () -> Float,
    colors: List<Int>,
    actions: SelectionActions,
    onCard: (Extent) -> Unit,
    onText: (Extent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sel by rememberUpdatedState(selection)
    val scroll by rememberUpdatedState(offsetY)
    val act by rememberUpdatedState(actions)
    val lasso = remember { mutableStateListOf<Offset>() }
    var preview by remember(selection?.token) { mutableStateOf(StrokeTransform.IDENTITY) }
    var dragging by remember { mutableStateOf(false) }
    var layerSize by remember { mutableStateOf(IntSize.Zero) }
    var toolbarSize by remember { mutableStateOf(IntSize.Zero) }
    val renderer = remember { CanvasStrokeRenderer.create(false) }
    val matrix = remember { Matrix() }

    Box(modifier.fillMaxSize().onSizeChanged { layerSize = it }) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val slop = viewConfiguration.touchSlop
                    val handleReach = 28.dp.toPx()
                    val grab = 10.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val start = down.position
                        val state = sel
                        val oy = scroll()
                        val box = state?.bounds
                        val mode = when {
                            box != null && abs(start.x - box.right) <= handleReach && abs(start.y + oy - box.bottom) <= handleReach -> Drag.SCALE
                            box != null && box.inflated(grab).contains(start.x, start.y + oy) -> Drag.MOVE
                            else -> Drag.LASSO
                        }
                        var end = start
                        var moved = false
                        lasso.clear()
                        if (mode == Drag.LASSO) lasso.add(start)
                        dragging = true
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            end = change.position
                            if (!moved && (end - start).getDistance() > slop) moved = true
                            if (!moved) continue
                            when (mode) {
                                Drag.MOVE -> preview = StrokeTransform.move(end.x - start.x, end.y - start.y)
                                Drag.SCALE -> {
                                    val b = box!!
                                    val sx = (end.x - b.left) / (b.right - b.left).coerceAtLeast(1f)
                                    val sy = (end.y + oy - b.top) / (b.bottom - b.top).coerceAtLeast(1f)
                                    preview = StrokeTransform.scaleAbout(((sx + sy) / 2f).coerceIn(MIN_SCALE, MAX_SCALE), b.left, b.top)
                                }
                                Drag.LASSO -> lasso.add(end)
                            }
                        }
                        dragging = false
                        when (mode) {
                            Drag.LASSO -> {
                                val path = lasso.map { it.x to it.y + oy }
                                lasso.clear()
                                if (!moved) act.onClear() else if (path.size >= MIN_LASSO_POINTS) act.onLasso(page, path)
                            }
                            else -> if (moved && preview != StrokeTransform.IDENTITY) act.onTransform(preview) else preview = StrokeTransform.IDENTITY
                        }
                    }
                },
        ) {
            val oy = scroll()
            drawSelection(sel, preview, oy, renderer, matrix)
            if (lasso.size > 1) {
                val path = Path().apply {
                    moveTo(lasso[0].x, lasso[0].y)
                    for (i in 1 until lasso.size) lineTo(lasso[i].x, lasso[i].y)
                }
                drawPath(path, Violet.copy(alpha = 0.1f))
                drawPath(path, Violet, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()))))
            }
        }

        val shown = selection
        if (shown != null && !dragging) {
            MiniToolbar(
                colors = colors,
                actions = actions,
                onCard = { onCard(shown.bounds) },
                onText = { onText(shown.bounds) },
                modifier = Modifier
                    .onSizeChanged { toolbarSize = it }
                    .offset {
                        val b = shown.bounds
                        val oy = scroll()
                        val top = preview.y(b.top) - oy
                        val bottom = preview.y(b.bottom) - oy
                        val gap = 12.dp.toPx()
                        val margin = 8.dp.toPx()
                        val centre = (preview.x(b.left) + preview.x(b.right)) / 2f
                        val x = (centre - toolbarSize.width / 2f)
                            .coerceIn(margin, (layerSize.width - toolbarSize.width - margin).coerceAtLeast(margin))
                        val above = top - toolbarSize.height - gap
                        val y = if (above >= margin) {
                            above
                        } else {
                            (bottom + gap).coerceAtMost((layerSize.height - toolbarSize.height - margin).coerceAtLeast(margin))
                        }
                        IntOffset(x.roundToInt(), y.roundToInt())
                    },
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSelection(
    selection: SelectionState?,
    preview: StrokeTransform,
    oy: Float,
    renderer: CanvasStrokeRenderer,
    matrix: Matrix,
) {
    selection ?: return
    matrix.setScale(preview.scale, preview.scale)
    matrix.postTranslate(preview.dx, preview.dy - oy)
    drawIntoCanvas { canvas ->
        selection.glow.forEach { renderer.draw(canvas.nativeCanvas, it, matrix) }
        selection.items.forEach { renderer.draw(canvas.nativeCanvas, it.stroke, matrix) }
    }
    val b = selection.bounds
    val topLeft = Offset(preview.x(b.left), preview.y(b.top) - oy)
    val size = Size((b.right - b.left) * preview.scale, (b.bottom - b.top) * preview.scale)
    drawRect(Violet.copy(alpha = 0.07f), topLeft, size)
    drawRect(
        Violet, topLeft, size,
        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 6.dp.toPx()))),
    )
    val handle = Offset(topLeft.x + size.width, topLeft.y + size.height)
    drawCircle(androidx.compose.ui.graphics.Color.White, 9.dp.toPx(), handle)
    drawCircle(Violet, 7.dp.toPx(), handle)
}

@Composable
private fun MiniToolbar(
    colors: List<Int>,
    actions: SelectionActions,
    onCard: () -> Unit,
    onText: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .border(1.dp, glassBorder(), shape)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (picking) {
            ColorDots(colors) {
                picking = false
                actions.onRecolor(it)
            }
        } else {
            ToolbarAction("Colour", "Recolour selection") { picking = true }
            ToolbarAction("Copy", "Duplicate selection", actions.onDuplicate)
            ToolbarAction("Delete", "Delete selection", actions.onDelete)
            ToolbarAction("Text", "Convert selection to text", onText)
            ToolbarAction("Card", "Make card from selection", onCard)
        }
    }
}

@Composable
private fun ToolbarAction(label: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(44.dp)
            .semantics { contentDescription = description }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.uppercase(),
            fontFamily = MonoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
