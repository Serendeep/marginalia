package com.serendeep.marginalia.ink

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import androidx.input.motionprediction.MotionEventPredictor

enum class InkTool { PEN, ERASER }

/**
 * A note surface. The stylus draws or erases; a single-finger drag scrolls the
 * sheet vertically. Holding the stylus barrel button erases regardless of [tool].
 *
 * Strokes are stored in canvas space: wet ink happens in screen coordinates, and
 * a finished stroke is shifted down by [canvasOffset] before it is handed out, so
 * persisted geometry is independent of where the sheet was scrolled at the time.
 * Dry rendering shifts everything back up by the current offset.
 */
@Composable
fun InkCanvas(
    strokes: List<Stroke>,
    tool: InkTool,
    penColor: Int,
    penSizePx: Float,
    canvasOffset: Float,
    onStrokeFinished: (Stroke) -> Unit,
    onErase: (x: Float, y: Float) -> Unit,
    onScrollBy: (deltaPx: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onFinished by rememberUpdatedState(onStrokeFinished)
    val onEraseAt by rememberUpdatedState(onErase)
    val onScroll by rememberUpdatedState(onScrollBy)
    val currentTool by rememberUpdatedState(tool)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            InkViewContainer(context).also { container ->
                val inkView = container.inkView
                inkView.eagerInit()
                inkView.addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
                    override fun onStrokesFinished(
                        finished: Map<InProgressStrokeId, Stroke>,
                    ) {
                        val dropped = finished.values.map { it.shiftedY(container.canvasOffset) }
                        container.dryView.addHandoffStrokes(dropped)
                        container.dryView.invalidate()
                        inkView.removeFinishedStrokes(finished.keys)
                        dropped.forEach(onFinished)
                    }
                })
                val touch = InkTouchHandler(inkView, MotionEventPredictor.newInstance(inkView))
                inkView.setOnTouchListener { _, event ->
                    touch.onTouch(
                        event = event,
                        brush = Pens.pen(penColor, penSizePx),
                        erasing = currentTool == InkTool.ERASER,
                        // Erasing works on stored strokes, so hit-test in canvas space.
                        onErase = { x, y -> onEraseAt(x, y + container.canvasOffset) },
                        onScrollBy = onScroll,
                    )
                }
            }
        },
        update = { container ->
            container.canvasOffset = canvasOffset
            container.dryView.setCanvasOffset(canvasOffset)
            container.dryView.setStrokes(strokes)
        },
    )
}

/** Same stroke with its recorded points moved down by [dy]. */
private fun Stroke.shiftedY(dy: Float): Stroke {
    if (dy == 0f) return this
    val moved = MutableStrokeInputBatch()
    for (i in 0 until inputs.size) {
        val p = inputs.get(i)
        moved.add(
            StrokeInput.create(
                p.x, p.y + dy, p.elapsedTimeMillis, p.toolType,
                p.strokeUnitLengthCm, p.pressure, p.tiltRadians, p.orientationRadians,
            ),
        )
    }
    return Stroke(brush, moved.toImmutable())
}

private class InkViewContainer(context: Context) : ViewGroup(context) {
    val dryView = DryInkView(context)
    val inkView = InProgressStrokesView(context)
    var canvasOffset = 0f

    init {
        addView(dryView)
        addView(inkView)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        val childWidth = MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY)
        val childHeight = MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
        dryView.measure(childWidth, childHeight)
        inkView.measure(childWidth, childHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        dryView.layout(0, 0, right - left, bottom - top)
        inkView.layout(0, 0, right - left, bottom - top)
    }
}

private class DryInkView(context: Context) : View(context) {
    private val renderer = CanvasStrokeRenderer.create(false)
    private val strokeToScreen = Matrix()
    private var canvasOffset = 0f
    private var strokes: List<Stroke> = emptyList()
    private var handoffStrokes: List<Stroke> = emptyList()

    fun setStrokes(value: List<Stroke>) {
        strokes = value
        handoffStrokes = handoffStrokes.filter { it !in value }
        invalidate()
    }

    fun addHandoffStrokes(value: Collection<Stroke>) {
        handoffStrokes = handoffStrokes + value
    }

    fun setCanvasOffset(value: Float) {
        if (canvasOffset != value) {
            canvasOffset = value
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Strokes live in canvas space; slide them up by the scroll offset.
        strokeToScreen.setTranslate(0f, -canvasOffset)
        val save = canvas.save()
        canvas.translate(0f, -canvasOffset)
        strokes.forEach { renderer.draw(canvas, it, strokeToScreen) }
        handoffStrokes.filter { it !in strokes }.forEach { renderer.draw(canvas, it, strokeToScreen) }
        canvas.restoreToCount(save)
    }
}

private class InkTouchHandler(
    private val view: InProgressStrokesView,
    private val predictor: MotionEventPredictor,
) {
    private var strokeId: InProgressStrokeId? = null
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var fingerY = 0f

    fun onTouch(
        event: MotionEvent,
        brush: Brush,
        erasing: Boolean,
        onErase: (Float, Float) -> Unit,
        onScrollBy: (Float) -> Unit,
    ): Boolean {
        if (event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
            // Fingers never ink; a single-finger drag scrolls the sheet.
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> fingerY = event.y
                MotionEvent.ACTION_MOVE -> {
                    onScrollBy(fingerY - event.y)
                    fingerY = event.y
                }
            }
            return true
        }

        val buttonHeld = event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0
        if (erasing || buttonHeld) {
            // Never leave a half-drawn stroke behind when the tool flips mid-contact.
            strokeId?.let { view.cancelStroke(it, event) }
            strokeId = null
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> onErase(event.x, event.y)
            }
            return true
        }

        predictor.record(event)
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = event.getPointerId(0)
                strokeId = view.startStroke(event, pointerId, brush)
                true
            }

            MotionEvent.ACTION_MOVE -> {
                val id = strokeId ?: return false
                val predicted = predictor.predict()
                view.addToStroke(event, pointerId, id, predicted)
                predicted?.recycle()
                true
            }

            MotionEvent.ACTION_UP -> {
                val id = strokeId ?: return false
                view.finishStroke(event, pointerId, id)
                strokeId = null
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                strokeId?.let { view.cancelStroke(it, event) }
                strokeId = null
                true
            }

            else -> false
        }
    }
}
