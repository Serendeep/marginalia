package com.serendeep.marginalia.study

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.serendeep.marginalia.data.CardSource
import com.serendeep.marginalia.data.CardState
import com.serendeep.marginalia.data.MarginaliaDatabase
import com.serendeep.marginalia.data.MarginaliaRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class CardPersistenceTest {
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
            db.studySessionDao(), db.searchDao(), db.highlightDao(), db.cardDao(),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun createCard_isNewAndDueImmediately() = runBlocking {
        val card = repo.createCard(CardSource.TYPED, frontText = "Q", backText = "A")
        val stored = repo.allCards().single()
        assertEquals(card.id, stored.id)
        assertEquals(CardState.NEW, stored.cardState)
        assertEquals(2.5, stored.ease, 0.0)
        assertNull(stored.lectureId)
        assertEquals(1, dueQueue(repo.allCards(), System.currentTimeMillis() + 1, 0).size)
    }

    @Test
    fun grade_updatesCardAndWritesLogTogether() = runBlocking {
        val card = repo.createCard(CardSource.TYPED, frontText = "Q", backText = "A")
        val at = System.currentTimeMillis()
        val updated = Scheduler.next(card, Grade.GOOD, at)
        repo.saveGrade(updated, card, Grade.GOOD.ordinal, at)

        val stored = repo.allCards().single()
        assertEquals(CardState.LEARNING, stored.cardState)
        assertEquals(1, stored.reps)
        assertEquals(at + 10 * 60_000L, stored.dueAt)
        assertEquals(1, db.cardDao().observeReviewTimes().first().size)
        // A learning-step answer is not a mature review.
        assertEquals(0, db.cardDao().observeRetention(0).first().total)
        assertEquals(1, repo.newIntroducedSince(at - 1000))
        assertEquals(0, repo.newIntroducedSince(at + 1000))
    }

    @Test
    fun retention_countsOnlyReviewStateAnswers() = runBlocking {
        val base = repo.createCard(CardSource.TYPED, frontText = "Q", backText = "A")
        val mature = base.copy(state = CardState.REVIEW.name, intervalDays = 10.0)
        db.cardDao().update(mature)
        val at = System.currentTimeMillis()
        repo.saveGrade(Scheduler.next(mature, Grade.GOOD, at), mature, Grade.GOOD.ordinal, at)
        repo.saveGrade(Scheduler.next(mature, Grade.AGAIN, at + 1), mature, Grade.AGAIN.ordinal, at + 1)
        val r = db.cardDao().observeRetention(0).first()
        assertEquals(2, r.total)
        assertEquals(1, r.good)
    }

    @Test
    fun deleteLecture_removesCardsLogsAndImageFile() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val course = repo.createCourse("Sys", 0, null)
        val lecture = repo.createLecture(course.id, "L")
        val image = File(context.cacheDir, "card-${UUID.randomUUID()}.png").apply { writeBytes(byteArrayOf(1)) }
        val card = repo.createCard(CardSource.LASSO, lectureId = lecture.id, frontImagePath = image.absolutePath, backText = "A")
        val at = System.currentTimeMillis()
        repo.saveGrade(Scheduler.next(card, Grade.EASY, at), card, Grade.EASY.ordinal, at)
        val typed = repo.createCard(CardSource.TYPED, frontText = "free", backText = "card")

        repo.deleteLecture(lecture.id)

        assertTrue(db.cardDao().getById(card.id) == null)
        assertTrue(db.cardDao().observeReviewTimes().first().isEmpty())
        assertFalse(image.exists())
        assertNotNull(db.cardDao().getById(typed.id))
        image.delete()
        Unit
    }
}
