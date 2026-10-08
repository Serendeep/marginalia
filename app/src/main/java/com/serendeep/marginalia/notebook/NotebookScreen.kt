package com.serendeep.marginalia.notebook

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.composables.core.DragIndication
import com.composables.core.ModalBottomSheet
import com.composables.core.Scrim
import com.composables.core.Sheet
import com.composables.core.SheetDetent
import com.composables.core.SheetDetent.Companion.FullyExpanded
import com.composables.core.SheetDetent.Companion.Hidden
import com.composables.core.rememberModalBottomSheetState
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.serendeep.marginalia.study.WORK_SECONDS
import com.serendeep.marginalia.ui.theme.Lime
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serendeep.marginalia.sharedCover
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.components.GlassDialog
import com.serendeep.marginalia.ui.components.WebPopup
import com.serendeep.marginalia.ui.components.GlassTextButton
import com.serendeep.marginalia.ui.components.MarginLabel
import com.serendeep.marginalia.ui.components.glassBorder
import com.serendeep.marginalia.cards.CardEditorSheet
import com.serendeep.marginalia.handwriting.HandwritingTextSheet
import com.serendeep.marginalia.data.InkSurface
import com.serendeep.marginalia.ink.InkCanvas
import com.serendeep.marginalia.ink.PenColors
import com.serendeep.marginalia.ink.InkTool
import com.serendeep.marginalia.ink.Pen
import com.serendeep.marginalia.ink.Pens
import com.serendeep.marginalia.pdf.PageAnchor
import com.serendeep.marginalia.pdf.PdfDocumentSource
import com.serendeep.marginalia.pdf.PdfLassoRegion
import com.serendeep.marginalia.pdf.PdfPane
import com.serendeep.marginalia.ui.theme.DotGridDark
import com.serendeep.marginalia.ui.theme.DotGridLight
import com.serendeep.marginalia.ui.theme.GlassSmokeDark
import com.serendeep.marginalia.ui.theme.GlassTintDark
import com.serendeep.marginalia.ui.theme.GlassTintLight
import com.serendeep.marginalia.ui.theme.InkLight
import com.serendeep.marginalia.ui.theme.LocalDarkTheme
import com.serendeep.marginalia.ui.theme.LocalPenPalette
import com.serendeep.marginalia.ui.theme.MonoFamily
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(
    viewModel: NotebookViewModel = hiltViewModel(),
    lectureId: String,
    onBack: () -> Unit,
    startPage: Int? = null,
) {
    val context = LocalContext.current
    var source by remember { mutableStateOf<PdfDocumentSource?>(null) }
    var pendingWebLink by remember { mutableStateOf<String?>(null) }
    val peekDetent = remember { SheetDetent("peek") { containerHeight, _ -> containerHeight * 0.5f } }
    val outlineSheet = rememberModalBottomSheetState(
        initialDetent = Hidden,
        detents = listOf(Hidden, peekDetent, FullyExpanded),
    )

    LaunchedEffect(lectureId) { viewModel.openLecture(lectureId, startPage) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onForeground()
                Lifecycle.Event.ON_STOP -> viewModel.onBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.closeLecture()
        }
    }

    val document by viewModel.document.collectAsStateWithLifecycle()
    val eraserSize by viewModel.eraserSize.collectAsStateWithLifecycle()
    val lectureTitle by viewModel.lectureTitle.collectAsStateWithLifecycle()
    LaunchedEffect(document) {
        source?.close()
        // Parsing a PDF is heavy native work; it must never block the frame
        // that is animating this screen in.
        source = document?.let { doc ->
            withContext(Dispatchers.IO) {
                runCatching { PdfDocumentSource.open(context, File(doc.localPath)) }.getOrNull()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { source?.close() }
    }

    val strokes by viewModel.strokes.collectAsStateWithLifecycle()
    val pageStrokes by viewModel.pageStrokes.collectAsStateWithLifecycle()
    val tool by viewModel.tool.collectAsStateWithLifecycle()
    val selectedPen by viewModel.selectedPen.collectAsStateWithLifecycle()
    val penDown by viewModel.penDown.collectAsStateWithLifecycle()
    val anchors by viewModel.anchors.collectAsStateWithLifecycle()
    val activeAnchor by viewModel.activeAnchor.collectAsStateWithLifecycle()
    val canvasOffset by viewModel.canvasOffset.collectAsStateWithLifecycle()
    val pdfSyncTarget by viewModel.pdfScrollTarget.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val pageCount by viewModel.pageCount.collectAsStateWithLifecycle()
    val penWidth by viewModel.penWidth.collectAsStateWithLifecycle()
    val swatches by viewModel.swatches.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    // Picked-up ink is drawn by the selection layer, so the sheet underneath skips it.
    val strokeList = remember(strokes, selection) {
        val held = selection?.takeIf { it.surface == InkSurface.MARGIN }?.ids.orEmpty()
        strokes.filterNot { it.record.id in held }.map { it.stroke }
    }
    val pageStrokeMap = remember(pageStrokes, selection) {
        val held = selection?.takeIf { it.surface == InkSurface.PAGE }?.ids.orEmpty()
        pageStrokes.filterNot { it.record.id in held }
            .groupBy { it.record.pdfPage }.mapValues { (_, items) -> items.map { it.stroke } }
    }
    val palette = LocalPenPalette.current
    val themed = remember(palette) { intArrayOf(palette.graphite.toArgb(), palette.indigo.toArgb(), palette.rust.toArgb()) }
    val choiceColors = remember(themed) { List(PenColors.choiceCount) { PenColors.resolve(it, themed) } }
    val swatchArgb = remember(swatches, themed) { swatches.map { PenColors.resolve(it, themed) } }
    val trails = remember { LaserTrails() }
    val selectionActions = remember(viewModel) {
        SelectionActions(
            onLasso = { page, polygon ->
                viewModel.selectInside(if (page == null) InkSurface.MARGIN else InkSurface.PAGE, page, polygon)
            },
            onTransform = viewModel::transformSelection,
            onRecolor = viewModel::recolorSelection,
            onDuplicate = viewModel::duplicateSelection,
            onDelete = viewModel::deleteSelection,
            onClear = viewModel::clearSelection,
        )
    }
    val pageAnchors = remember(anchors) {
        anchors.map { PageAnchor(it.id, it.pdfPage, it.pageXFraction, it.pageYFraction, it.label) }
    }

    val current = source
    val sheetColor = MaterialTheme.colorScheme.surface.toArgb()
    val lassoDraft by viewModel.lassoDraft.collectAsStateWithLifecycle()
    lassoDraft?.let { draft ->
        CardEditorSheet(
            imageLoader = viewModel.imageLoader,
            frontImagePath = draft.imagePath,
            onDismiss = viewModel::cancelLassoCard,
            initialBack = draft.back,
            onSave = { _, back, _ -> viewModel.saveLassoCard(back) },
        )
    }
    val textDraft by viewModel.textDraft.collectAsStateWithLifecycle()
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()
    textDraft?.let { draft ->
        HandwritingTextSheet(
            text = draft.text,
            modelState = modelState,
            onCard = viewModel::cardFromText,
            onDismiss = viewModel::dismissText,
        )
    }
    val pdfHaze = remember { HazeState() }
    Box(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        if (current != null) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .sharedCover("pdf-$lectureId")
                // A real touch on this pane is what makes the PDF the sync driver;
                // observe only, never consume.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        viewModel.onPdfTouched()
                        while (awaitPointerEvent().changes.any { it.pressed }) {
                            // Wait out the gesture.
                        }
                    }
                },
        ) {
            Box(Modifier.matchParentSize().hazeSource(pdfHaze)) {
                PdfPane(
                    source = current,
                    modifier = Modifier.fillMaxSize(),
                    anchors = pageAnchors,
                    onPageLongPress = viewModel::placeAnchor,
                    onAnchorTap = viewModel::flashAnchor,
                    onAnchorRemove = viewModel::removeAnchor,
                    onWebLink = { pendingWebLink = it },
                    scrollToPos = pdfSyncTarget,
                    onScrollHandled = viewModel::onPdfScrollHandled,
                    onScrollPos = viewModel::onPdfScrollPos,
                    pageStrokes = pageStrokeMap,
                    inkTool = tool,
                    inkColor = swatchArgb[selectedPen.ordinal],
                    inkSizePx = penWidth.px,
                    eraserRadiusPx = eraserSize.radiusPx,
                    onPageStrokeFinished = viewModel::onPageStrokeFinished,
                    onPageErase = viewModel::erasePageAt,
                    onLaser = trails::touch,
                    selection = selection,
                    selectionActions = selectionActions,
                    selectionColors = choiceColors,
                    onLasso = { region -> viewModel.stageLassoCard(region.page) { current.renderRegion(region) } },
                    onText = { region -> viewModel.convertSelection(region.page) { current.renderRegion(region) } },
                )
            }
            DocumentBar(
                title = document?.fileName?.removeSuffix(".pdf") ?: lectureTitle,
                hasOutline = current?.outline().orEmpty().isNotEmpty(),
                onBack = onBack,
                onOutline = { outlineSheet.currentDetent = peekDetent },
                hazeState = pdfHaze,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
            Column(
                Modifier.align(Alignment.BottomCenter).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MarkDoneChip(viewModel)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (pageCount > 0) {
                        PageIndicator(page = currentPage + 1, pageCount = pageCount)
                    }
                    FocusPill(viewModel)
                }
            }
        }

        pendingWebLink?.let { url ->
            WebPopup(url = url, onDismiss = { pendingWebLink = null })
        }

        // Outline sheet: opens at half height for a glance, drags to full for
        // long documents; a detent-aware sheet, not the stock two-state one.
        ModalBottomSheet(state = outlineSheet) {
            Scrim()
            Sheet(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    ),
            ) {
                val outline = source?.outline().orEmpty()
                Column(Modifier.fillMaxWidth()) {
                    DragIndication(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 12.dp, bottom = 8.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(100),
                            )
                            .width(36.dp)
                            .height(4.dp),
                    )
                    MarginLabel("Outline", Modifier.padding(start = 24.dp, bottom = 6.dp))
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(outline) { node ->
                            Text(
                                text = node.title,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        outlineSheet.currentDetent = Hidden
                                        viewModel.requestPdfPage(node.pageIndex)
                                    }
                                    .padding(
                                        start = 24.dp + 20.dp * node.depth,
                                        end = 24.dp,
                                        top = 10.dp,
                                        bottom = 10.dp,
                                    ),
                            )
                        }
                    }
                }
            }
        }
        }

        if (current != null) VerticalDivider()

        val dotColor = if (LocalDarkTheme.current) DotGridDark else DotGridLight
        val hazeState = remember { HazeState() }
        Box(
            (if (current != null) Modifier.weight(1f) else Modifier.fillMaxWidth())
                .fillMaxHeight(),
        ) {
            if (current == null) {
                DocumentBar(
                    title = lectureTitle,
                    hasOutline = false,
                    onBack = onBack,
                    onOutline = {},
                    hazeState = hazeState,
                    // Keep the navigation bar above the full-size note canvas.
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .zIndex(1f),
                )
            }
            // The sheet is the rail's blur source, so it lives in its own node
            // beneath the rail rather than as the rail's parent.
            Box(
                Modifier
                    .matchParentSize()
                    .hazeSource(hazeState)
                    .background(MaterialTheme.colorScheme.surface)
                    .onSizeChanged { viewModel.onInkPaneHeight(it.height.toFloat()) }
                    .drawWithCache {
                        // Dot grid on the note sheet, 22dp pitch. Built once per size
                        // (one extra row so it can slide), drawn as a single path and
                        // shifted with the canvas scroll so the paper moves with the ink.
                        val step = 22.dp.toPx()
                        val radius = 1.dp.toPx()
                        val grid = Path()
                        var x = step
                        while (x < size.width) {
                            var y = 0f
                            while (y < size.height + step) {
                                grid.addOval(Rect(Offset(x, y), radius))
                                y += step
                            }
                            x += step
                        }
                        onDrawBehind {
                            translate(top = -(canvasOffset % step)) { drawPath(grid, dotColor) }
                        }
                    },
            ) {
                InkCanvas(
                    strokes = strokeList,
                    tool = tool,
                    penColor = swatchArgb[selectedPen.ordinal],
                    penSizePx = penWidth.px,
                    onLaser = trails::touch,
                    eraserRadiusPx = eraserSize.radiusPx,
                    canvasOffset = canvasOffset,
                    onStrokeFinished = viewModel::onStrokeFinished,
                    onErase = viewModel::eraseAt,
                    onScrollBy = viewModel::onCanvasScrolledBy,
                    modifier = Modifier.fillMaxSize(),
                    onPenActive = viewModel::setPenActive,
                )
                if (tool == InkTool.SELECT) {
                    SelectionLayer(
                        selection = selection?.takeIf { it.surface == InkSurface.MARGIN },
                        page = null,
                        offsetY = { canvasOffset },
                        colors = choiceColors,
                        actions = selectionActions,
                        onCard = { box ->
                            viewModel.stageLassoCard(null) {
                                renderMarginCrop(strokes, Rect(box.left, box.top, box.right, box.bottom), sheetColor)
                            }
                        },
                        onText = { box ->
                            viewModel.convertSelection(null) {
                                renderMarginCrop(strokes, Rect(box.left, box.top, box.right, box.bottom), sheetColor)
                            }
                        },
                    )
                }
                if (tool == InkTool.LASSO) {
                    LassoOverlay(Modifier.fillMaxSize()) { area ->
                        // The sheet scrolls; the crop is taken in note-canvas space.
                        val canvasArea = area.translate(0f, canvasOffset)
                        viewModel.stageLassoCard(null) { renderMarginCrop(strokes, canvasArea, sheetColor) }
                    }
                }
            }

            val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
            val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
            ToolRail(
                tool = tool,
                selectedPen = selectedPen,
                swatchColors = swatchArgb.map { Color(it) },
                choiceColors = choiceColors,
                penWidth = penWidth,
                onPenWidth = viewModel::setPenWidth,
                onSwatchChoice = viewModel::setSwatch,
                penDown = penDown,
                canUndo = canUndo,
                canRedo = canRedo,
                onSelectPen = viewModel::selectPen,
                onHighlighter = viewModel::selectHighlighter,
                onLaser = { viewModel.setTool(InkTool.LASER) },
                onEraser = { viewModel.setTool(InkTool.ERASER) },
                eraserSize = eraserSize,
                onEraserSize = viewModel::setEraserSize,
                onSelect = { viewModel.setTool(InkTool.SELECT) },
                onLasso = viewModel::selectLasso,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            )

            if (current == null) {
                FocusPill(viewModel, Modifier.align(Alignment.BottomStart).padding(12.dp))
            }

            activeAnchor?.let { anchor ->
                AssistChip(
                    onClick = viewModel::finishAnchorBinding,
                    label = { Text("Linking #${anchor.label} — writing binds to it · tap when done") },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
            }
        }
    }
    LaserOverlay(trails)
    }
}

