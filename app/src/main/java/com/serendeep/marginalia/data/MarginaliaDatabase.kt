package com.serendeep.marginalia.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CourseEntity::class,
        LectureEntity::class,
        DocumentEntity::class,
        StrokeEntity::class,
        AnchorEntity::class,
        StudySessionEntity::class,
        PageTextEntity::class,
        IndexedDocumentEntity::class,
        HighlightEntity::class,
        CardEntity::class,
        ReviewLogEntity::class,
        TagEntity::class,
        LectureTagEntity::class,
        InkTextEntity::class,
        InkIndexStateEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class MarginaliaDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun lectureDao(): LectureDao
    abstract fun documentDao(): DocumentDao
    abstract fun strokeDao(): StrokeDao
    abstract fun anchorDao(): AnchorDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun searchDao(): SearchDao
    abstract fun highlightDao(): HighlightDao
    abstract fun cardDao(): CardDao
    abstract fun tagDao(): TagDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `anchors` (
                        `id` TEXT NOT NULL, `lectureId` TEXT NOT NULL,
                        `documentId` TEXT NOT NULL, `pdfPage` INTEGER NOT NULL,
                        `pageXFraction` REAL NOT NULL, `pageYFraction` REAL NOT NULL,
                        `label` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`lectureId`) REFERENCES `lectures`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_anchors_lectureId` ON `anchors` (`lectureId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_anchors_documentId` ON `anchors` (`documentId`)")
                db.execSQL("ALTER TABLE `strokes` ADD COLUMN `anchorId` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_strokes_anchorId` ON `strokes` (`anchorId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN colorIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE courses ADD COLUMN emoji TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE strokes ADD COLUMN surface TEXT NOT NULL DEFAULT 'MARGIN'")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lectures ADD COLUMN lastPage INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE lectures ADD COLUMN lastOpenedAt INTEGER")
                db.execSQL("ALTER TABLE lectures ADD COLUMN readingStatus TEXT NOT NULL DEFAULT 'TO_READ'")
                db.execSQL(
                    "UPDATE lectures SET readingStatus = 'READING' " +
                        "WHERE id IN (SELECT DISTINCT lectureId FROM strokes)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `study_sessions` (
                        `id` TEXT NOT NULL, `lectureId` TEXT, `kind` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_sessions_startedAt` ON `study_sessions` (`startedAt`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `page_text` USING FTS4(" +
                        "`documentId` TEXT NOT NULL, `page` INTEGER NOT NULL, `text` TEXT NOT NULL, " +
                        "tokenize=unicode61, notindexed=`documentId`, notindexed=`page`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `indexed_documents` (" +
                        "`documentId` TEXT NOT NULL, `indexedAt` INTEGER NOT NULL, PRIMARY KEY(`documentId`))",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `highlights` (
                        `id` TEXT NOT NULL, `lectureId` TEXT NOT NULL, `documentId` TEXT NOT NULL,
                        `page` INTEGER NOT NULL, `text` TEXT NOT NULL, `color` INTEGER NOT NULL,
                        `strokeId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`lectureId`) REFERENCES `lectures`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_highlights_lectureId` ON `highlights` (`lectureId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_highlights_strokeId` ON `highlights` (`strokeId`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cards` (
                        `id` TEXT NOT NULL, `lectureId` TEXT, `documentId` TEXT, `page` INTEGER,
                        `frontText` TEXT, `frontImagePath` TEXT, `backText` TEXT, `backInk` BLOB,
                        `source` TEXT NOT NULL, `highlightId` TEXT,
                        `state` TEXT NOT NULL DEFAULT 'NEW', `dueAt` INTEGER NOT NULL,
                        `intervalDays` REAL NOT NULL DEFAULT 0, `ease` REAL NOT NULL DEFAULT 2.5,
                        `reps` INTEGER NOT NULL DEFAULT 0, `lapses` INTEGER NOT NULL DEFAULT 0,
                        `step` INTEGER NOT NULL DEFAULT 0, `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`lectureId`) REFERENCES `lectures`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_dueAt` ON `cards` (`dueAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_lectureId` ON `cards` (`lectureId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `review_log` (
                        `id` TEXT NOT NULL, `cardId` TEXT NOT NULL, `grade` INTEGER NOT NULL,
                        `reviewedAt` INTEGER NOT NULL, `prevIntervalDays` REAL, `newIntervalDays` REAL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`cardId`) REFERENCES `cards`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_log_reviewedAt` ON `review_log` (`reviewedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_log_cardId` ON `review_log` (`cardId`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tags` (`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL COLLATE NOCASE, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `lecture_tags` (
                        `lectureId` TEXT NOT NULL, `tagId` TEXT NOT NULL,
                        PRIMARY KEY(`lectureId`, `tagId`),
                        FOREIGN KEY(`lectureId`) REFERENCES `lectures`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lecture_tags_tagId` ON `lecture_tags` (`tagId`)")
                db.execSQL("ALTER TABLE lectures ADD COLUMN doi TEXT")
                db.execSQL("ALTER TABLE lectures ADD COLUMN arxivId TEXT")
                db.execSQL("ALTER TABLE lectures ADD COLUMN bibtex TEXT")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `ink_text` USING FTS4(" +
                        "`lectureId` TEXT NOT NULL, `blockKey` TEXT NOT NULL, `page` INTEGER, `text` TEXT NOT NULL, " +
                        "tokenize=unicode61, notindexed=`lectureId`, notindexed=`blockKey`, notindexed=`page`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ink_index_state` (
                        `lectureId` TEXT NOT NULL, `strokesHash` TEXT NOT NULL, `indexedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`lectureId`),
                        FOREIGN KEY(`lectureId`) REFERENCES `lectures`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
