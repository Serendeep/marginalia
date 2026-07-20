package com.serendeep.marginalia.data

import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.StrokeInput
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarginaliaRepositoryTest {

    private lateinit var db: MarginaliaDatabase
    private lateinit var repo: MarginaliaRepository

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, MarginaliaDatabase::class.java).build()
        repo = MarginaliaRepository(db.courseDao(), db.lectureDao(), db.documentDao(), db.strokeDao())
    }

    @After
    fun teardown() = db.close()

    @Test
    fun strokeSurvivesRoundTrip() = runBlocking {
        val course = repo.createCourse("Thermodynamics")
        val lecture = repo.createLecture(course.id, "Week 3: Entropy")
        val doc = repo.importDocument(lecture.id, "slides.pdf", "/data/slides.pdf", pageCount = 12)

        val batch = strokeOf(listOf(10f to 10f, 20f to 14f, 30f to 12f))
        val stroke = InkStroke(
            id = "stroke-1",
            lectureId = lecture.id,
            documentId = doc.id,
            pdfPage = 4,
            viewport = Box(0f, 100f, 595f, 800f),
            bounds = Box(10f, 10f, 30f, 14f),
            startedAt = 1_000,
            endedAt = 1_200,
            brushColor = 0xFF1A73E8,
            brushSizeDp = 3f,
            batch = batch,
        )
        repo.saveStroke(stroke)

        val loaded = repo.loadStrokes(lecture.id)
        assertEquals(1, loaded.size)
        val r = loaded[0]
        assertEquals(4, r.pdfPage)
        assertEquals(doc.id, r.documentId)
        assertEquals(0xFF1A73E8, r.brushColor)
        assertEquals(3f, r.brushSizeDp, 0.001f)
        assertEquals(Box(0f, 100f, 595f, 800f), r.viewport)
        assertEquals("geometry survives serialization", batch.size, r.batch.size)
    }

    @Test
    fun deletingCourseCascadesToStrokes() = runBlocking {
        val course = repo.createCourse("Algorithms")
        val lecture = repo.createLecture(course.id, "Week 1")
        val doc = repo.importDocument(lecture.id, "l1.pdf", "/data/l1.pdf", pageCount = 3)
        repo.saveStroke(
            InkStroke(
                id = "s1",
                lectureId = lecture.id,
                documentId = doc.id,
                pdfPage = 0,
                viewport = Box(0f, 0f, 595f, 842f),
                bounds = Box(0f, 0f, 5f, 5f),
                startedAt = 0,
                endedAt = 5,
                brushColor = 0xFF000000,
                brushSizeDp = 2f,
                batch = strokeOf(listOf(0f to 0f, 5f to 5f)),
            ),
        )
        assertEquals(1, repo.loadStrokes(lecture.id).size)

        db.courseDao().delete(course)

        assertTrue("strokes gone with the course", repo.loadStrokes(lecture.id).isEmpty())
        assertTrue("lectures gone with the course", repo.observeLectures(course.id).first().isEmpty())
    }

    @Test
    fun reimportGetsNextVersionIndex() = runBlocking {
        val course = repo.createCourse("Networks")
        val lecture = repo.createLecture(course.id, "Week 2")
        val v0 = repo.importDocument(lecture.id, "slides.pdf", "/data/v0.pdf", pageCount = 8)
        val v1 = repo.importDocument(lecture.id, "slides.pdf", "/data/v1.pdf", pageCount = 9)
        assertEquals(0, v0.versionIndex)
        assertEquals(1, v1.versionIndex)
    }

    private fun strokeOf(points: List<Pair<Float, Float>>): MutableStrokeInputBatch {
        val batch = MutableStrokeInputBatch()
        points.forEachIndexed { i, (x, y) ->
            batch.add(
                StrokeInput.create(
                    x,
                    y,
                    i * 16L,
                    InputToolType.STYLUS,
                    StrokeInput.NO_STROKE_UNIT_LENGTH,
                ),
            )
        }
        return batch
    }
}
