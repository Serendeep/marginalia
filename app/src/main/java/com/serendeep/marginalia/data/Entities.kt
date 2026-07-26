package com.serendeep.marginalia.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val orderIndex: Long,
    val colorIndex: Int = 0,
    val emoji: String? = null,
)

@Entity(
    tableName = "lectures",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("courseId")],
)
data class LectureEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val title: String,
    val createdAt: Long,
    val orderIndex: Long,
)

@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = LectureEntity::class,
            parentColumns = ["id"],
            childColumns = ["lectureId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("lectureId")],
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val lectureId: String,
    val fileName: String,
    val localPath: String,
    val pageCount: Int,
    val importedAt: Long,
    // Ordinal of this version within the lecture; re-importing a corrected PDF adds a higher one.
    val versionIndex: Int,
)

// A link between a spot on a PDF page and the notes written about it.
// Page position is stored as fractions of page size so it survives any zoom.
@Entity(
    tableName = "anchors",
    foreignKeys = [
        ForeignKey(
            entity = LectureEntity::class,
            parentColumns = ["id"],
            childColumns = ["lectureId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("lectureId"), Index("documentId")],
)
data class AnchorEntity(
    @PrimaryKey val id: String,
    val lectureId: String,
    val documentId: String,
    val pdfPage: Int,
    val pageXFraction: Float,
    val pageYFraction: Float,
    val label: Int,
    val createdAt: Long,
)

// A single handwritten stroke. Ink geometry is a serialized blob; everything else
// anchors the stroke to a spot in a document and a spot on the lecture canvas.
@Entity(
    tableName = "strokes",
    foreignKeys = [
        ForeignKey(
            entity = LectureEntity::class,
            parentColumns = ["id"],
            childColumns = ["lectureId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("lectureId"), Index("documentId"), Index("anchorId")],
)
data class StrokeEntity(
    @PrimaryKey val id: String,
    val lectureId: String,
    val documentId: String,
    val anchorId: String? = null,
    val pdfPage: Int,
    // Page rect visible when the stroke was started, in PDF points.
    val viewportLeft: Float,
    val viewportTop: Float,
    val viewportRight: Float,
    val viewportBottom: Float,
    // Stroke's own bounds on the note canvas, in dp.
    val boundsLeft: Float,
    val boundsTop: Float,
    val boundsRight: Float,
    val boundsBottom: Float,
    val startedAt: Long,
    val endedAt: Long,
    val brushColor: Long,
    val brushSizeDp: Float,
    val inkBlob: ByteArray,
    val surface: String = "MARGIN",
) {
    // ByteArray needs value-based equality for the data class to behave.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StrokeEntity) return false
        return id == other.id && inkBlob.contentEquals(other.inkBlob)
    }

    override fun hashCode(): Int = 31 * id.hashCode() + inkBlob.contentHashCode()
}
