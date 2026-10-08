package com.serendeep.marginalia.search

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.serendeep.marginalia.data.MarginaliaDatabase
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.data.SNIPPET_OPEN
import com.serendeep.marginalia.library.PdfImporter
import com.serendeep.marginalia.pdf.PdfDocumentSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class TextSearchTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: MarginaliaDatabase
    private lateinit var repo: MarginaliaRepository
    private lateinit var pdfsDir: File
    private lateinit var preExisting: Set<String>

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, MarginaliaDatabase::class.java).build()
        repo = MarginaliaRepository(
            db.courseDao(), db.lectureDao(), db.documentDao(), db.strokeDao(),
            db.anchorDao(), db.studySessionDao(), db.searchDao(), db.highlightDao(),
        )
        pdfsDir = File(context.filesDir, "pdfs")
        preExisting = pdfsDir.list()?.toSet() ?: emptySet()
    }

    @After
    fun teardown() {
        db.close()
        pdfsDir.list()?.filterNot { it in preExisting }?.forEach { File(pdfsDir, it).delete() }
    }

    @Test
    fun importedPdfIsIndexedOnceAndSearchableByPage() = runBlocking {
        val lecture = importFixture("Thermodynamics")
        val indexer = TextIndexer(context, repo)

        indexer.indexPending()
        indexer.indexPending()

        val found = repo.search("entrop")
        assertEquals(1, found.pages.size)
        val hit = found.pages.single()
        assertEquals(lecture, hit.lectureId)
        assertEquals(0, hit.page)
        assertTrue("snippet marks the match: ${hit.snippet}", hit.snippet.contains("${SNIPPET_OPEN}Entropy"))
        assertEquals(1, repo.search("gibbs energy").pages.single().page)
        assertTrue(repo.search("zzzz").isEmpty)
        assertEquals(listOf(lecture), repo.search("thermo").documents.map { it.lectureId })
    }

    @Test
    fun deletingLectureDropsItsIndexedText() = runBlocking {
        val lecture = importFixture("Thermodynamics")
        TextIndexer(context, repo).indexPending()
        assertEquals(1, repo.search("entropy").pages.size)

        repo.deleteLecture(lecture)

        assertTrue(repo.search("entropy").isEmpty)
    }

    @Test
    fun textInReadsOnlyTheRequestedBand() = runBlocking {
        val source = PdfDocumentSource.open(context, writeFixture())
        try {
            // First line sits at baseline y=80 of 842, 24pt tall: fractions ~0.067..0.095.
            val line = source.textIn(0, RectF(0.05f, 0.06f, 0.95f, 0.10f))
            assertTrue("line text: '$line'", line.contains("Entropy never decreases"))
            // A thin band through the middle of the glyphs, like a highlighter centre line.
            val thin = source.textIn(0, RectF(0.05f, 0.078f, 0.95f, 0.082f))
            assertTrue("thin band text: '$thin'", thin.contains("Entropy"))
            assertEquals("", source.textIn(0, RectF(0.05f, 0.5f, 0.95f, 0.6f)).trim())
            assertTrue(source.pageText(1).contains("Gibbs free energy"))
        } finally {
            source.close()
        }
    }

    private suspend fun importFixture(title: String): String {
        val course = repo.createCourse("Course", colorIndex = 0, emoji = null)
        val lecture = repo.createLecture(course.id, title)
        val result = PdfImporter(context, repo).import(lecture.id, writeFixture().toUri())
        assertTrue("import: $result", result is PdfImporter.Result.Success)
        return lecture.id
    }

    private fun writeFixture(): File {
        val doc = PdfDocument()
        val paint = Paint().apply { color = Color.BLACK; textSize = 24f }
        listOf("Entropy never decreases", "Gibbs free energy").forEachIndexed { i, line ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, i + 1).create())
            page.canvas.drawText(line, 40f, 80f, paint)
            doc.finishPage(page)
        }
        val out = File(context.cacheDir, "search-fixture.pdf")
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }
}
