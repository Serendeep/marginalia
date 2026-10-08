package com.serendeep.marginalia.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
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
    val lastPage: Int = 0,
    val lastOpenedAt: Long? = null,
    val readingStatus: String = ReadingStatus.TO_READ.name,
    val doi: String? = null,
    val arxivId: String? = null,
    val bibtex: String? = null,
)

enum class ReadingStatus { TO_READ, READING, DONE }

enum class SessionKind { FOCUS, READING, REVIEW }

// Time spent studying. No foreign key: history outlives a deleted lecture.
@Entity(tableName = "study_sessions", indices = [Index("startedAt")])
data class StudySessionEntity(
    @PrimaryKey val id: String,
    val lectureId: String?,
    val kind: String,
    val startedAt: Long,
    val endedAt: Long,
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

// Extracted text of one PDF page. Full-text index only; documentId and page ride along unindexed.
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61, notIndexed = ["documentId", "page"])
@Entity(tableName = "page_text")
data class PageTextEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Int,
    val documentId: String,
    val page: Int,
    val text: String,
)

// A document whose pages are all in page_text. Absence means indexing is pending or interrupted.
@Entity(tableName = "indexed_documents")
data class IndexedDocumentEntity(
    @PrimaryKey val documentId: String,
    val indexedAt: Long,
)

// PDF text picked up by a highlighter stroke. strokeId ties it to the ink so erasing removes it.
@Entity(
    tableName = "highlights",
    foreignKeys = [
        ForeignKey(
            entity = LectureEntity::class,
            parentColumns = ["id"],
            childColumns = ["lectureId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("lectureId"), Index("strokeId")],
)
data class HighlightEntity(
    @PrimaryKey val id: String,
    val lectureId: String,
    val documentId: String,
    val page: Int,
    val text: String,
    val color: Long,
    val strokeId: String,
    val createdAt: Long,
)

enum class CardSource { LASSO, HIGHLIGHT, TYPED, AI }

enum class CardState { NEW, LEARNING, REVIEW, RELEARNING }

// A flashcard. The lecture link is optional: typed cards can float free.
@Entity(
    tableName = "cards",
    foreignKeys = [
        ForeignKey(
            entity = LectureEntity::class,
            parentColumns = ["id"],
            childColumns = ["lectureId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("dueAt"), Index("lectureId")],
)
data class CardEntity(
    @PrimaryKey val id: String,
    val lectureId: String? = null,
    val documentId: String? = null,
    val page: Int? = null,
    val frontText: String? = null,
    val frontImagePath: String? = null,
    val backText: String? = null,
    val backInk: ByteArray? = null,
    val source: String,
    val highlightId: String? = null,
    val state: String = CardState.NEW.name,
    val dueAt: Long,
    val intervalDays: Double = 0.0,
    val ease: Double = 2.5,
    val reps: Int = 0,
    val lapses: Int = 0,
    val step: Int = 0,
    val createdAt: Long,
) {
    val cardState: CardState
        get() = runCatching { CardState.valueOf(state) }.getOrDefault(CardState.NEW)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CardEntity) return false
        return id == other.id && state == other.state && dueAt == other.dueAt &&
            intervalDays == other.intervalDays && ease == other.ease && reps == other.reps &&
            lapses == other.lapses && step == other.step && frontText == other.frontText &&
            backText == other.backText && frontImagePath == other.frontImagePath
    }

    override fun hashCode(): Int = 31 * id.hashCode() + dueAt.hashCode()
}

// One graded answer. prevIntervalDays is null unless the card was already in REVIEW state,
// which is what separates mature reviews from learning steps in retention stats.
@Entity(
    tableName = "review_log",
    foreignKeys = [
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("reviewedAt"), Index("cardId")],
)
data class ReviewLogEntity(
    @PrimaryKey val id: String,
    val cardId: String,
    val grade: Int,
    val reviewedAt: Long,
    val prevIntervalDays: Double?,
    val newIntervalDays: Double?,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "lecture_tags",
    primaryKeys = ["lectureId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = LectureEntity::class,
            parentColumns = ["id"],
            childColumns = ["lectureId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class LectureTagEntity(val lectureId: String, val tagId: String)
