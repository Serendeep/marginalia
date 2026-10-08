@file:Suppress("RestrictedApi")

package com.serendeep.marginalia.ink

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.serendeep.marginalia.data.Box
import com.serendeep.marginalia.data.InkStroke
import com.serendeep.marginalia.data.MarginaliaDatabase
import com.serendeep.marginalia.data.MarginaliaRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StrokeEditTest {

    private lateinit var db: MarginaliaDatabase
    private lateinit var repo: MarginaliaRepository
    private lateinit var lectureId: String

    @Before
    fun setup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, MarginaliaDatabase::class.java).build()
        repo = MarginaliaRepository(db.courseDao(), db.lectureDao(), db.documentDao(), db.strokeDao(), db.anchorDao(), db.studySessionDao(), db.searchDao(), db.highlightDao(), db.cardDao(), db.tagDao())
        lectureId = repo.createLecture(repo.createCourse("C", 0, null).id, "L").id
    }

    @After
    fun teardown() = db.close()

    private fun stroke(id: String, vararg pts: Pair<Float, Float>): InkStroke {
        val batch = pts.mapIndexed { i, p -> InkPt(p.first, p.second, i * 10L) }.toBatch(0.6f)
        return InkStroke(id, lectureId, "", null, 0, Box(0f, 0f, 0f, 0f), batch.bounds(), 1, 2, 0xFF112233, 6f, batch)
    }

    @Test
    fun movePersistsAndUndoRestores() = runBlocking {
        val original = stroke("a", 10f to 10f, 50f to 30f)
        repo.saveStroke(original)
        val moved = original.transformed(StrokeTransform.move(100f, -5f))
        repo.saveStrokes(listOf(moved))
        val loaded = repo.loadStrokes(lectureId).single()
        assertEquals(110f, loaded.batch.get(0).x, 0.01f)
        assertEquals(5f, loaded.batch.get(0).y, 0.01f)
        assertEquals(110f, loaded.bounds.left, 0.01f)
        assertEquals(0.6f, loaded.batch.get(0).pressure, 0.001f)
        repo.saveStrokes(listOf(original))
        assertEquals(10f, repo.loadStrokes(lectureId).single().batch.get(0).x, 0.01f)
    }

    @Test
    fun scaleResizesGeometryAndBrush() {
        val s = stroke("a", 10f to 10f, 50f to 30f).transformed(StrokeTransform.scaleAbout(2f, 10f, 10f))
        assertEquals(10f, s.batch.get(0).x, 0.01f)
        assertEquals(90f, s.batch.get(1).x, 0.01f)
        assertEquals(50f, s.batch.get(1).y, 0.01f)
        assertEquals(12f, s.brushSizeDp, 0.01f)
    }

    @Test
    fun recolorKeepsAlpha() {
        val s = stroke("a", 0f to 0f, 5f to 5f).copy(brushColor = 0x99F2C84BL).recolored(0xE5484D)
        assertEquals(0x99E5484DL, s.brushColor)
    }

    @Test
    fun scratchEraseIsOneRemovalAndUndoRestoresAll() = runBlocking {
        val a = stroke("a", 100f to 100f, 140f to 102f, 180f to 100f)
        val b = stroke("b", 100f to 300f, 180f to 300f)
        repo.saveStrokes(listOf(a, b))
        val scratch = Traces2.zigzagPoints()
        val area = ScratchOut.detect(scratch)!!
        val covered = listOf(a, b).filter { ScratchOut.covers(area, it.batch.toPoints()) }
        assertEquals(listOf("a"), covered.map { it.id })
        covered.forEach { repo.deleteStroke(it.id) }
        assertEquals(listOf("b"), repo.loadStrokes(lectureId).map { it.id })
        repo.saveStrokes(covered)
        assertEquals(setOf("a", "b"), repo.loadStrokes(lectureId).map { it.id }.toSet())
    }

    @Test
    fun synthesizedBatchRoundTripsThroughStorage() = runBlocking {
        val circle = (0..60).map {
            val th = it * 0.1f
            InkPt(200f + 50f * kotlin.math.cos(th), 200f + 50f * kotlin.math.sin(th), it * 8L)
        }
        val batch = circle.toBatch(ShapeSnap.PRESSURE)
        repo.saveStroke(stroke("c", 0f to 0f, 1f to 1f).copy(batch = batch, bounds = batch.bounds()))
        val loaded = repo.loadStrokes(lectureId).single().batch
        assertEquals(circle.size, loaded.size)
        assertTrue(loaded.get(loaded.size - 1).elapsedTimeMillis >= loaded.get(0).elapsedTimeMillis)
    }
}

private object Traces2 {
    fun zigzagPoints(): List<InkPt> {
        val out = ArrayList<InkPt>()
        var t = 0L
        for (i in 0..6) {
            val x0 = if (i % 2 == 0) 100f else 180f
            val x1 = if (i % 2 == 0) 180f else 100f
            for (k in 0..10) {
                out.add(InkPt(x0 + (x1 - x0) * k / 10f, 90f + i * 5f + k * 0.8f, t))
                t += 8
            }
        }
        return out
    }
}
