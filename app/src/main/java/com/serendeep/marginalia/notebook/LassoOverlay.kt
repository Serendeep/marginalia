package com.serendeep.marginalia.notebook

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.serendeep.marginalia.ui.theme.Violet

private const val MIN_SELECTION_PX = 32f

/**
 * Transparent layer that turns a stylus or finger drag into a dashed selection rectangle.
 * It exists only while the lasso tool is active, so the pen path never sees it.
 */
@Composable
fun LassoOverlay(modifier: Modifier = Modifier, onSelected: (Rect) -> Unit) {
    var rect by remember { mutableStateOf<Rect?>(null) }
    val onDone by rememberUpdatedState(onSelected)
    Box(
        modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val start = down.position
                    var end = start
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        end = change.position
                        rect = Rect(start, end).normalized()
                    }
                    val done = Rect(start, end).normalized()
                    rect = null
                    if (done.width >= MIN_SELECTION_PX && done.height >= MIN_SELECTION_PX) onDone(done)
                }
            }
            .drawBehind {
                // Read in the draw phase: dragging redraws this layer, nothing recomposes.
                rect?.let {
                    drawRect(Violet.copy(alpha = 0.12f), it.topLeft, it.size)
                    drawRect(
                        Violet,
                        it.topLeft,
                        it.size,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 9.dp.toPx())),
                        ),
                    )
                }
            },
    )
}

private fun Rect.normalized(): Rect = Rect(minOf(left, right), minOf(top, bottom), maxOf(left, right), maxOf(top, bottom))
