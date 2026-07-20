package com.serendeep.marginalia.library

import android.graphics.pdf.PdfDocument
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.serendeep.marginalia.data.MarginaliaDatabase
import com.serendeep.marginalia.data.MarginaliaRepository
import kotlinx.coroutines.flow.first
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
class LibraryImportTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: MarginaliaDatabase
    private lateinit var repo: MarginaliaRepository
    private lateinit var importer: PdfImporter
    private lateinit var pdfsDir: File

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, MarginaliaDatabase::class.java).build()
        repo = MarginaliaRepository(db.courseDao(), db.lectureDao(), db.documentDao(), db.strokeDao(), db.anchorDao())
        importer = PdfImporter(context, repo)
        pdfsDir = File(context.filesDir, "pdfs")
        pdfsDir.deleteRecursively()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun validPdfIsCopiedAndRegistered() = runBlocking {
        val course = repo.createCourse("Course")
        val lecture = repo.createLecture(course.id, "Lecture")
        val src = writeValidPdf()

        val result = importer.import(lecture.id, src.toUri())

        val success = result as? PdfImporter.Result.Success ?: error("expected success, got $result")
        assertEquals(1, success.document.pageCount)
        assertTrue("copy landed under filesDir/pdfs", success.document.localPath.startsWith(pdfsDir.absolutePath))
        assertTrue("copy exists on disk", File(success.document.localPath).exists())

        val stored = repo.observeDocuments(lecture.id).first()
        assertEquals(1, stored.size)
        assertEquals(success.document.id, stored[0].id)
    }

    @Test
    fun garbageFileIsRejectedAndLeavesNoTrace() = runBlocking {
        val course = repo.createCourse("Course")
        val lecture = repo.createLecture(course.id, "Lecture")
        val garbage = File(context.cacheDir, "garbage.pdf").apply {
            writeBytes(ByteArray(64) { it.toByte() })
        }

        val result = importer.import(lecture.id, garbage.toUri())

        assertTrue("rejected as failure", result is PdfImporter.Result.Failure)
        assertTrue("no copied file left behind", pdfsDir.listFiles().isNullOrEmpty())
        assertTrue("no document row created", repo.observeDocuments(lecture.id).first().isEmpty())
    }

    private fun writeValidPdf(): File {
        val doc = PdfDocument()
        val info = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        doc.finishPage(doc.startPage(info))
        val out = File(context.cacheDir, "library-import-source.pdf")
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }
}
