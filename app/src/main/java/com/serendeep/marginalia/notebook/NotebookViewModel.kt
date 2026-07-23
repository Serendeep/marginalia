package com.serendeep.marginalia.notebook

import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInputBatch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serendeep.marginalia.data.AnchorEntity
import com.serendeep.marginalia.data.DocumentEntity
import com.serendeep.marginalia.data.InkStroke
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.ink.InkTool
import com.serendeep.marginalia.ink.Pen
import com.serendeep.marginalia.ink.Pens
import com.serendeep.marginalia.ink.StrokeEraser
import com.serendeep.marginalia.ink.toStroke
import com.serendeep.marginalia.sync.ScrollSync
import com.serendeep.marginalia.sync.SyncPair
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
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

    private val _selectedPen = MutableStateFlow(Pen.GRAPHITE)
    val selectedPen: StateFlow<Pen> = _selectedPen.asStateFlow()

    private val _penDown = MutableStateFlow(false)
    val penDown: StateFlow<Boolean> = _penDown.asStateFlow()

    private val _anchors = MutableStateFlow<List<AnchorEntity>>(emptyList())
    val anchors: StateFlow<List<AnchorEntity>> = _anchors.asStateFlow()

    private val _activeAnchor = MutableStateFlow<AnchorEntity?>(null)
    val activeAnchor: StateFlow<AnchorEntity?> = _activeAnchor.asStateFlow()

    /** Vertical scroll of the note sheet, in canvas px, 0 at the very top. */
    private val _canvasOffset = MutableStateFlow(0f)
    val canvasOffset: StateFlow<Float> = _canvasOffset.asStateFlow()

    /** Continuous position the PDF pane should scroll to; consumed via [onPdfScrollHandled]. */
    private val _pdfScrollTarget = MutableStateFlow<Float?>(null)
    val pdfScrollTarget: StateFlow<Float?> = _pdfScrollTarget.asStateFlow()

    /** Latest imported document with a file still on disk, or null if none. */
    private val _document = MutableStateFlow<DocumentEntity?>(null)
    val document: StateFlow<DocumentEntity?> = _document.asStateFlow()

    private val ops = ArrayDeque<EditOp>()
    private val redos = ArrayDeque<EditOp>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    // Display-only mirrors of firstVisiblePage/pdfPageCount below, for the page
    // indicator pill. StateFlow only notifies collectors on an actual value
    // change, so despite onPdfScrollPos firing every scroll frame, this only
    // triggers recomposition once per page crossed.
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _pageCount = MutableStateFlow(0)
    val pageCount: StateFlow<Int> = _pageCount.asStateFlow()

    private var lectureId: String? = null
    private var lectureJob: Job? = null
    private var documentId: String = ""
    private var pdfPageCount = 0
    private var inkPaneHeightPx = 1600f
    private var firstVisiblePage = 0
    private var pdfPos = 0f

    // Feedback latch: the pane the user drove last owns the sync for a short
    // window, so the programmatic echo from the other pane is ignored.
    private enum class Driver { NONE, PDF, CANVAS }
    private var driver = Driver.NONE
    private var drivenAt = 0L
    private var canvasAnim: Job? = null
    private var penActive = false
    private var pendingCanvasTarget: Float? = null
    private var expectedPdfPos: Float? = null
    private var syncCanvasAfterRequest = false

    /** Switches the notebook to a different lecture, resetting all per-lecture state. */
    fun openLecture(id: String) {
        if (lectureId == id) return
        lectureJob?.cancel()
        canvasAnim?.cancel()

        lectureId = id
        documentId = ""
        pdfPageCount = 0
        _tool.value = InkTool.PEN
        firstVisiblePage = 0
        pdfPos = 0f
        _currentPage.value = 0
        _pageCount.value = 0
        ops.clear()
        redos.clear()
        syncUndoState()
        driver = Driver.NONE
        drivenAt = 0L
        penActive = false
        pendingCanvasTarget = null
        expectedPdfPos = null
        syncCanvasAfterRequest = false

        _strokes.value = emptyList()
        _anchors.value = emptyList()
        _activeAnchor.value = null
        _canvasOffset.value = 0f
        _pdfScrollTarget.value = null
        _document.value = null

        lectureJob = viewModelScope.launch {
            _strokes.value = repository.loadStrokes(id).map { RenderedStroke(it, it.toStroke()) }
            launch { repository.observeAnchors(id).collect { _anchors.value = it } }
            launch {
                repository.observeDocuments(id).collect { documents ->
                    val latest = documents
                        .filter { it.localPath.isNotEmpty() && File(it.localPath).exists() }
                        .maxByOrNull { it.versionIndex }
                    _document.value = latest
                    documentId = latest?.id ?: ""
                    pdfPageCount = latest?.pageCount ?: 0
                    _pageCount.value = pdfPageCount
                }
            }
        }
    }

    fun setTool(tool: InkTool) {
        _tool.value = tool
    }

    fun toggleTool() {
        _tool.value = if (_tool.value == InkTool.PEN) InkTool.ERASER else InkTool.PEN
    }

    fun selectPen(pen: Pen) {
        _selectedPen.value = pen
        _tool.value = InkTool.PEN
    }

    fun onStrokeFinished(stroke: Stroke) {
        val id = lectureId ?: return
        // The stroke arrives already in canvas space, so its bounds are too.
        val record = InkStroke(
            id = newId(),
            lectureId = id,
            documentId = documentId,
            anchorId = _activeAnchor.value?.id,
            pdfPage = firstVisiblePage,
            // The correspondence pair that lets sync restore this exact alignment:
            // what the PDF showed, and where the sheet sat, as the ink went down.
            viewport = AnchorBox(pdfPos, _canvasOffset.value, 0f, 0f),
            bounds = strokeBounds(stroke.inputs),
            startedAt = now(),
            endedAt = now(),
            brushColor = stroke.brush.colorIntArgb.toLong() and 0xFFFFFFFFL,
            brushSizeDp = stroke.brush.size,
            batch = stroke.inputs,
        )
        _strokes.value = _strokes.value + RenderedStroke(record, stroke)
        pushOp(EditOp.Add(record))
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
        pushOp(EditOp.Erase(removed, added))
        viewModelScope.launch {
            removed.forEach { repository.deleteStroke(it.id) }
            repository.saveStrokes(added)
        }
    }

    fun undo() {
        val op = ops.removeLastOrNull() ?: return
        when (op) {
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
        redos.addLast(op)
        syncUndoState()
    }

    /** Re-applies the most recently undone operation. */
    fun redo() {
        val op = redos.removeLastOrNull() ?: return
        when (op) {
            is EditOp.Add -> {
                _strokes.value = _strokes.value + RenderedStroke(op.record, op.record.toStroke())
                viewModelScope.launch { repository.saveStroke(op.record) }
            }

            is EditOp.Erase -> {
                val removedIds = op.removed.map { it.id }.toSet()
                val pieces = op.added.map { RenderedStroke(it, it.toStroke()) }
                _strokes.value = _strokes.value.filterNot { it.record.id in removedIds } + pieces
                viewModelScope.launch {
                    removedIds.forEach { repository.deleteStroke(it) }
                    repository.saveStrokes(op.added)
                }
            }

            is EditOp.RemoveAnchor -> {
                val ids = op.boundStrokeIds.toSet()
                _strokes.value = _strokes.value.map {
                    if (it.record.id in ids) it.copy(record = it.record.copy(anchorId = null)) else it
                }
                viewModelScope.launch { repository.removeAnchorAndUnbind(op.anchor.id) }
            }
        }
        ops.addLast(op)
        syncUndoState()
    }

    /** Records a fresh edit; anything undone before it can no longer be redone. */
    private fun pushOp(op: EditOp) {
        ops.addLast(op)
        redos.clear()
        syncUndoState()
    }

    private fun syncUndoState() {
        _canUndo.value = ops.isNotEmpty()
        _canRedo.value = redos.isNotEmpty()
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
            pushOp(EditOp.RemoveAnchor(anchor, bound))
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

    fun onInkPaneHeight(px: Float) {
        if (px > 0f) inkPaneHeightPx = px
    }

    /** Finger scroll on the note sheet. The canvas becomes the sync driver. */
    fun onCanvasScrolledBy(delta: Float) {
        canvasAnim?.cancel()
        _canvasOffset.value = (_canvasOffset.value + delta).coerceAtLeast(0f)
        driver = Driver.CANVAS
        drivenAt = now()
        if (pdfPageCount <= 0) return
        val pos = sync().pdfForCanvas(_canvasOffset.value)
        if (kotlin.math.abs(pos - pdfPos) > POS_EPSILON) {
            expectedPdfPos = pos
            syncCanvasAfterRequest = false
            _pdfScrollTarget.value = pos
        }
    }

    /** A real touch landed on the PDF pane; only that makes the PDF the driver. */
    fun onPdfTouched() {
        driver = Driver.PDF
        drivenAt = now()
    }

    /** Deliberate navigation (outline, anchor jumps): scroll the PDF, then bring the notes along. */
    fun requestPdfPage(page: Int) {
        expectedPdfPos = page.toFloat()
        syncCanvasAfterRequest = true
        _pdfScrollTarget.value = page.toFloat()
    }

    /** The stylus is touching the sheet; the canvas must not move under it. */
    fun setPenActive(active: Boolean) {
        penActive = active
        _penDown.value = active
        if (active) {
            canvasAnim?.cancel()
        } else {
            pendingCanvasTarget?.let {
                pendingCanvasTarget = null
                animateCanvasTo(it)
            }
        }
    }

    /**
     * PDF pane reports its continuous scroll position. Reports alone never claim
     * driverhood: they move the canvas only after a real touch on the PDF pane
     * ([onPdfTouched]) or as the tail of a deliberate navigation ([requestPdfPage]).
     */
    fun onPdfScrollPos(pos: Float) {
        pdfPos = pos
        firstVisiblePage = pos.toInt()
        _currentPage.value = firstVisiblePage
        // While our own scroll request is in flight, every report is an echo.
        if (_pdfScrollTarget.value != null) return
        val expected = expectedPdfPos
        if (expected != null && kotlin.math.abs(pos - expected) < POS_EPSILON) {
            expectedPdfPos = null
            if (syncCanvasAfterRequest) {
                syncCanvasAfterRequest = false
                syncCanvasToPos(pos)
            }
            return
        }
        if (driver == Driver.PDF && now() - drivenAt < PDF_DRIVE_WINDOW_MS) {
            drivenAt = now()
            syncCanvasToPos(pos)
        }
    }

    private fun syncCanvasToPos(pos: Float) {
        val target = sync().canvasForPdf(pos)
        if (penActive) pendingCanvasTarget = target else animateCanvasTo(target)
    }

    fun onPdfScrollHandled() {
        _pdfScrollTarget.value = null
    }

    // Rebuilding the mapping sorts every stroke; scroll events arrive far too
    // often for that, so the instance is cached until its inputs change.
    private var syncCache: ScrollSync? = null
    private var syncCacheKey: Triple<List<RenderedStroke>, Int, Float>? = null

    private fun sync(): ScrollSync {
        val key = Triple(_strokes.value, pdfPageCount, inkPaneHeightPx)
        syncCache?.let { if (key == syncCacheKey) return it }
        val built = ScrollSync(
            pairs = key.first.map { rendered ->
                val v = rendered.record.viewport
                // Older strokes predate correspondence pairs; approximate with the
                // page they were written on and where their ink starts.
                if (v.left == 0f && v.top == 0f && v.right == 0f && v.bottom == 0f) {
                    SyncPair(rendered.record.pdfPage.toFloat(), rendered.record.bounds.top)
                } else {
                    SyncPair(v.left, v.top)
                }
            },
            pageCount = pdfPageCount,
            pageHeightPx = inkPaneHeightPx,
        )
        syncCache = built
        syncCacheKey = key
        return built
    }

    private fun animateCanvasTo(target: Float) {
        canvasAnim?.cancel()
        if (target == _canvasOffset.value) return
        canvasAnim = viewModelScope.launch {
            val start = _canvasOffset.value
            val startedAt = now()
            while (true) {
                val t = ((now() - startedAt).toFloat() / CANVAS_ANIM_MS).coerceAtMost(1f)
                val eased = 1f - (1f - t) * (1f - t)
                _canvasOffset.value = (start + (target - start) * eased).coerceAtLeast(0f)
                if (t >= 1f) break
                delay(16)
            }
        }
    }

    private fun strokeBounds(batch: StrokeInputBatch): AnchorBox {
        if (batch.size == 0) return AnchorBox(0f, 0f, 0f, 0f)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in 0 until batch.size) {
            val p = batch.get(i)
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        return AnchorBox(minX, minY, maxX, maxY)
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private fun now(): Long = System.currentTimeMillis()

    private companion object {
        const val HIGHLIGHT_COLOR: Int = 0xFF3557A6.toInt()
        const val PDF_DRIVE_WINDOW_MS = 2000L
        const val POS_EPSILON = 0.05f
        const val CANVAS_ANIM_MS = 250f
    }
}
