package com.serendeep.marginalia.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MarginaliaDatabase::class.java,
    )

    @Test
    fun migrate1To2_preservesStrokesAndAddsAnchors() {
        val db = helper.createDatabase(DB, 1)
        db.execSQL(
            "INSERT INTO courses (id, name, createdAt, orderIndex) VALUES ('c1', 'Course', 1, 1)",
        )
        db.execSQL(
            "INSERT INTO lectures (id, courseId, title, createdAt, orderIndex) VALUES ('l1', 'c1', 'L', 1, 1)",
        )
        db.execSQL(
            """
            INSERT INTO strokes (id, lectureId, documentId, pdfPage,
                viewportLeft, viewportTop, viewportRight, viewportBottom,
                boundsLeft, boundsTop, boundsRight, boundsBottom,
                startedAt, endedAt, brushColor, brushSizeDp, inkBlob)
            VALUES ('s1', 'l1', 'd1', 3, 0,0,0,0, 0,0,0,0, 10, 20, 255, 4.0, x'00')
            """.trimIndent(),
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(DB, 2, true, MarginaliaDatabase.MIGRATION_1_2)

        migrated.query("SELECT id, anchorId, pdfPage FROM strokes").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("s1", c.getString(0))
            assertTrue("anchorId defaults to null", c.isNull(1))
            assertEquals(3, c.getInt(2))
        }
        migrated.execSQL(
            """
            INSERT INTO anchors (id, lectureId, documentId, pdfPage,
                pageXFraction, pageYFraction, label, createdAt)
            VALUES ('a1', 'l1', 'd1', 3, 0.5, 0.25, 1, 30)
            """.trimIndent(),
        )
        migrated.query("SELECT COUNT(*) FROM anchors").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
    }

    @Test
    fun migrate2To3_addsCourseCustomization() {
        val db = helper.createDatabase(DB, 2)
        db.execSQL(
            "INSERT INTO courses (id, name, createdAt, orderIndex) VALUES ('c1', 'Systems', 0, 0)",
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(DB, 3, true, MarginaliaDatabase.MIGRATION_2_3)

        migrated.query("SELECT colorIndex, emoji FROM courses WHERE id = 'c1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
            assertTrue("emoji defaults to null", c.isNull(1))
        }
    }

    @Test
    fun migrate3To4_addsPageSurfaceToExistingStrokes() {
        val db = helper.createDatabase(DB, 3)
        db.execSQL(
            "INSERT INTO courses (id, name, createdAt, orderIndex, colorIndex, emoji) VALUES ('c1', 'Course', 1, 1, 0, NULL)",
        )
        db.execSQL(
            "INSERT INTO lectures (id, courseId, title, createdAt, orderIndex) VALUES ('l1', 'c1', 'L', 1, 1)",
        )
        db.execSQL(
            """
            INSERT INTO strokes (id, lectureId, documentId, anchorId, pdfPage,
                viewportLeft, viewportTop, viewportRight, viewportBottom,
                boundsLeft, boundsTop, boundsRight, boundsBottom,
                startedAt, endedAt, brushColor, brushSizeDp, inkBlob)
            VALUES ('s1', 'l1', 'd1', NULL, 0, 0,0,0,0, 0,0,0,0, 10,20,255,4.0,x'00')
            """.trimIndent(),
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(DB, 4, true, MarginaliaDatabase.MIGRATION_3_4)

        migrated.query("SELECT surface FROM strokes WHERE id = 's1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("MARGIN", c.getString(0))
        }
    }

    @Test
    fun migrate4To5_addsReadingColumnsAndBackfillsInkedLectures() {
        val db = helper.createDatabase(DB, 4)
        db.execSQL(
            "INSERT INTO courses (id, name, createdAt, orderIndex, colorIndex, emoji) VALUES ('c1', 'Course', 1, 1, 0, NULL)",
        )
        db.execSQL("INSERT INTO lectures (id, courseId, title, createdAt, orderIndex) VALUES ('inked', 'c1', 'A', 1, 1)")
        db.execSQL("INSERT INTO lectures (id, courseId, title, createdAt, orderIndex) VALUES ('blank', 'c1', 'B', 1, 1)")
        db.execSQL(
            """
            INSERT INTO strokes (id, lectureId, documentId, anchorId, pdfPage,
                viewportLeft, viewportTop, viewportRight, viewportBottom,
                boundsLeft, boundsTop, boundsRight, boundsBottom,
                startedAt, endedAt, brushColor, brushSizeDp, inkBlob, surface)
            VALUES ('s1', 'inked', 'd1', NULL, 0, 0,0,0,0, 0,0,0,0, 10,20,255,4.0,x'00', 'MARGIN')
            """.trimIndent(),
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(DB, 5, true, MarginaliaDatabase.MIGRATION_4_5)

        migrated.query("SELECT lastPage, lastOpenedAt, readingStatus FROM lectures WHERE id = 'blank'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
            assertTrue(c.isNull(1))
            assertEquals("TO_READ", c.getString(2))
        }
        migrated.query("SELECT readingStatus FROM lectures WHERE id = 'inked'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("READING", c.getString(0))
        }
        migrated.execSQL(
            "INSERT INTO study_sessions (id, lectureId, kind, startedAt, endedAt) VALUES ('x', NULL, 'FOCUS', 1, 2)",
        )
    }

    @Test
    fun migrate5To6_addsTextIndexAndHighlights() {
        val db = helper.createDatabase(DB, 5)
        db.execSQL(
            "INSERT INTO courses (id, name, createdAt, orderIndex, colorIndex, emoji) VALUES ('c1', 'Course', 1, 1, 0, NULL)",
        )
        db.execSQL(
            "INSERT INTO lectures (id, courseId, title, createdAt, orderIndex, lastPage, lastOpenedAt, readingStatus) " +
                "VALUES ('l1', 'c1', 'L', 1, 1, 0, NULL, 'TO_READ')",
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(DB, 6, true, MarginaliaDatabase.MIGRATION_5_6)

        migrated.execSQL("INSERT INTO page_text (documentId, page, text) VALUES ('d1', 2, 'Entropy never decreases')")
        migrated.query("SELECT page FROM page_text WHERE page_text MATCH 'entrop*'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }
        migrated.execSQL("INSERT INTO indexed_documents (documentId, indexedAt) VALUES ('d1', 5)")
        migrated.execSQL(
            "INSERT INTO highlights (id, lectureId, documentId, page, text, color, strokeId, createdAt) " +
                "VALUES ('h1', 'l1', 'd1', 2, 'quote', 4294951115, 's1', 9)",
        )
        migrated.query("SELECT COUNT(*) FROM highlights WHERE strokeId = 's1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
    }

    @Test
    fun migrate6To7_addsCardsAndReviewLog() {
        val db = helper.createDatabase(DB, 6)
        db.execSQL(
            "INSERT INTO courses (id, name, createdAt, orderIndex, colorIndex, emoji) VALUES ('c1', 'Course', 1, 1, 0, NULL)",
        )
        db.execSQL(
            "INSERT INTO lectures (id, courseId, title, createdAt, orderIndex, lastPage, lastOpenedAt, readingStatus) " +
                "VALUES ('l1', 'c1', 'L', 1, 1, 0, NULL, 'TO_READ')",
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(DB, 7, true, MarginaliaDatabase.MIGRATION_6_7)

        migrated.execSQL("PRAGMA foreign_keys = ON")
        migrated.execSQL("INSERT INTO cards (id, lectureId, source, dueAt, createdAt) VALUES ('k1', 'l1', 'TYPED', 5, 5)")
        migrated.query("SELECT state, intervalDays, ease, reps, lapses, step FROM cards WHERE id = 'k1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("NEW", c.getString(0))
            assertEquals(0.0, c.getDouble(1), 0.0)
            assertEquals(2.5, c.getDouble(2), 0.0)
            assertEquals(0, c.getInt(3))
        }
        migrated.execSQL(
            "INSERT INTO review_log (id, cardId, grade, reviewedAt, prevIntervalDays, newIntervalDays) " +
                "VALUES ('r1', 'k1', 2, 9, NULL, 0.1)",
        )
        migrated.execSQL("DELETE FROM lectures WHERE id = 'l1'")
        migrated.query("SELECT COUNT(*) FROM cards").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM review_log").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
    }

    @Test
    fun migrate7To8_addsTagsAndIdentifierColumns() {
        val db = helper.createDatabase(DB, 7)
        db.execSQL(
            "INSERT INTO courses (id, name, createdAt, orderIndex, colorIndex, emoji) VALUES ('c1', 'Course', 1, 1, 0, NULL)",
        )
        db.execSQL(
            "INSERT INTO lectures (id, courseId, title, createdAt, orderIndex, lastPage, lastOpenedAt, readingStatus) " +
                "VALUES ('l1', 'c1', 'L', 1, 1, 0, NULL, 'TO_READ')",
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(DB, 8, true, MarginaliaDatabase.MIGRATION_7_8)

        migrated.query("SELECT doi, arxivId, bibtex FROM lectures WHERE id = 'l1'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.isNull(0) && c.isNull(1) && c.isNull(2))
        }
        migrated.execSQL("PRAGMA foreign_keys = ON")
        migrated.execSQL("INSERT INTO tags (id, name, createdAt) VALUES ('t1', 'Physics', 1)")
        migrated.execSQL("INSERT INTO lecture_tags (lectureId, tagId) VALUES ('l1', 't1')")
        migrated.query("SELECT COUNT(*) FROM tags WHERE name = 'physics'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        migrated.execSQL("DELETE FROM lectures WHERE id = 'l1'")
        migrated.query("SELECT COUNT(*) FROM lecture_tags").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM tags").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
