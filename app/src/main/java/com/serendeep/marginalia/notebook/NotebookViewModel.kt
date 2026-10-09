package com.serendeep.marginalia.notebook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import coil3.ImageLoader
import com.serendeep.marginalia.data.CardSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInputBatch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serendeep.marginalia.data.AnchorEntity
import com.serendeep.marginalia.handwriting.InkIndexer
import com.serendeep.marginalia.handwriting.InkRecognizer
import com.serendeep.marginalia.handwriting.recognizeLines
import com.serendeep.marginalia.data.DocumentEntity
import com.serendeep.marginalia.data.HighlightEntity
import com.serendeep.marginalia.data.InkStroke
import com.serendeep.marginalia.data.InkSurface
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.data.ReadingStatus
import com.serendeep.marginalia.ink.EraserSize
import com.serendeep.marginalia.ink.Extent
import com.serendeep.marginalia.ink.PenColors
import com.serendeep.marginalia.ink.PenWidth
import com.serendeep.marginalia.ink.PencilAction
import com.serendeep.marginalia.ink.ScratchOut
import com.serendeep.marginalia.ink.ShapeSnap
import com.serendeep.marginalia.ink.StrokeTransform
import com.serendeep.marginalia.ink.bounds
import com.serendeep.marginalia.ink.doubleTapTool
import com.serendeep.marginalia.ink.fractionInsidePolygon
import com.serendeep.marginalia.ink.recolored
import com.serendeep.marginalia.ink.toBatch
import com.serendeep.marginalia.ink.toPoints
import com.serendeep.marginalia.ink.transformed
import com.serendeep.marginalia.ink.InkPt
import com.serendeep.marginalia.ink.InkTool
import com.serendeep.marginalia.ink.Pen
import com.serendeep.marginalia.ink.Pens
import com.serendeep.marginalia.ink.StrokeEraser
import com.serendeep.marginalia.ink.toStroke
import com.serendeep.marginalia.search.TextIndexer
import com.serendeep.marginalia.sync.ScrollSync
import com.serendeep.marginalia.study.FocusState
import com.serendeep.marginalia.study.FocusTimer
import com.serendeep.marginalia.study.StudyTracker
import com.serendeep.marginalia.sync.SyncPair
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
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
    data class AddGroup(val records: List<InkStroke>) : EditOp
    data class Transform(val before: List<InkStroke>, val after: List<InkStroke>) : EditOp
    data class Erase(val removed: List<InkStroke>, val added: List<InkStroke>) : EditOp
    data class RemoveAnchor(val anchor: AnchorEntity, val boundStrokeIds: List<String>) : EditOp
}

