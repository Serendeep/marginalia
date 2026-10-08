@file:Suppress("RestrictedApi")

package com.serendeep.marginalia.handwriting

import android.graphics.RectF
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.serendeep.marginalia.data.Box
import com.serendeep.marginalia.data.InkStroke
import com.serendeep.marginalia.data.InkSurface
import com.serendeep.marginalia.data.MarginaliaDatabase
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.data.SNIPPET_OPEN
import com.serendeep.marginalia.ink.InkPt
import com.serendeep.marginalia.ink.toBatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkIndexerTest {

    private class FakeRecognizer(private val words: String) : InkRecognizer() {
        var calls = 0
        val model = MutableStateFlow<ModelState>(ModelState.Ready)
        override val state: StateFlow<ModelState> get() = model
        override suspend fun refresh(): ModelState = model.value
        override suspend fun recognize(strokes: List<InkStroke>, writingArea: RectF?): List<String> {
            calls++
            return listOf(words)
        }
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: MarginaliaDatabase
    private lateinit var repo: MarginaliaRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, MarginaliaDatabase::class.java).build()
        repo = MarginaliaRepository(
            db.courseDao(), db.lectureDao(), db.documentDao(), db.strokeDao(),
            db.anchorDao(), db.studySessionDao(), db.searchDao(), db.highlightDao(), db.cardDao(), db.tagDao(),
        )
    }

    @After
    fun teardown() = db.close()

    private suspend fun lecture(): String = repo.createLecture(repo.createCourse("C", 0, null).id, "Thermo notes").id

    private fun stroke(id: String, lectureId: String, top: Float, surface: InkSurface = InkSurface.MARGIN): InkStroke {
        val points = listOf(InkPt(10f, top, 0), InkPt(60f, top + 30f, 40), InkPt(110f, top + 5f, 80))
        return InkStroke(
            id = id, lectureId = lectureId, documentId = "", pdfPage = 0,
            viewport = Box(0f, 0f, 0f, 0f), bounds = Box(10f, top, 110f, top + 30f),
            startedAt = 1_000, endedAt = 1_100, brushColor = 0xFF000000, brushSizeDp = 3f,
            batch = points.toBatch(0.5f), surface = surface,
        )
    }

    @Test
    fun indexesChangedLecturesOnlyAndReplacesOldRows() = runBlocking {
        val id = lecture()
        repo.saveStrokes(listOf(stroke("s1", id, 0f), stroke("s2", id, 10f), stroke("page", id, 0f, InkSurface.PAGE)))
        val fake = FakeRecognizer("entropy never decreases")
        val indexer = InkIndexer(context, repo, fake)

        indexer.indexStale()
        val hit = repo.search("entrop").ink.single()
        assertEquals(id, hit.lectureId)
        assertEquals("Thermo notes", hit.title)
        assertEquals(null, hit.page)
        assertTrue(hit.snippet.contains("${SNIPPET_OPEN}entropy"))
        val firstCalls = fake.calls
        assertEquals(1, firstCalls)

        indexer.indexStale()
        assertEquals("unchanged strokes are not re-read", firstCalls, fake.calls)

        repo.saveStrokes(listOf(stroke("s3", id, 400f)))
        indexer.indexStale()
        assertTrue(fake.calls > firstCalls)
        assertEquals("old rows were replaced, not duplicated", 2, repo.search("entropy").ink.size)
    }

    @Test
    fun removingAllStrokesClearsTheRowsAndLeavingLectureDropsThem() = runBlocking {
        val id = lecture()
        repo.saveStrokes(listOf(stroke("s1", id, 0f)))
        val indexer = InkIndexer(context, repo, FakeRecognizer("gibbs free energy"))
        indexer.indexStale()
        assertEquals(1, repo.search("gibbs").ink.size)

        repo.deleteStroke("s1")
        indexer.indexStale()
        assertTrue(repo.search("gibbs").ink.isEmpty())

        repo.saveStrokes(listOf(stroke("s9", id, 0f)))
        indexer.indexStale()
        assertEquals(1, repo.search("gibbs").ink.size)
        repo.deleteLecture(id)
        assertTrue(repo.search("gibbs").ink.isEmpty())
    }

    @Test
    fun nothingRunsUntilTheModelIsReady() = runBlocking {
        val id = lecture()
        repo.saveStrokes(listOf(stroke("s1", id, 0f)))
        val fake = FakeRecognizer("hello").apply { model.value = ModelState.NotDownloaded }
        val indexer = InkIndexer(context, repo, fake)

        indexer.indexStale()

        assertEquals(0, fake.calls)
        assertTrue(repo.search("hello").ink.isEmpty())
    }

    @Test
    fun searchSkipsHandwritingWhenAskedTo() = runBlocking {
        val id = lecture()
        repo.saveStrokes(listOf(stroke("s1", id, 0f)))
        InkIndexer(context, repo, FakeRecognizer("hello world")).indexStale()

        assertEquals(1, repo.search("hello").ink.size)
        assertTrue(repo.search("hello", includeInk = false).ink.isEmpty())
    }
}
