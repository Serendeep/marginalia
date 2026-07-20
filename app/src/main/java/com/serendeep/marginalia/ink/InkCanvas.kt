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
import androidx.ink.strokes.Stroke
import androidx.input.motionprediction.MotionEventPredictor

enum class InkTool { PEN, ERASER }

/**
 * A note surface. The stylus draws or erases; fingers are ignored so they stay
 * free to scroll. Holding the stylus barrel button erases regardless of [tool].
 */
@Composable
fun InkCanvas(
    strokes: List<Stroke>,
    tool: InkTool,
    penColor: Int,
    penSizePx: Float,
    onStrokeFinished: (Stroke) -> Unit,
    onErase: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onFinished by rememberUpdatedState(onStrokeFinished)
    val onEraseAt by rememberUpdatedState(onErase)
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
                        container.dryView.addHandoffStrokes(finished.values)
                        container.dryView.invalidate()
                        inkView.removeFinishedStrokes(finished.keys)
                        finished.values.forEach(onFinished)
                    }
                })
                val touch = InkTouchHandler(inkView, MotionEventPredictor.newInstance(inkView))
                inkView.setOnTouchListener { _, event ->
                    touch.onTouch(
                        event = event,
                        brush = Pens.pen(penColor, penSizePx),
                        erasing = currentTool == InkTool.ERASER,
                        onErase = onEraseAt,
                    )
                }
            }
        },
        update = { container ->
            container.dryView.setStrokes(strokes)
        },
    )
}

private class InkViewContainer(context: Context) : ViewGroup(context) {
    val dryView = DryInkView(context)
    val inkView = InProgressStrokesView(context)

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
    private val identity = Matrix()
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        strokes.forEach { renderer.draw(canvas, it, identity) }
        handoffStrokes.filter { it !in strokes }.forEach { renderer.draw(canvas, it, identity) }
    }
}

private class InkTouchHandler(
    private val view: InProgressStrokesView,
    private val predictor: MotionEventPredictor,
) {
    private var strokeId: InProgressStrokeId? = null
    private var pointerId = MotionEvent.INVALID_POINTER_ID

    fun onTouch(
        event: MotionEvent,
        brush: Brush,
        erasing: Boolean,
        onErase: (Float, Float) -> Unit,
    ): Boolean {
        if (event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) return false

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