@OptIn(FlowPreview::class)
@HiltViewModel
class NotebookViewModel @Inject constructor(
    private val repository: MarginaliaRepository,
    private val tracker: StudyTracker,
    private val focusTimer: FocusTimer,
    private val indexer: TextIndexer,
    private val inkIndexer: InkIndexer,
    private val recognizer: InkRecognizer,
    @ApplicationContext private val context: Context,
    val imageLoader: ImageLoader,
) : ViewModel() {

    /** A lasso capture waiting in the "Make card" sheet. */
    data class LassoDraft(val id: String, val imagePath: String, val page: Int?, val back: String = "")

    /** A selection being read as text; [text] is null until recognition finishes. */
    class TextDraft(val page: Int?, val render: suspend () -> Bitmap, val text: String? = null)

    private val _textDraft = MutableStateFlow<TextDraft?>(null)
    val textDraft: StateFlow<TextDraft?> = _textDraft.asStateFlow()
    val modelState = recognizer.state
    private var convertJob: Job? = null

    /** Reads the selected strokes as text; [render] produces the crop used if it becomes a card. */
    fun convertSelection(page: Int?, render: suspend () -> Bitmap) {
        val sel = _selection.value ?: return
        val draft = TextDraft(page, render)
        _textDraft.value = draft
        convertJob?.cancel()
        convertJob = viewModelScope.launch(Dispatchers.Default) {
            val text = recognizer.recognizeLines(sel.items.map { it.record }).joinToString("\n")
            _textDraft.value = TextDraft(page, render, text)
        }
    }

    fun dismissText() {
        convertJob?.cancel()
        _textDraft.value = null
    }

    fun cardFromText(text: String) {
        val draft = _textDraft.value ?: return
        _textDraft.value = null
        stageLassoCard(draft.page, text, draft.render)
    }

    private val _lassoDraft = MutableStateFlow<LassoDraft?>(null)
    val lassoDraft: StateFlow<LassoDraft?> = _lassoDraft.asStateFlow()

    /**
     * Renders and saves a crop for a new card, then shows the sheet. The lasso is one-shot, so the
     * pen comes back at once; everything heavy runs on IO.
     */
    fun stageLassoCard(page: Int?, back: String = "", render: suspend () -> Bitmap) {
        _tool.value = InkTool.PEN
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = runCatching { render() }.getOrNull() ?: return@launch
            val id = newId()
            val file = File(File(context.filesDir, "cards").also { it.mkdirs() }, "$id.png")
            val saved = runCatching { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            bitmap.recycle()
            if (saved.isFailure) {
                file.delete()
                return@launch
            }
            _lassoDraft.value = LassoDraft(id, file.absolutePath, page ?: firstVisiblePage.takeIf { documentId.isNotEmpty() }, back)
        }
    }

    fun saveLassoCard(back: String) {
        val draft = _lassoDraft.value ?: return
        val lecture = lectureId ?: return
        _lassoDraft.value = null
        viewModelScope.launch(Dispatchers.IO) {
            repository.createCard(
                source = CardSource.LASSO,
                lectureId = lecture,
                documentId = documentId.ifEmpty { null },
                page = draft.page,
                frontImagePath = draft.imagePath,
                backText = back,
                id = draft.id,
            )
        }
    }

    /** Hands the staged crop to [onCrop] on the main thread and drops the card draft. */
    fun askAboutLasso(onCrop: (ByteArray) -> Unit) {
        val draft = _lassoDraft.value ?: return
        _lassoDraft.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = runCatching { File(draft.imagePath).readBytes() }.getOrNull()
            File(draft.imagePath).delete()
            if (bytes != null) withContext(Dispatchers.Main) { onCrop(bytes) }
        }
    }

    fun cancelLassoCard() {
        val draft = _lassoDraft.value ?: return
        _lassoDraft.value = null
        viewModelScope.launch(Dispatchers.IO) { File(draft.imagePath).delete() }
    }

    val focus: StateFlow<FocusState> = focusTimer.state

    private val prefs by lazy { context.getSharedPreferences(com.serendeep.marginalia.shell.PREFS, Context.MODE_PRIVATE) }
    private val _eraserSize = MutableStateFlow(EraserSize.MEDIUM)
    val eraserSize: StateFlow<EraserSize> = _eraserSize.asStateFlow()

    private val _penWidth = MutableStateFlow(PenWidth.MEDIUM)
    val penWidth: StateFlow<PenWidth> = _penWidth.asStateFlow()

    /** Palette choice behind each of the three pen swatches; see [PenColors]. */
    private val _swatches = MutableStateFlow(List(PenColors.SWATCHES) { it })
    val swatches: StateFlow<List<Int>> = _swatches.asStateFlow()

    private var previousTool = InkTool.PEN

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            prefs.getString(ERASER_SIZE_KEY, null)
                ?.let { runCatching { EraserSize.valueOf(it) }.getOrNull() }
                ?.let { _eraserSize.value = it }
            _penWidth.value = PenColors.widthFrom(prefs.getString(PenColors.WIDTH_KEY, null))
            _swatches.value = List(PenColors.SWATCHES) { slot ->
                PenColors.choiceFrom(prefs.getInt(PenColors.swatchKey(slot), -1), slot)
            }
        }
    }

    fun setPenWidth(width: PenWidth) {
        _penWidth.value = width
        prefs.edit().putString(PenColors.WIDTH_KEY, width.name).apply()
    }

    fun setSwatch(slot: Int, choice: Int) {
        _swatches.value = _swatches.value.toMutableList().also { it[slot] = choice }
        prefs.edit().putInt(PenColors.swatchKey(slot), choice).apply()
    }

    /** Runs the user's chosen Pencil double-tap action. */
    fun onPencilDoubleTap() {
        val action = PenColors.actionFrom(prefs.getString(PenColors.ACTION_KEY, null))
        if (action == PencilAction.UNDO) {
            undo()
            return
        }
        doubleTapTool(action, _tool.value, previousTool)?.let { _tool.value = it }
    }

    fun setEraserSize(size: EraserSize) {
        _eraserSize.value = size
        prefs.edit().putString(ERASER_SIZE_KEY, size.name).apply()
    }

    fun toggleFocus() = focusTimer.toggle()

    fun onForeground() {
        lectureId?.let(tracker::start)
    }

    fun onBackground() = tracker.stop()

    private val _strokes = MutableStateFlow<List<RenderedStroke>>(emptyList())
    val strokes: StateFlow<List<RenderedStroke>> = _strokes.asStateFlow()

    private val _pageStrokes = MutableStateFlow<List<RenderedStroke>>(emptyList())
    val pageStrokes: StateFlow<List<RenderedStroke>> = _pageStrokes.asStateFlow()

    private val _tool = MutableStateFlow(InkTool.PEN)
    val tool: StateFlow<InkTool> = _tool.asStateFlow()

    private val _selection = MutableStateFlow<SelectionState?>(null)
    val selection: StateFlow<SelectionState?> = _selection.asStateFlow()

    init {
        viewModelScope.launch {
            var prev = _tool.value
            _tool.collect { now ->
                if (now != prev) {
                    previousTool = prev
                    prev = now
                }
                if (now != InkTool.SELECT) _selection.value = null
            }
        }
    }

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

    private val _lectureTitle = MutableStateFlow("Notebook")
    val lectureTitle: StateFlow<String> = _lectureTitle.asStateFlow()

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

    private val _status = MutableStateFlow(ReadingStatus.TO_READ.name)
    private val markDoneDismissed = MutableStateFlow(false)

    /** True on the last page of a document that is not yet marked done. */
    val showMarkDone: StateFlow<Boolean> = combine(
        _currentPage, _pageCount, _status, markDoneDismissed,
    ) { page, count, status, dismissed ->
        count > 1 && page >= count - 1 && status != ReadingStatus.DONE.name && !dismissed
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun markDone() {
        val id = lectureId ?: return
        markDoneDismissed.value = true
        viewModelScope.launch { repository.setReadingStatus(id, ReadingStatus.DONE) }
    }

    fun dismissMarkDone() {
        markDoneDismissed.value = true
    }

    // True while a saved page is being scrolled back to; page reports then are echoes, not reading.
    private var restoring = false
    private var restoreChecked = false

    private var lectureId: String? = null
    private var startPage: Int? = null
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
    fun openLecture(id: String, startPage: Int? = null) {
        if (lectureId == id) return
        this.startPage = startPage
        parkedHighlights.clear()
        lectureJob?.cancel()
        canvasAnim?.cancel()

        lectureId = id
        tracker.start(id)
        inkIndexer.onNotebookOpened()
        restoring = false
        restoreChecked = false
        _status.value = ReadingStatus.TO_READ.name
        markDoneDismissed.value = false
        documentId = ""
        pdfPageCount = 0
        _tool.value = InkTool.PEN
        _selection.value = null
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
        _pageStrokes.value = emptyList()
        _anchors.value = emptyList()
        _activeAnchor.value = null
        _canvasOffset.value = 0f
        _pdfScrollTarget.value = null
        _document.value = null
        _lectureTitle.value = "Notebook"

        lectureJob = viewModelScope.launch {
            launch { repository.markOpened(id) }
            launch {
                _currentPage.debounce(PAGE_SAVE_DEBOUNCE_MS).collect { page ->
                    if (!restoring && restoreChecked) repository.setLastPage(id, page)
                }
            }
            launch {
                repository.observeLecture(id).collect { lecture ->
                    _lectureTitle.value = lecture?.title ?: "Notebook"
                    _status.value = lecture?.readingStatus ?: ReadingStatus.TO_READ.name
                }
            }
            val loaded = repository.loadStrokes(id)
            _strokes.value = loaded.filter { it.surface == InkSurface.MARGIN }
                .map { RenderedStroke(it, it.toStroke()) }
            _pageStrokes.value = loaded.filter { it.surface == InkSurface.PAGE }
                .map { RenderedStroke(it, it.toStroke()) }
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
                    if (!restoreChecked) {
                        val saved = startPage ?: repository.getLecture(id)?.lastPage ?: 0
                        val target = saved.coerceAtMost((pdfPageCount - 1).coerceAtLeast(0))
                        if (latest != null && target > 0) {
                            restoring = true
                            requestPdfPage(target)
                        }
                        restoreChecked = true
                    }
                }
            }
        }
    }

    /** Persists the page now and forgets the lecture, so the next open starts fresh. */
    fun closeLecture() {
        val id = lectureId ?: return
        tracker.stop()
        inkIndexer.onNotebookClosed()
        if (!restoring && restoreChecked) {
            val page = firstVisiblePage
            viewModelScope.launch { repository.setLastPage(id, page) }
        }
        lectureId = null
    }

    fun setTool(tool: InkTool) {
        _tool.value = tool
    }

    fun selectPen(pen: Pen) {
        _selectedPen.value = pen
        _tool.value = InkTool.PEN
    }

    fun selectLasso() {
        _tool.value = InkTool.LASSO
    }

    fun selectHighlighter() {
        _tool.value = InkTool.HIGHLIGHTER
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
            bounds = stroke.inputs.bounds(),
            startedAt = now(),
            endedAt = now(),
            brushColor = stroke.brush.colorIntArgb.toLong() and 0xFFFFFFFFL,
            brushSizeDp = stroke.brush.size,
            batch = stroke.inputs,
            surface = InkSurface.MARGIN,
        )
        tracker.activity()
        _strokes.value = _strokes.value + RenderedStroke(record, stroke)
        pushOp(EditOp.Add(record))
        val saved = viewModelScope.launch { repository.saveStroke(record) }
        if (_tool.value == InkTool.PEN) refinePenStroke(record, saved)
    }

    fun onPageStrokeFinished(page: Int, width: Float, height: Float, stroke: Stroke) {
        val id = lectureId ?: return
        val record = InkStroke(
            id = newId(),
            lectureId = id,
            documentId = documentId,
            pdfPage = page,
            viewport = AnchorBox(0f, 0f, width, height),
            bounds = stroke.inputs.bounds(),
            startedAt = now(),
            endedAt = now(),
            brushColor = stroke.brush.colorIntArgb.toLong() and 0xFFFFFFFFL,
            brushSizeDp = stroke.brush.size,
            batch = stroke.inputs,
            surface = InkSurface.PAGE,
        )
        tracker.activity()
        _pageStrokes.value = _pageStrokes.value + RenderedStroke(record, stroke)
        pushOp(EditOp.Add(record))
        val saved = viewModelScope.launch { repository.saveStroke(record) }
        when (_tool.value) {
            InkTool.PEN -> refinePenStroke(record, saved)
            InkTool.HIGHLIGHTER -> snapHighlighter(record, width, height, saved)
            else -> Unit
        }
    }

    private fun surfaceList(surface: InkSurface) = if (surface == InkSurface.MARGIN) _strokes.value else _pageStrokes.value

    /**
     * After a pen stroke ends: hold-to-shape swaps it for the clean shape, a scratch
     * gesture deletes what it covers. All analysis runs off the main thread.
     */
    private fun refinePenStroke(record: InkStroke, saved: Job) {
        viewModelScope.launch {
            val verdict = withContext(Dispatchers.Default) {
                val points = record.batch.toPoints()
                ShapeSnap.snap(points)?.let { Refinement.Snap(it.toBatch(ShapeSnap.PRESSURE)) }
                    ?: ScratchOut.detect(points)?.let { Refinement.Scratch(it) }
            } ?: return@launch
            saved.join()
            when (verdict) {
                is Refinement.Snap -> applySnap(record, verdict.batch)
                is Refinement.Scratch -> applyScratch(record, verdict.area)
            }
        }
    }

    private sealed interface Refinement {
        class Snap(val batch: StrokeInputBatch) : Refinement
        class Scratch(val area: Extent) : Refinement
    }

    private suspend fun applySnap(record: InkStroke, batch: StrokeInputBatch) {
        val snapped = record.copy(batch = batch, bounds = batch.bounds())
        val present = surfaceList(record.surface).any { it.record.id == record.id }
        val queued = swapAddOp(record.id, snapped)
        if (!present && !queued) return
        if (present) {
            val rendered = withContext(Dispatchers.Default) { RenderedStroke(snapped, snapped.toStroke()) }
            updateSurface(record.surface) { list -> list.map { if (it.record.id == record.id) rendered else it } }
            repository.saveStroke(snapped)
        }
    }

    /** Points whichever stack still holds the Add op for [id] at its replacement record. */
    private fun swapAddOp(id: String, replacement: InkStroke): Boolean {
        for (stack in listOf(ops, redos)) {
            val at = stack.indexOfFirst { it is EditOp.Add && it.record.id == id }
            if (at >= 0) {
                stack[at] = EditOp.Add(replacement)
                return true
            }
        }
        return false
    }

    private suspend fun applyScratch(scratch: InkStroke, area: Extent) {
        val candidates = surfaceList(scratch.surface).filter {
            it.record.id != scratch.id && (scratch.surface == InkSurface.MARGIN || it.record.pdfPage == scratch.pdfPage)
        }
        val covered = withContext(Dispatchers.Default) {
            val reach = area.inflated(ScratchOut.INFLATE_PX)
            candidates.filter {
                val b = it.record.bounds
                reach.intersects(Extent(b.left, b.top, b.right, b.bottom)) &&
                    ScratchOut.covers(area, it.record.batch.toPoints())
            }.map { it.record }
        }
        val liveIds = surfaceList(scratch.surface).mapTo(HashSet()) { it.record.id }
        val victims = covered.filter { it.id in liveIds }
        if (victims.isEmpty() || scratch.id !in liveIds) return
        val gone = victims.mapTo(HashSet()) { it.id } + scratch.id
        updateSurface(scratch.surface) { list -> list.filterNot { it.record.id in gone } }
        ops.removeAll { it is EditOp.Add && it.record.id == scratch.id }
        pushOp(EditOp.Erase(victims, emptyList()))
        removeStrokes(gone)
    }

    /**
     * Replaces a freehand highlighter swipe with one straight band per text line it
     * sweeps; with no text underneath the swipe stays as drawn.
     */
    private fun snapHighlighter(record: InkStroke, width: Float, height: Float, saved: Job) {
        viewModelScope.launch {
            val path = _document.value?.localPath
            val b = record.bounds
            val pad = record.brushSizeDp * BAND_REACH
            val area = RectF(
                (b.left / width).coerceIn(0f, 1f),
                ((b.top - pad) / height).coerceIn(0f, 1f),
                (b.right / width).coerceIn(0f, 1f),
                ((b.bottom + pad) / height).coerceIn(0f, 1f),
            )
            val lines = if (path == null || width <= 0f || height <= 0f) {
                emptyList()
            } else {
                indexer.textLineRects(path, record.pdfPage, area)
            }
            val bands = withContext(Dispatchers.Default) {
                lines.mapNotNull { line ->
                    val top = line.top * height
                    val bottom = line.bottom * height
                    val overlap = minOf(bottom, b.bottom + pad) - maxOf(top, b.top - pad)
                    val left = maxOf(line.left * width, b.left)
                    val right = minOf(line.right * width, b.right)
                    if (overlap < 0.5f * (bottom - top) || right - left < MIN_BAND_PX) {
                        null
                    } else {
                        bandRecord(record, left, right, (top + bottom) / 2f, bottom - top)
                    }
                }
            }
            saved.join()
            val stillThere = _pageStrokes.value.any { it.record.id == record.id }
            if (bands.isEmpty() || !stillThere) {
                if (stillThere) captureHighlight(record, area)
                return@launch
            }
            val rendered = withContext(Dispatchers.Default) { bands.map { RenderedStroke(it, it.toStroke()) } }
            _pageStrokes.value = _pageStrokes.value.filterNot { it.record.id == record.id } + rendered
            val at = ops.indexOfFirst { it is EditOp.Add && it.record.id == record.id }
            if (at >= 0) ops[at] = EditOp.AddGroup(bands)
            removeStrokes(listOf(record.id))
            repository.saveStrokes(bands)
            val union = RectF(
                (bands.minOf { it.bounds.left } - bands.first().brushSizeDp * 0.25f) / width,
                bands.minOf { it.bounds.top - it.brushSizeDp / 2f } / height,
                (bands.maxOf { it.bounds.right } + bands.first().brushSizeDp * 0.25f) / width,
                bands.maxOf { it.bounds.bottom + it.brushSizeDp / 2f } / height,
            )
            captureHighlight(bands.first(), union)
        }
    }

    private fun bandRecord(base: InkStroke, left: Float, right: Float, y: Float, lineHeight: Float): InkStroke {
        val steps = ((right - left) / BAND_STEP_PX).toInt().coerceAtLeast(1)
        val points = List(steps + 1) { InkPt(left + (right - left) * it / steps, y, 10L * it) }
        val batch = points.toBatch(1f)
        return base.copy(
            id = newId(),
            batch = batch,
            bounds = batch.bounds(),
            brushSizeDp = lineHeight * BAND_THICKNESS,
        )
    }

    // Highlights ride on their stroke: pulled out when it is erased or undone, put back on redo.
    private val parkedHighlights = HashMap<String, HighlightEntity>()
    private val highlightLock = Mutex()

    /** Reads the page text under [area] (page fractions) for a finished highlight; runs entirely off the ink path. */
    private fun captureHighlight(record: InkStroke, area: RectF) {
        val path = _document.value?.localPath ?: return
        viewModelScope.launch {
            val text = indexer.textIn(path, record.pdfPage, area).replace(Regex("\\s+"), " ").trim()
            if (text.isEmpty()) return@launch
            val highlight = HighlightEntity(
                id = newId(),
                lectureId = record.lectureId,
                documentId = record.documentId,
                page = record.pdfPage,
                text = text,
                color = record.brushColor,
                strokeId = record.id,
                createdAt = now(),
            )
            highlightLock.withLock {
                if (_pageStrokes.value.any { it.record.id == record.id }) {
                    repository.saveHighlight(highlight)
                } else {
                    parkedHighlights[record.id] = highlight
                }
            }
        }
    }

    private suspend fun removeStrokes(ids: Collection<String>) = highlightLock.withLock {
        repository.highlightsForStrokes(ids.toList()).forEach { parkedHighlights[it.strokeId] = it }
        ids.forEach { repository.deleteStroke(it) }
    }

    private suspend fun restoreStrokes(records: List<InkStroke>) = highlightLock.withLock {
        repository.saveStrokes(records)
        repository.saveHighlights(records.mapNotNull { parkedHighlights.remove(it.id) })
    }

    fun eraseAt(x: Float, y: Float) {
        eraseSurface(InkSurface.MARGIN, null, x, y)
    }

    fun erasePageAt(page: Int, x: Float, y: Float) {
        eraseSurface(InkSurface.PAGE, page, x, y)
    }

    private fun eraseSurface(surface: InkSurface, page: Int?, x: Float, y: Float) {
        val radius = _eraserSize.value.radiusPx
        val current = if (surface == InkSurface.MARGIN) _strokes.value else _pageStrokes.value
        val removed = ArrayList<InkStroke>()
        val added = ArrayList<InkStroke>()
        val next = ArrayList<RenderedStroke>(current.size)

        for (item in current) {
            if (page != null && item.record.pdfPage != page) {
                next.add(item)
                continue
            }
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

        if (surface == InkSurface.MARGIN) _strokes.value = next else _pageStrokes.value = next
        pushOp(EditOp.Erase(removed, added))
        viewModelScope.launch {
            removeStrokes(removed.map { it.id })
            repository.saveStrokes(added)
        }
    }

    fun undo() {
        val op = ops.removeLastOrNull() ?: return
        when (op) {
            is EditOp.Add -> {
                updateSurface(op.record.surface) { it.filterNot { stroke -> stroke.record.id == op.record.id } }
                viewModelScope.launch { removeStrokes(listOf(op.record.id)) }
            }

            is EditOp.AddGroup -> {
                val ids = op.records.mapTo(HashSet()) { it.id }
                updateSurface(op.records.first().surface) { it.filterNot { stroke -> stroke.record.id in ids } }
                viewModelScope.launch { removeStrokes(ids) }
            }

            is EditOp.Transform -> {
                applyRecords(op.before)
                _selection.value = null
            }

            is EditOp.Erase -> {
                val addedIds = op.added.map { it.id }.toSet()
                val restored = op.removed.map { RenderedStroke(it, it.toStroke()) }
                val surface = op.removed.firstOrNull()?.surface ?: InkSurface.MARGIN
                updateSurface(surface) { it.filterNot { stroke -> stroke.record.id in addedIds } + restored }
                viewModelScope.launch {
                    removeStrokes(addedIds)
                    restoreStrokes(op.removed)
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
                updateSurface(op.record.surface) { it + RenderedStroke(op.record, op.record.toStroke()) }
                viewModelScope.launch { restoreStrokes(listOf(op.record)) }
            }

            is EditOp.AddGroup -> {
                updateSurface(op.records.first().surface) { list -> list + op.records.map { RenderedStroke(it, it.toStroke()) } }
                viewModelScope.launch { restoreStrokes(op.records) }
            }

            is EditOp.Transform -> {
                applyRecords(op.after)
                _selection.value = null
            }

            is EditOp.Erase -> {
                val removedIds = op.removed.map { it.id }.toSet()
                val pieces = op.added.map { RenderedStroke(it, it.toStroke()) }
                val surface = op.removed.firstOrNull()?.surface ?: InkSurface.MARGIN
                updateSurface(surface) { it.filterNot { stroke -> stroke.record.id in removedIds } + pieces }
                viewModelScope.launch {
                    removeStrokes(removedIds)
                    restoreStrokes(op.added)
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

    /** Swaps stored strokes for edited versions of themselves, matched by id, and persists them. */
    private fun applyRecords(records: List<InkStroke>) {
        val byId = records.associateBy { it.id }
        updateSurface(records.first().surface) { list ->
            list.map { item -> byId[item.record.id]?.let { RenderedStroke(it, it.toStroke()) } ?: item }
        }
        viewModelScope.launch { repository.saveStrokes(records) }
    }

    /** Picks the strokes on one surface (a page, or the margin when [page] is null) mostly inside [polygon]. */
    fun selectInside(surface: InkSurface, page: Int?, polygon: List<Pair<Float, Float>>) {
        val pool = surfaceList(surface).filter { page == null || it.record.pdfPage == page }
        viewModelScope.launch {
            val picked = withContext(Dispatchers.Default) {
                if (polygon.size < 3) return@withContext emptyList()
                val reach = Extent(polygon.minOf { it.first }, polygon.minOf { it.second }, polygon.maxOf { it.first }, polygon.maxOf { it.second })
                pool.filter {
                    val b = it.record.bounds
                    reach.intersects(Extent(b.left, b.top, b.right, b.bottom)) &&
                        fractionInsidePolygon(it.record.batch.toPoints(), polygon) >= SELECT_COVERAGE
                }
            }
            _selection.value = if (picked.isEmpty()) {
                null
            } else {
                withContext(Dispatchers.Default) { SelectionState.of(surface, page, picked) }
            }
        }
    }

    fun clearSelection() {
        _selection.value = null
    }

    /** One undoable edit for a whole move, scale or recolour of the selection. */
    private fun editSelection(change: (InkStroke) -> InkStroke) {
        val sel = _selection.value ?: return
        viewModelScope.launch {
            val before = sel.items.map { it.record }
            val (after, rendered) = withContext(Dispatchers.Default) {
                val edited = before.map(change)
                edited to edited.map { RenderedStroke(it, it.toStroke()) }
            }
            val byId = rendered.associateBy { it.record.id }
            updateSurface(sel.surface) { list -> list.map { byId[it.record.id] ?: it } }
            pushOp(EditOp.Transform(before, after))
            repository.saveStrokes(after)
            _selection.value = withContext(Dispatchers.Default) { SelectionState.of(sel.surface, sel.page, rendered) }
        }
    }

    fun transformSelection(transform: StrokeTransform) = editSelection { it.transformed(transform) }

    fun recolorSelection(rgb: Int) = editSelection { it.recolored(rgb) }

    fun duplicateSelection() {
        val sel = _selection.value ?: return
        viewModelScope.launch {
            val (copies, rendered) = withContext(Dispatchers.Default) {
                val moved = StrokeTransform.move(DUPLICATE_OFFSET_PX, DUPLICATE_OFFSET_PX)
                val made = sel.items.map { it.record.transformed(moved).copy(id = newId(), startedAt = now(), endedAt = now()) }
                made to made.map { RenderedStroke(it, it.toStroke()) }
            }
            updateSurface(sel.surface) { it + rendered }
            pushOp(EditOp.AddGroup(copies))
            repository.saveStrokes(copies)
            _selection.value = withContext(Dispatchers.Default) { SelectionState.of(sel.surface, sel.page, rendered) }
        }
    }

    fun deleteSelection() {
        val sel = _selection.value ?: return
        _selection.value = null
        val ids = sel.ids
        updateSurface(sel.surface) { list -> list.filterNot { it.record.id in ids } }
        pushOp(EditOp.Erase(sel.items.map { it.record }, emptyList()))
        viewModelScope.launch { removeStrokes(ids) }
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

    private fun updateSurface(
        surface: InkSurface,
        transform: (List<RenderedStroke>) -> List<RenderedStroke>,
    ) {
        if (surface == InkSurface.MARGIN) {
            _strokes.value = transform(_strokes.value)
        } else {
            _pageStrokes.value = transform(_pageStrokes.value)
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
        tracker.activity()
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
        // A saved page the list cannot scroll to the top (the last page) never matches; touch ends the restore.
        restoring = false
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
        tracker.activity()
        pdfPos = pos
        firstVisiblePage = pos.toInt()
        _currentPage.value = firstVisiblePage
        // While our own scroll request is in flight, every report is an echo.
        if (_pdfScrollTarget.value != null) return
        val expected = expectedPdfPos
        if (expected != null && kotlin.math.abs(pos - expected) < POS_EPSILON) {
            expectedPdfPos = null
            restoring = false
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

    private fun newId(): String = UUID.randomUUID().toString()

    private fun now(): Long = System.currentTimeMillis()

    private companion object {
        const val HIGHLIGHT_COLOR: Int = 0xFF3557A6.toInt()
        const val PDF_DRIVE_WINDOW_MS = 2000L
        const val POS_EPSILON = 0.05f
        const val CANVAS_ANIM_MS = 250f
        const val PAGE_SAVE_DEBOUNCE_MS = 1000L
        const val ERASER_SIZE_KEY = "eraser_size"
        const val BAND_REACH = 0.3f
        const val BAND_THICKNESS = 1.1f
        const val BAND_STEP_PX = 8f
        const val MIN_BAND_PX = 4f
        const val SELECT_COVERAGE = 0.6f
        const val DUPLICATE_OFFSET_PX = 16f
    }
}
