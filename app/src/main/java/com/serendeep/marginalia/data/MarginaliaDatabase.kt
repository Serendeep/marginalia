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
    ],
    version = 3,
    exportSchema = true,
)
abstract class MarginaliaDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun lectureDao(): LectureDao
    abstract fun documentDao(): DocumentDao
    abstract fun strokeDao(): StrokeDao
    abstract fun anchorDao(): AnchorDao

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
    }
}
