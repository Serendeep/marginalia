package com.serendeep.marginalia.notebook

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.dp
import com.serendeep.marginalia.ink.LASER_ARGB
import com.serendeep.marginalia.ink.LASER_DOWN
import com.serendeep.marginalia.ink.LASER_UP

/**
 * The laser pointer's trails, in the overlay's own coordinate space. Trails live only
 * here: nothing is stored and there is no undo entry.
 */
@Stable
class LaserTrails {
    private class Trail {
        val points = ArrayList<Offset>()
        var liftedAt = 0L
    }

    private val trails = ArrayList<Trail>()
    private var current: Trail? = null
    private var origin = Offset.Zero

    /** True while any trail is still visible; the overlay only runs frames while this holds. */
    var alive by mutableStateOf(false)
        private set

    /** Bumped on every change so the canvas redraws. */
    var tick by mutableLongStateOf(0L)
        private set

    fun setOrigin(screenPosition: Offset) {
        origin = screenPosition
    }

    /** Feeds one stylus sample, in screen coordinates. */
    fun touch(rawX: Float, rawY: Float, phase: Int) {
        val p = Offset(rawX - origin.x, rawY - origin.y)
        when (phase) {
            LASER_DOWN -> {
                current = Trail().also { it.points.add(p); trails.add(it) }
                alive = true
            }
            LASER_UP -> {
                current?.liftedAt = SystemClock.uptimeMillis()
                current = null
            }
            else -> current?.points?.add(p)
        }
        tick++
    }

    /** Drops fully faded trails; returns whether any remain. */
    fun advance(now: Long): Boolean {
        trails.removeAll { it.liftedAt != 0L && now - it.liftedAt >= FADE_MS }
        alive = trails.isNotEmpty()
        tick++
        return alive
    }

    fun draw(scope: DrawScope, now: Long) = with(scope) {
        val width = 5.dp.toPx()
        for (trail in trails) {
            if (trail.points.size < 2) continue
            val alpha = if (trail.liftedAt == 0L) 1f else (1f - (now - trail.liftedAt).toFloat() / FADE_MS).coerceIn(0f, 1f)
            val path = Path().apply {
                moveTo(trail.points[0].x, trail.points[0].y)
                for (i in 1 until trail.points.size) lineTo(trail.points[i].x, trail.points[i].y)
            }
            val red = Color(LASER_ARGB)
            drawPath(path, red.copy(alpha = 0.28f * alpha), style = Stroke(width * 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, red.copy(alpha = alpha), style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (trail.liftedAt == 0L) drawCircle(Color.White.copy(alpha = 0.9f), width * 0.6f, trail.points.last())
        }
    }

    companion object {
        const val FADE_MS = 2000L
    }
}

/** One canvas above both panes. Frames run only while a trail is visible. */
@Composable
fun LaserOverlay(trails: LaserTrails, modifier: Modifier = Modifier) {
    LaunchedEffect(trails.alive) {
        while (trails.alive) {
            androidx.compose.runtime.withFrameNanos { }
            trails.advance(SystemClock.uptimeMillis())
        }
    }
    Canvas(modifier.fillMaxSize().onGloballyPositioned { trails.setOrigin(it.positionOnScreen()) }) {
        trails.tick
        trails.draw(this, SystemClock.uptimeMillis())
    }
}
