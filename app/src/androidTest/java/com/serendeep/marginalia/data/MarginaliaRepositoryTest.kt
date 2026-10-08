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
        repo = MarginaliaRepository(db.courseDao(), db.lectureDao(), db.documentDao(), db.strokeDao(), db.anchorDao(), db.studySessionDao(), db.searchDao(), db.highlightDao())
    }

    @After
    fun teardown() = db.close()

    @Test
    fun strokeSurvivesRoundTrip() = runBlocking {
        val course = repo.createCourse("Thermodynamics", colorIndex = 0, emoji = null)
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
        val course = repo.createCourse("Algorithms", colorIndex = 0, emoji = null)
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
    fun removeAnchorUnbindsStrokes_andRestoreRebinds() = runBlocking {
        val course = repo.createCourse("Systems", colorIndex = 0, emoji = null)
        val lecture = repo.createLecture(course.id, "Week 4")
        val doc = repo.importDocument(lecture.id, "s.pdf", "/data/s.pdf", pageCount = 5)
        val anchor = repo.createAnchor(lecture.id, doc.id, pdfPage = 2, pageXFraction = 0.4f, pageYFraction = 0.6f)
        repo.saveStroke(
            InkStroke(
                id = "bound-1",
                lectureId = lecture.id,
                documentId = doc.id,
                anchorId = anchor.id,
                pdfPage = 2,
                viewport = Box(0f, 0f, 595f, 842f),
                bounds = Box(0f, 0f, 5f, 5f),
                startedAt = 0,
                endedAt = 5,
                brushColor = 0xFF000000,
                brushSizeDp = 2f,
                batch = strokeOf(listOf(0f to 0f, 5f to 5f)),
            ),
        )

        val bound = repo.removeAnchorAndUnbind(anchor.id)
        assertEquals(listOf("bound-1"), bound)
        assertEquals(null, repo.loadStrokes(lecture.id).single().anchorId)
        assertTrue(repo.observeAnchors(lecture.id).first().isEmpty())

        repo.restoreAnchor(anchor, bound)
        assertEquals(anchor.id, repo.loadStrokes(lecture.id).single().anchorId)
        assertEquals(1, repo.observeAnchors(lecture.id).first().size)
    }

    @Test
    fun reimportGetsNextVersionIndex() = runBlocking {
        val course = repo.createCourse("Networks", colorIndex = 0, emoji = null)
        val lecture = repo.createLecture(course.id, "Week 2")
        val v0 = repo.importDocument(lecture.id, "slides.pdf", "/data/v0.pdf", pageCount = 8)
        val v1 = repo.importDocument(lecture.id, "slides.pdf", "/data/v1.pdf", pageCount = 9)
        assertEquals(0, v0.versionIndex)
        assertEquals(1, v1.versionIndex)
    }

    @Test
    fun deletingStrokeRemovesItsHighlight() = runBlocking {
        val course = repo.createCourse("Thermodynamics", colorIndex = 0, emoji = null)
        val lecture = repo.createLecture(course.id, "Week 3")
        val doc = repo.importDocument(lecture.id, "slides.pdf", "/data/slides.pdf", pageCount = 2)
        fun stroke(id: String) = InkStroke(
            id = id, lectureId = lecture.id, documentId = doc.id, pdfPage = 1,
            viewport = Box(0f, 0f, 600f, 800f), bounds = Box(10f, 10f, 30f, 14f),
            startedAt = 1, endedAt = 2, brushColor = 0x99F2C84B, brushSizeDp = 22f,
            batch = strokeOf(listOf(10f to 10f, 30f to 14f)), surface = InkSurface.PAGE,
        )
        repo.saveStrokes(listOf(stroke("s1"), stroke("s2")))
        repo.saveHighlights(
            listOf(
                HighlightEntity("h1", lecture.id, doc.id, 1, "entropy never decreases", 0x99F2C84B, "s1", 5),
                HighlightEntity("h2", lecture.id, doc.id, 1, "free energy", 0x99F2C84B, "s2", 6),
            ),
        )
        assertEquals(2, repo.observeHighlightCount().first())

        repo.deleteStroke("s1")

        val left = repo.observeHighlights().first()
        assertEquals(listOf("h2"), left.map { it.highlight.id })
        assertEquals("Week 3", left.single().lectureTitle)
        assertEquals(1, repo.loadStrokes(lecture.id).size)

        repo.saveHighlights(
            listOf(HighlightEntity("h1", lecture.id, doc.id, 1, "entropy never decreases", 0x99F2C84B, "s1", 5)),
        )
        assertEquals(2, repo.observeHighlightCount().first())
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
