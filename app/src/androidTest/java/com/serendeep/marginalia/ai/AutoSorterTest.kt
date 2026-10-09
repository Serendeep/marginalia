package com.serendeep.marginalia.ai

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.serendeep.marginalia.data.MarginaliaDatabase
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.library.LibraryViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoSorterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: MarginaliaDatabase
    private lateinit var repo: MarginaliaRepository
    private var reply = ""

    private val fake = object : AiProvider {
        override val name = "fake"
        override fun stream(request: AiRequest): Flow<AiEvent> = flowOf(AiEvent.Delta(reply), AiEvent.Completed)
        override suspend fun models() = emptyList<ChatModel>()
    }

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, MarginaliaDatabase::class.java).build()
        repo = MarginaliaRepository(
            db.courseDao(), db.lectureDao(), db.documentDao(), db.strokeDao(), db.anchorDao(),
            db.studySessionDao(), db.searchDao(), db.highlightDao(), db.cardDao(), db.tagDao(),
        )
    }

    @After
    fun teardown() = db.close()

    private fun sorter(): AutoSorter {
        val prefs = context.getSharedPreferences("auto-sorter-test", 0).also { it.edit().clear().commit() }
        return AutoSorter(repo, prefs) { fake }
    }

    @Test
    fun sortsIntoNewCourseThenUndoRestoresEverything() = runBlocking {
        val unsorted = repo.createCourse(LibraryViewModel.UNSORTED_NAME, 0, null)
        val lecture = repo.createLecture(unsorted.id, "attention_is_all_you_need")
        repo.importDocument(lecture.id, "paper.pdf", "/nonexistent/paper.pdf", 4)
        reply = """{"course":null,"newCourse":{"name":"Deep Learning","emoji":"🧠"},"tags":["transformers"],"title":"Attention Is All You Need"}"""
        val sorter = sorter()

        val result = sorter.sort(lecture.id)!!

        val moved = repo.getLecture(lecture.id)!!
        assertEquals("Attention Is All You Need", moved.title)
        val course = repo.courses().first { it.name == "Deep Learning" }
        assertEquals(course.id, moved.courseId)
        assertEquals("🧠", course.emoji)
        assertEquals(1, result.addedTagIds.size)

        sorter.undo(result)

        val back = repo.getLecture(lecture.id)!!
        assertEquals(unsorted.id, back.courseId)
        assertEquals("attention_is_all_you_need", back.title)
        assertTrue(repo.tagIdsOf(lecture.id).isEmpty())
        assertTrue(repo.courses().none { it.name == "Deep Learning" })
    }

    @Test
    fun existingCourseIsReusedAndRealTitleKept() = runBlocking {
        val unsorted = repo.createCourse(LibraryViewModel.UNSORTED_NAME, 0, null)
        val algo = repo.createCourse("Algorithms", 1, null)
        val lecture = repo.createLecture(unsorted.id, "Graph Theory Notes")
        repo.importDocument(lecture.id, "paper.pdf", "/nonexistent/paper.pdf", 4)
        reply = "```json\n{\"course\":\"algorithms\",\"newCourse\":null,\"tags\":[],\"title\":\"Something Else\"}\n```"
        val sorter = sorter()

        val result = sorter.sort(lecture.id)!!

        assertEquals(algo.id, repo.getLecture(lecture.id)!!.courseId)
        assertEquals("Graph Theory Notes", repo.getLecture(lecture.id)!!.title)
        sorter.undo(result)
        assertNotNull("pre-existing course survives undo", repo.courses().firstOrNull { it.name == "Algorithms" })
    }

    @Test
    fun attemptedLectureIsSkippedUnlessForced() = runBlocking {
        val unsorted = repo.createCourse(LibraryViewModel.UNSORTED_NAME, 0, null)
        val lecture = repo.createLecture(unsorted.id, "x")
        repo.importDocument(lecture.id, "paper.pdf", "/nonexistent/paper.pdf", 4)
        reply = "no idea"
        val sorter = sorter()
        assertNull(sorter.sort(lecture.id))
        reply = """{"course":null,"newCourse":{"name":"Misc","emoji":"📁"},"tags":[],"title":null}"""
        assertNull(sorter.sort(lecture.id))
        assertNotNull(sorter.sort(lecture.id, force = true))
    }

    @Test
    fun garbageReplyLeavesLectureInUnsorted() = runBlocking {
        val unsorted = repo.createCourse(LibraryViewModel.UNSORTED_NAME, 0, null)
        val lecture = repo.createLecture(unsorted.id, "x")
        repo.importDocument(lecture.id, "paper.pdf", "/nonexistent/paper.pdf", 4)
        reply = "no idea"
        assertNull(sorter().sort(lecture.id))
        assertEquals(unsorted.id, repo.getLecture(lecture.id)!!.courseId)
    }
}
