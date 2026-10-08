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
    ],
    version = 5,
    exportSchema = true,
)
abstract class MarginaliaDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun lectureDao(): LectureDao
    abstract fun documentDao(): DocumentDao
    abstract fun strokeDao(): StrokeDao
    abstract fun anchorDao(): AnchorDao
    abstract fun studySessionDao(): StudySessionDao

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
    }
}