/** Floating glass strip over the PDF: back, document title, outline. */
@Composable
private fun DocumentBar(
    title: String,
    hasOutline: Boolean,
    onBack: () -> Unit,
    onOutline: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val dark = LocalDarkTheme.current
    val iconColor = if (dark) Color(0xFFE8EAEE) else InkLight
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    // The bar floats over the PDF page, which is white in every
                    // theme; clear glass would wash light text out there.
                    tint = HazeTint(if (dark) GlassSmokeDark else GlassTintLight),
                    blurRadius = 24.dp,
                    noiseFactor = 0.02f,
                ),
            ) {
                inputScale = HazeInputScale.Fixed(0.5f)
            }
            .border(1.dp, glassBorder(), shape)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBackIos,
                contentDescription = "Back to library",
                tint = iconColor,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            title.uppercase(Locale.ROOT),
            fontFamily = MonoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
            color = iconColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 300.dp).padding(horizontal = 4.dp),
        )
        if (hasOutline) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onOutline),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.FormatListBulleted,
                    contentDescription = "Outline",
                    tint = iconColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** Small glass pill showing the PDF page under the viewport. */
@Composable
private fun PageIndicator(page: Int, pageCount: Int, modifier: Modifier = Modifier) {
    val dark = LocalDarkTheme.current
    val iconColor = if (dark) Color(0xFFE8EAEE) else InkLight
    val shape = RoundedCornerShape(14.dp)
    Text(
        "PG %02d/%02d".format(Locale.ROOT, page, pageCount),
        fontFamily = MonoFamily,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        color = iconColor,
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .border(1.dp, glassBorder(), shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Offers to finish a document once its last page is reached. */
@Composable
private fun MarkDoneChip(viewModel: NotebookViewModel) {
    val show by viewModel.showMarkDone.collectAsStateWithLifecycle()
    if (!show) return
    AssistChip(
        onClick = viewModel::markDone,
        label = { Text("Mark as done?") },
    )
}

/** Running focus timer; tap to pause or resume. Reads its own state so only it recomposes each second. */
@Composable
private fun FocusPill(viewModel: NotebookViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.focus.collectAsStateWithLifecycle()
    if (!state.running && state.remainingSec == WORK_SECONDS && !state.onBreak) return
    val shape = RoundedCornerShape(14.dp)
    val tint = if (state.running) Lime else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        "%02d:%02d".format(Locale.ROOT, state.remainingSec / 60, state.remainingSec % 60),
        fontFamily = MonoFamily,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        color = tint,
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .border(1.dp, glassBorder(), shape)
            .clickable(onClick = viewModel::toggleFocus)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun CenteredHint(text: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

private suspend fun PdfDocumentSource.renderRegion(r: PdfLassoRegion) = renderRegion(
    r.page, r.scaledPageWidthPx, r.scaledPageHeightPx, r.srcLeftPx, r.srcTopPx, r.outWidthPx, r.outHeightPx,
)
