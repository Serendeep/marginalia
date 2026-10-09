package com.serendeep.marginalia.ink

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** Synthetic hand-drawn traces for the detector tests. */
object Traces {
    fun walk(
        vertices: List<Pair<Float, Float>>,
        noise: Float = 1.2f,
        step: Float = 3f,
        seed: Int = 7,
        startMs: Long = 0L,
        msPerPoint: Long = 8L,
    ): List<InkPt> {
        val rnd = Random(seed)
        val out = ArrayList<InkPt>()
        var t = startMs
        for (i in 1 until vertices.size) {
            val (ax, ay) = vertices[i - 1]
            val (bx, by) = vertices[i]
            val n = (hypot(bx - ax, by - ay) / step).toInt().coerceAtLeast(1)
            for (k in (if (i == 1) 0 else 1)..n) {
                val f = k.toFloat() / n
                out.add(InkPt(ax + (bx - ax) * f + jitter(rnd, noise), ay + (by - ay) * f + jitter(rnd, noise), t))
                t += msPerPoint
            }
        }
        return out
    }

    private fun jitter(rnd: Random, amount: Float) = (rnd.nextFloat() * 2f - 1f) * amount

    fun hold(points: List<InkPt>, ms: Long = 700L): List<InkPt> {
        val rnd = Random(3)
        val last = points.last()
        val extra = (1..10).map {
            InkPt(last.x + jitter(rnd, 1.5f), last.y + jitter(rnd, 1.5f), last.t + ms * it / 10)
        }
        return points + extra
    }

    fun circle(cx: Float, cy: Float, r: Float, overshoot: Float = 0.05f, noise: Float = 1.5f): List<InkPt> {
        val verts = ArrayList<Pair<Float, Float>>()
        val steps = 90
        val sweep = 2 * PI.toFloat() * (1f + overshoot)
        for (i in 0..steps) {
            val th = -PI.toFloat() / 2 + sweep * i / steps
            val wobble = 1f + 0.03f * sin(3 * th)
            verts.add(cx + r * wobble * cos(th) to cy + r * wobble * sin(th))
        }
        return walk(verts, noise)
    }

    fun ellipse(cx: Float, cy: Float, a: Float, b: Float): List<InkPt> {
        val verts = (0..90).map {
            val th = 2 * PI.toFloat() * it / 90
            cx + a * cos(th) to cy + b * sin(th)
        }
        return walk(verts)
    }

    fun polygon(corners: List<Pair<Float, Float>>, noise: Float = 1.5f): List<InkPt> =
        walk(corners + corners.first(), noise)

    fun rotated(corners: List<Pair<Float, Float>>, degrees: Float, cx: Float, cy: Float): List<Pair<Float, Float>> {
        val a = degrees * PI.toFloat() / 180f
        return corners.map { (x, y) ->
            val dx = x - cx
            val dy = y - cy
            cx + dx * cos(a) - dy * sin(a) to cy + dx * sin(a) + dy * cos(a)
        }
    }

    fun arrow(): List<InkPt> {
        val s = 100f to 300f
        val tip = 400f to 300f
        val w1 = 400f - 60f * cos(0.6f) to 300f - 60f * sin(0.6f)
        val w2 = 400f - 60f * cos(0.6f) to 300f + 60f * sin(0.6f)
        return walk(listOf(s, tip, w1, tip, w2))
    }

    fun zigzag(strokes: Int = 6, width: Float = 120f, height: Float = 30f): List<InkPt> {
        val verts = ArrayList<Pair<Float, Float>>()
        for (i in 0..strokes) verts.add((if (i % 2 == 0) 0f else width) + 100f to 100f + height * i / strokes)
        return walk(verts, noise = 1f)
    }

    /** Cursive run of [humps] arches ("mm" = 4, "www" = 6 cusps), travelling right. */
    fun humps(humps: Int, pitch: Float = 22f, height: Float = 28f, cusp: Float = 3f): List<InkPt> {
        val out = ArrayList<Pair<Float, Float>>()
        val steps = humps * 12
        for (i in 0..steps) {
            val u = i.toFloat() / 12f
            val x = 100f + pitch * u + cusp * sin(2 * PI.toFloat() * u)
            val y = 200f - height * kotlin.math.abs(sin(PI.toFloat() * u))
            out.add(x to y)
        }
        return walk(out, noise = 0.8f, step = 2f)
    }

    fun scribble(seed: Int = 5, n: Int = 40): List<InkPt> {
        val rnd = Random(seed)
        val v = (0 until n).map { 100f + rnd.nextFloat() * 160f to 100f + rnd.nextFloat() * 120f }
        return walk(v, noise = 0f)
    }
}
