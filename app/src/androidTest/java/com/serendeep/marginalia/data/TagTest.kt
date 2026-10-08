package com.serendeep.marginalia.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TagTest {
    private lateinit var db: MarginaliaDatabase
    private lateinit var repo: MarginaliaRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            MarginaliaDatabase::class.java,
        ).build()
        repo = MarginaliaRepository(
            db.courseDao(), db.lectureDao(), db.documentDao(), db.strokeDao(), db.anchorDao(),
            db.studySessionDao(), db.searchDao(), db.highlightDao(), db.cardDao(), db.tagDao(),
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun lecture(title: String): LectureEntity {
        val course = repo.createCourse("C", 0, null)
        return repo.createLecture(course.id, title)
    }

    @Test
    fun addTag_reusesExistingNameIgnoringCase() = runBlocking {
        val a = lecture("A")
        val b = lecture("B")
        repo.addTag(a.id, "Physics")
        repo.addTag(b.id, "  physics ")
        val tags = repo.observeTags().first()
        assertEquals(1, tags.size)
        assertEquals(2, repo.observeTagLinks().first().size)
    }

    @Test
    fun setTagged_untagsWithoutDeletingTag() = runBlocking {
        val a = lecture("A")
        repo.addTag(a.id, "ML")
        val tag = repo.observeTags().first().single()
        repo.setTagged(a.id, tag.id, false)
        assertEquals(0, repo.observeTagLinks().first().size)
        assertEquals(1, repo.observeTags().first().size)
        repo.setTagged(a.id, tag.id, true)
        assertEquals(1, repo.observeTagLinks().first().size)
    }

    @Test
    fun deletingLectureOrTag_cascadesLinks() = runBlocking {
        val a = lecture("A")
        val b = lecture("B")
        repo.addTag(a.id, "x")
        repo.addTag(b.id, "y")
        repo.deleteLecture(a.id)
        assertEquals(listOf(b.id), repo.observeTagLinks().first().map { it.lectureId })
        repo.deleteTag(repo.observeTags().first().first { it.name == "y" }.id)
        assertEquals(0, repo.observeTagLinks().first().size)
    }

    @Test
    fun search_matchesTagNamesAsDocuments() = runBlocking {
        val a = lecture("Thermodynamics")
        lecture("Other")
        repo.addTag(a.id, "exam-prep")
        assertEquals(listOf(a.id), repo.search("exam").documents.map { it.lectureId })
    }

    @Test
    fun fillIdentifiers_neverOverwritesAndBibtexPersists() = runBlocking {
        val a = lecture("A")
        repo.fillIdentifiers(a.id, null, "1909.13231")
        repo.fillIdentifiers(a.id, "10.1/x", "2303.15361")
        repo.saveBibtex(a.id, "@misc{k}")
        val stored = repo.getLecture(a.id)!!
        assertEquals("1909.13231", stored.arxivId)
        assertEquals("10.1/x", stored.doi)
        assertEquals("@misc{k}", stored.bibtex)
        assertNull(repo.getLecture("missing"))
    }
}
