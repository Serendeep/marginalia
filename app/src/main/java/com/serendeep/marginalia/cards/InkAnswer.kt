@file:Suppress("RestrictedApi")

package com.serendeep.marginalia.cards

import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import com.serendeep.marginalia.data.StrokeCodec
import com.serendeep.marginalia.ink.InkCanvas
import com.serendeep.marginalia.ink.InkTool
import com.serendeep.marginalia.ink.Pens
import com.serendeep.marginalia.ui.components.glassBorder
import com.serendeep.marginalia.ui.theme.LocalPenPalette
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.min

/** A handwritten card answer: each stroke's input batch in turn, with no colour. */
fun encodeInkAnswer(strokes: List<Stroke>): ByteArray? {
    if (strokes.isEmpty()) return null
    val out = ByteArrayOutputStream()
    DataOutputStream(out).use { data ->
        data.writeInt(strokes.size)
        strokes.forEach {
            val bytes = StrokeCodec.encode(it.inputs)
            data.writeInt(bytes.size)
            data.write(bytes)
        }
    }
    return out.toByteArray()
}

/** Rebuilds the strokes of a stored answer in [colorArgb]; unreadable bytes give an empty list. */
fun decodeInkAnswer(bytes: ByteArray, colorArgb: Int): List<Stroke> = runCatching {
    DataInputStream(ByteArrayInputStream(bytes)).use { data ->
        List(data.readInt()) {
            val chunk = ByteArray(data.readInt()).also(data::readFully)
            Stroke(Pens.pen(colorArgb, Pens.DEFAULT_SIZE_PX), StrokeCodec.decode(chunk))
        }
    }
}.getOrDefault(emptyList())

/** Uniform scale that fits a [w]x[h] box inside [boxW]x[boxH] without enlarging past [maxScale]. */
internal fun fitScale(w: Float, h: Float, boxW: Float, boxH: Float, maxScale: Float = 1.5f): Float {
    if (w <= 0f || h <= 0f) return 1f
    return min(min(boxW / w, boxH / h), maxScale)
}

/** Stylus-only writing surface for a card's answer. */
@Composable
fun InkAnswerPad(strokes: List<Stroke>, onFinished: (Stroke) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    InkCanvas(
        strokes = strokes,
        tool = InkTool.PEN,
        penColor = LocalPenPalette.current.graphite.toArgb(),
        penSizePx = Pens.DEFAULT_SIZE_PX,
        canvasOffset = 0f,
        onStrokeFinished = onFinished,
        onErase = { _, _ -> },
        onScrollBy = {},
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, glassBorder(), shape),
    )
}

/** Draws a stored answer scaled to fit its box. */
@Composable
fun InkAnswerView(bytes: ByteArray, modifier: Modifier = Modifier) {
    val color = LocalPenPalette.current.graphite.toArgb()
    val strokes = remember(bytes, color) { decodeInkAnswer(bytes, color) }
    val renderer = remember { CanvasStrokeRenderer.create(false) }
    Canvas(modifier) {
        if (strokes.isEmpty()) return@Canvas
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        strokes.forEach { s ->
            for (i in 0 until s.inputs.size) {
                val p = s.inputs.get(i)
                minX = min(minX, p.x)
                maxX = maxOf(maxX, p.x)
                minY = min(minY, p.y)
                maxY = maxOf(maxY, p.y)
            }
        }
        val margin = Pens.DEFAULT_SIZE_PX
        val w = maxX - minX + 2 * margin
        val h = maxY - minY + 2 * margin
        val scale = fitScale(w, h, size.width, size.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                (size.width - w * scale) / 2f - (minX - margin) * scale,
                (size.height - h * scale) / 2f - (minY - margin) * scale,
            )
        }
        drawIntoCanvas { c -> strokes.forEach { renderer.draw(c.nativeCanvas, it, matrix) } }
    }
}
