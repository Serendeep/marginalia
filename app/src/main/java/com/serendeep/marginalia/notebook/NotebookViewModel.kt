package com.serendeep.marginalia.notebook

import androidx.ink.strokes.Stroke
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serendeep.marginalia.data.AnchorEntity
import com.serendeep.marginalia.data.InkStroke
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.ink.InkTool
import com.serendeep.marginalia.ink.Pens
import com.serendeep.marginalia.ink.StrokeEraser
import com.serendeep.marginalia.ink.toStroke
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import com.serendeep.marginalia.data.Box as AnchorBox

data class RenderedStroke(
    val record: InkStroke,
    val stroke: Stroke,
    val highlighted: Boolean = false,
)

private sealed interface EditOp {
    data class Add(val record: InkStroke) : EditOp
    data class Erase(val removed: List<InkStroke>, val added: List<InkStroke>) : EditOp
    data class RemoveAnchor(val anchor: AnchorEntity, val boundStrokeIds: List<String>) : EditOp
}

@HiltViewModel
class NotebookViewModel @Inject constructor(
    private val repository: MarginaliaRepository,
) : ViewModel() {

    private val _strokes = MutableStateFlow<List<RenderedStroke>>(emptyList())
    val strokes: StateFlow<List<RenderedStroke>> = _strokes.asStateFlow()

    private val _tool = MutableStateFlow(InkTool.PEN)
    val tool: StateFlow<InkTool> = _tool.asStateFlow()

    private val _anchors = MutableStateFlow<List<AnchorEntity>>(emptyList())
    val anchors: StateFlow<List<AnchorEntity>> = _anchors.asStateFlow()

    private val _activeAnchor = MutableStateFlow<AnchorEntity?>(null)
    val activeAnchor: StateFlow<AnchorEntity?> = _activeAnchor.asStateFlow()

    private val ops = ArrayDeque<EditOp>()
    private var lectureId: String? = null
    private var documentId: String = ""

    init {
        viewModelScope.launch {
            val id = ensureScratchLecture()
            lectureId = id
            _strokes.value = repository.loadStrokes(id).map { RenderedStroke(it, it.toStroke()) }
            repository.observeAnchors(id).collect { _anchors.value = it }
        }
    }

    fun setTool(tool: InkTool) {
        _tool.value = tool
    }

    fun toggleTool() {
        _tool.value = if (_tool.value == InkTool.PEN) InkTool.ERASER else InkTool.PEN
    }

    /** Registers the currently opened PDF so anchors and strokes can reference it. */
    fun onDocumentOpened(fileName: String, pageCount: Int) {
        val id = lectureId ?: return
        viewModelScope.launch {
            val existing = repository.observeDocuments(id).first()
                .firstOrNull { it.fileName == fileName && it.pageCount == pageCount }
            documentId = (existing ?: repository.importDocument(id, fileName, "", pageCount)).id
        }
    }

    fun onStrokeFinished(stroke: Stroke) {
        val id = lectureId ?: return
        val record = InkStroke(
            id = newId(),
            lectureId = id,
            documentId = documentId,
            anchorId = _activeAnchor.value?.id,
            pdfPage = 0,
            viewport = AnchorBox(0f, 0f, 0f, 0f),
            bounds = AnchorBox(0f, 0f, 0f, 0f),
            startedAt = now(),
            endedAt = now(),
            brushColor = stroke.brush.colorIntArgb.toLong() and 0xFFFFFFFFL,
            brushSizeDp = stroke.brush.size,
            batch = stroke.inputs,
        )
        _strokes.value = _strokes.value + RenderedStroke(record, stroke)
        ops.addLast(EditOp.Add(record))
        viewModelScope.launch { repository.saveStroke(record) }
    }

    fun eraseAt(x: Float, y: Float) {
        val radius = Pens.DEFAULT_SIZE_PX * 3f
        val current = _strokes.value
        val removed = ArrayList<InkStroke>()
        val added = ArrayList<InkStroke>()
        val next = ArrayList<RenderedStroke>(current.size)

        for (item in current) {
            val result = StrokeEraser.erase(item.record.batch, x, y, radius)
            if (result.untouched) {
                next.add(item)
                continue
            }
            removed.add(item.record)
            result.segments.forEach { segment ->
                val piece = item.record.copy(id = newId(), batch = segment)
                added.add(piece)
                next.add(RenderedStroke(piece, piece.toStroke()))
            }
        }
        if (removed.isEmpty()) return

        _strokes.value = next
        ops.addLast(EditOp.Erase(removed, added))
        viewModelScope.launch {
            removed.forEach { repository.deleteStroke(it.id) }
            repository.saveStrokes(added)
        }
    }

    fun undo() {
        when (val op = ops.removeLastOrNull() ?: return) {
            is EditOp.Add -> {
                _strokes.value = _strokes.value.filterNot { it.record.id == op.record.id }
                viewModelScope.launch { repository.deleteStroke(op.record.id) }
            }

            is EditOp.Erase -> {
                val addedIds = op.added.map { it.id }.toSet()
                val restored = op.removed.map { RenderedStroke(it, it.toStroke()) }
                _strokes.value = _strokes.value.filterNot { it.record.id in addedIds } + restored
                viewModelScope.launch {
                    addedIds.forEach { repository.deleteStroke(it) }
                    repository.saveStrokes(op.removed)
                }
            }

            is EditOp.RemoveAnchor -> {
                val ids = op.boundStrokeIds.toSet()
                _strokes.value = _strokes.value.map {
                    if (it.record.id in ids) it.copy(record = it.record.copy(anchorId = op.anchor.id)) else it
                }
                viewModelScope.launch { repository.restoreAnchor(op.anchor, op.boundStrokeIds) }
            }
        }
    }

    /** Removes a link. The bound ink stays; only the connection goes. Undoable. */
    fun removeAnchor(anchorId: String) {
        val anchor = _anchors.value.firstOrNull { it.id == anchorId } ?: return
        if (_activeAnchor.value?.id == anchorId) _activeAnchor.value = null
        viewModelScope.launch {
            val bound = repository.removeAnchorAndUnbind(anchorId)
            val ids = bound.toSet()
            _strokes.value = _strokes.value.map {
                if (it.record.id in ids) it.copy(record = it.record.copy(anchorId = null)) else it
            }
            ops.addLast(EditOp.RemoveAnchor(anchor, bound))
        }
    }

    /** Places an anchor on a page point; strokes written next bind to it until done. */
    fun placeAnchor(pdfPage: Int, xFraction: Float, yFraction: Float) {
        val id = lectureId ?: return
        viewModelScope.launch {
            _activeAnchor.value = repository.createAnchor(id, documentId, pdfPage, xFraction, yFraction)
        }
    }

    fun finishAnchorBinding() {
        val anchor = _activeAnchor.value ?: return
        _activeAnchor.value = null
        viewModelScope.launch {
            // An anchor nothing was written against is noise; drop it.
            val bound = _strokes.value.any { it.record.anchorId == anchor.id }
            if (!bound) repository.deleteAnchor(anchor.id)
        }
    }

    /** Briefly highlights the strokes bound to an anchor. */
    fun flashAnchor(anchorId: String) {
        viewModelScope.launch {
            _strokes.value = _strokes.value.map {
                if (it.record.anchorId == anchorId) {
                    it.copy(stroke = it.record.toStroke(colorOverride = HIGHLIGHT_COLOR), highlighted = true)
                } else {
                    it
                }
            }
            delay(1200)
            _strokes.value = _strokes.value.map {
                if (it.highlighted) it.copy(stroke = it.record.toStroke(), highlighted = false) else it
            }
        }
    }

    private suspend fun ensureScratchLecture(): String {
        val course = repository.observeCourses().first().firstOrNull()
            ?: repository.createCourse("My notes")
        val lecture = repository.observeLectures(course.id).first().firstOrNull()
            ?: repository.createLecture(course.id, "Scratch")
        return lecture.id
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private fun now(): Long = System.currentTimeMillis()

    private companion object {
        const val HIGHLIGHT_COLOR: Int = 0xFF3557A6.toInt()
    }
}
