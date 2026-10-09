package com.serendeep.marginalia.ai

import com.serendeep.marginalia.data.HighlightEntity
import com.serendeep.marginalia.data.HighlightRow
import com.serendeep.marginalia.data.LectureEntity
import com.serendeep.marginalia.data.RetentionRow
import com.serendeep.marginalia.data.StudySessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DigestTest {
    private val zone = ZoneOffset.UTC
    private val min = 60_000L
    private val noRetention = RetentionRow(0, 0)

    private fun session(lecture: String?, startMin: Long, lengthMin: Long) =
        StudySessionEntity("s$startMin", lecture, "READING", startMin * min, (startMin + lengthMin) * min)

    private fun lecture(id: String, title: String, lastPage: Int = 0) = LectureEntity(id, "c", title, 0, 0, lastPage = lastPage)

    private fun highlight(text: String) =
        HighlightRow(HighlightEntity("h", "l1", "d", 0, text, 0, "s", 0), "Thermo")

    private fun input(
        sessions: List<StudySessionEntity> = emptyList(),
        opened: List<LectureEntity> = emptyList(),
        highlights: List<HighlightRow> = emptyList(),
        titles: Map<String, String> = mapOf("l1" to "Thermo", "l2" to "Optics"),
        cards: Int = 0,
        reviews: Int = 0,
        retention: RetentionRow = noRetention,
        due: Int = 0,
    ) = buildDigestInput(sessions, opened, highlights, titles, cards, reviews, retention, due, zone)

    @Test
    fun noActivityMeansNoDigest() {
        assertNull(input(due = 12))
    }

    @Test
    fun anySingleKindOfActivityCounts() {
        assertTrue(input(sessions = listOf(session("l1", 0, 5))) != null)
        assertTrue(input(opened = listOf(lecture("l1", "Thermo"))) != null)
        assertTrue(input(highlights = listOf(highlight("x"))) != null)
        assertTrue(input(cards = 1) != null)
        assertTrue(input(reviews = 1) != null)
    }

    @Test
    fun studyTimeIsTotalAndPerDocumentLargestFirst() {
        val result = input(
            sessions = listOf(session("l1", 0, 20), session("l2", 100, 45), session("l1", 200, 10), session(null, 300, 5)),
        )!!
        assertEquals(80, result.studyMinutes)
        assertEquals(listOf("Optics" to 45, "Thermo" to 30), result.byDocument)
    }

    @Test
    fun openedDocumentsReportOneBasedPage() {
        val result = input(opened = listOf(lecture("l1", "Thermo", lastPage = 11)))!!
        assertEquals(listOf(OpenedDocument("Thermo", 12)), result.opened)
    }

    @Test
    fun highlightsAreCappedAndTrimmed() {
        val many = List(12) { highlight("  " + "w".repeat(300)) }
        val result = input(highlights = many)!!
        assertEquals(12, result.highlightCount)
        assertEquals(8, result.highlights.size)
        assertEquals(160, result.highlights.first().length)
    }

    @Test
    fun retentionOnlyWhenThereWereMatureReviews() {
        assertNull(input(reviews = 3)!!.retentionPct)
        assertEquals(75, input(reviews = 4, retention = RetentionRow(4, 3))!!.retentionPct)
    }

    @Test
    fun promptStatesTheFactsAndForbidsQuizzes() {
        val request = Prompts.digest(
            input(
                sessions = listOf(session("l1", 0, 30)),
                highlights = listOf(highlight("Entropy never decreases")),
                cards = 4,
                reviews = 20,
                retention = RetentionRow(10, 9),
                due = 14,
            )!!,
        )
        assertEquals(AiTask.DIGEST, request.task)
        assertTrue(request.text.contains("Study time: 30 min"))
        assertTrue(request.text.contains("Thermo 30 min"))
        assertTrue(request.text.contains("- Entropy never decreases"))
        assertTrue(request.text.contains("Cards created: 4"))
        assertTrue(request.text.contains("Reviews done: 20 (retention 90%)"))
        assertTrue(request.text.contains("Cards due today: 14"))
        assertTrue(request.instructions.contains("Do not ask questions"))
    }

    @Test
    fun dayKeyIsTheIsoDate() {
        assertEquals("2026-10-10", dayKey(LocalDate.of(2026, 10, 10)))
    }

    @Test
    fun cacheServesTodayOnlyAndRemembersDismissal() {
        val empty = DigestCache()
        assertTrue(empty.needed("2026-10-10"))
        assertNull(empty.visible("2026-10-10"))

        val written = empty.with("2026-10-10", "You read.")
        assertEquals("You read.", written.visible("2026-10-10"))
        assertFalse(written.needed("2026-10-10"))
        // The next morning the old text is stale and a new one is due.
        assertNull(written.visible("2026-10-11"))
        assertTrue(written.needed("2026-10-11"))

        val dismissed = written.dismissed("2026-10-10")
        assertNull(dismissed.visible("2026-10-10"))
        assertFalse(dismissed.needed("2026-10-10"))
        assertEquals("You read.", dismissed.cached("2026-10-10"))
        assertTrue(dismissed.needed("2026-10-11"))
    }
}
