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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serendeep.marginalia.sharedCover
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.components.GlassDialog
import com.serendeep.marginalia.ui.components.GlassTextButton
import com.serendeep.marginalia.ui.components.MarginLabel
import com.serendeep.marginalia.ink.InkCanvas
import com.serendeep.marginalia.ink.InkTool
import com.serendeep.marginalia.ink.Pen
import com.serendeep.marginalia.ink.Pens
import com.serendeep.marginalia.pdf.PageAnchor
import com.serendeep.marginalia.pdf.PdfDocumentSource
import com.serendeep.marginalia.pdf.PdfPane
import com.serendeep.marginalia.ui.theme.DotGridDark
import com.serendeep.marginalia.ui.theme.DotGridLight
import com.serendeep.marginalia.ui.theme.GlassBorderDark
import com.serendeep.marginalia.ui.theme.GlassBorderLight
import com.serendeep.marginalia.ui.theme.GlassTintDark
import com.serendeep.marginalia.ui.theme.GlassTintLight
import com.serendeep.marginalia.ui.theme.InkLight
import com.serendeep.marginalia.ui.theme.LocalDarkTheme
import com.serendeep.marginalia.ui.theme.LocalPenPalette
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(
    viewModel: NotebookViewModel = hiltViewModel(),
    lectureId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var source by remember { mutableStateOf<PdfDocumentSource?>(null) }
    var pendingWebLink by remember { mutableStateOf<String?>(null) }
    val peekDetent = remember { SheetDetent("peek") { containerHeight, _ -> containerHeight * 0.5f } }
    val outlineSheet = rememberModalBottomSheetState(
        initialDetent = Hidden,
        detents = listOf(Hidden, peekDetent, FullyExpanded),
    )

    LaunchedEffect(lectureId) { viewModel.openLecture(lectureId) }

    val document by viewModel.document.collectAsStateWithLifecycle()
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
    val tool by viewModel.tool.collectAsStateWithLifecycle()
    val selectedPen by viewModel.selectedPen.collectAsStateWithLifecycle()
    val penDown by viewModel.penDown.collectAsStateWithLifecycle()
    val anchors by viewModel.anchors.collectAsStateWithLifecycle()
    val activeAnchor by viewModel.activeAnchor.collectAsStateWithLifecycle()
    val canvasOffset by viewModel.canvasOffset.collectAsStateWithLifecycle()
    val pdfSyncTarget by viewModel.pdfScrollTarget.collectAsStateWithLifecycle()
    val strokeList = remember(strokes) { strokes.map { it.stroke } }
    val pageAnchors = remember(anchors) {
        anchors.map { PageAnchor(it.id, it.pdfPage, it.pageXFraction, it.pageYFraction, it.label) }
    }

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        val pdfHaze = remember { HazeState() }
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
            val current = source
            Box(Modifier.matchParentSize().hazeSource(pdfHaze)) {
                if (current == null) {
                    CenteredHint("Import a PDF from the library")
                } else {
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
                    )
                }
            }
            DocumentBar(
                title = document?.fileName?.removeSuffix(".pdf") ?: "Notebook",
                hasOutline = current?.outline().orEmpty().isNotEmpty(),
                onBack = onBack,
                onOutline = { outlineSheet.currentDetent = peekDetent },
                hazeState = pdfHaze,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
        }

        pendingWebLink?.let { url ->
            GlassDialog(onDismiss = { pendingWebLink = null }) {
                Text("Open link?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(Uri.parse(url).host ?: url, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    GlassTextButton("Cancel", onClick = { pendingWebLink = null })
                    Spacer(Modifier.width(8.dp))
                    GlassButton("Open", onClick = {
                        pendingWebLink = null
                        val parsed = Uri.parse(url)
                        // Only ever hand http(s) to the system; PDFs can carry hostile schemes.
                        if (parsed.scheme == "http" || parsed.scheme == "https") {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, parsed)) }
                        }
                    })
                }
            }
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

        VerticalDivider()

        val dotColor = if (LocalDarkTheme.current) DotGridDark else DotGridLight
        val hazeState = remember { HazeState() }
        Box(Modifier.weight(1f).fillMaxHeight()) {
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
                val penPalette = LocalPenPalette.current
                val penColor = when (selectedPen) {
                    Pen.GRAPHITE -> penPalette.graphite
                    Pen.INDIGO -> penPalette.indigo
                    Pen.RUST -> penPalette.rust
                }
                InkCanvas(
                    strokes = strokeList,
                    tool = tool,
                    penColor = penColor.toArgb(),
                    penSizePx = Pens.DEFAULT_SIZE_PX,
                    canvasOffset = canvasOffset,
                    onStrokeFinished = viewModel::onStrokeFinished,
                    onErase = viewModel::eraseAt,
                    onScrollBy = viewModel::onCanvasScrolledBy,
                    modifier = Modifier.fillMaxSize(),
                    onPenActive = viewModel::setPenActive,
                )
            }

            val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
            val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
            ToolRail(
                tool = tool,
                selectedPen = selectedPen,
                penDown = penDown,
                canUndo = canUndo,
                canRedo = canRedo,
                onSelectPen = viewModel::selectPen,
                onEraser = { viewModel.setTool(InkTool.ERASER) },
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            )

            activeAnchor?.let { anchor ->
                AssistChip(
                    onClick = viewModel::finishAnchorBinding,
                    label = { Text("Linking #${anchor.label} — writing binds to it · tap when done") },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
            }
        }
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
                    tint = HazeTint(if (dark) GlassTintDark else GlassTintLight),
                    blurRadius = 24.dp,
                    noiseFactor = 0.02f,
                ),
            ) {
                inputScale = HazeInputScale.Fixed(0.5f)
            }
            .border(1.dp, if (dark) GlassBorderDark else GlassBorderLight, shape)
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
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
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
