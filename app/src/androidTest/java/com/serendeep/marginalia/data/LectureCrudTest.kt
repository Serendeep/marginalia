package com.serendeep.marginalia.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Brief's helper used a nested runBlocking whose implicit receiver shadowed the
// Flow, so `first()` didn't resolve; this is a plain suspend extension instead.
private suspend fun <T> Flow<List<T>>.firstValue(): List<T> = first()

class LectureCrudTest {
    private fun db() = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        MarginaliaDatabase::class.java,
    ).build()

    @Test
    fun rename_keepsStrokes() = runBlocking {
        val db = db()
        db.courseDao().insert(CourseEntity("c1", "Sys", 0, 0))
        db.lectureDao().insert(LectureEntity("l1", "c1", "Old", 0, 0))
        db.strokeDao().insert(
            StrokeEntity(
                id = "s1", lectureId = "l1", documentId = "d1", pdfPage = 0,
                viewportLeft = 0f, viewportTop = 0f, viewportRight = 0f, viewportBottom = 0f,
                boundsLeft = 0f, boundsTop = 0f, boundsRight = 0f, boundsBottom = 0f,
                startedAt = 0, endedAt = 0, brushColor = 0, brushSizeDp = 1f,
                inkBlob = byteArrayOf(1),
            )
        )
        db.lectureDao().rename("l1", "New")
        assertEquals("New", db.lectureDao().observeAll().firstValue().single().title)
        assertEquals(1, db.strokeDao().getByLecture("l1").size) // REPLACE would have cascaded
        db.close()
    }

    @Test
    fun newLecture_startsWithoutDocument() = runBlocking {
        val db = db()
        db.courseDao().insert(CourseEntity("c1", "Sys", 0, 0))
        val lecture = MarginaliaRepository(
            db.courseDao(), db.lectureDao(), db.documentDao(), db.strokeDao(), db.anchorDao()
        ).createLecture("c1", "Scratch Notes")

        assertTrue(db.documentDao().getByLecture(lecture.id).isEmpty())
        assertEquals("Scratch Notes", db.lectureDao().observeAll().firstValue().single().title)
        db.close()
    }

    @Test
    fun deleteLecture_cascades() = runBlocking {
        val db = db()
        db.courseDao().insert(CourseEntity("c1", "Sys", 0, 0))
        db.lectureDao().insert(LectureEntity("l1", "c1", "T", 0, 0))
        db.documentDao().insert(
            DocumentEntity("d1", "l1", "f.pdf", "/tmp/none.pdf", 3, 0, 0)
        )
        db.lectureDao().deleteById("l1")
        assertTrue(db.documentDao().getByLecture("l1").isEmpty())
        db.close()
    }
}
