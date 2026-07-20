package com.serendeep.marginalia.notebook

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serendeep.marginalia.ink.InkCanvas
import com.serendeep.marginalia.ink.InkTool
import com.serendeep.marginalia.ink.Pens
import com.serendeep.marginalia.pdf.PageAnchor
import com.serendeep.marginalia.pdf.PdfDocumentSource
import com.serendeep.marginalia.pdf.PdfPane

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(viewModel: NotebookViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var source by remember { mutableStateOf<PdfDocumentSource?>(null) }
    var pendingWebLink by remember { mutableStateOf<String?>(null) }
    var showOutline by remember { mutableStateOf(false) }
    var outlineTarget by remember { mutableStateOf<Int?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        source?.close()
        val opened = context.contentResolver.openFileDescriptor(uri, "r")?.let {
            PdfDocumentSource.open(context, it)
        }
        source = opened
        if (opened != null) {
            viewModel.onDocumentOpened(displayName(context, uri), opened.pageCount)
        }
    }

    DisposableEffect(Unit) {
        onDispose { source?.close() }
    }

    val strokes by viewModel.strokes.collectAsStateWithLifecycle()
    val tool by viewModel.tool.collectAsStateWithLifecycle()
    val anchors by viewModel.anchors.collectAsStateWithLifecycle()
    val activeAnchor by viewModel.activeAnchor.collectAsStateWithLifecycle()
    val strokeList = remember(strokes) { strokes.map { it.stroke } }
    val pageAnchors = remember(anchors) {
        anchors.map { PageAnchor(it.id, it.pdfPage, it.pageXFraction, it.pageYFraction, it.label) }
    }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(top = 12.dp)) {
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { picker.launch(arrayOf("application/pdf")) }) {
                    Text("Open PDF")
                }
                val outline = source?.outline().orEmpty()
                if (outline.isNotEmpty()) {
                    TextButton(onClick = { showOutline = true }) { Text("Outline") }
                }
                if (source != null) {
                    Text(
                        "Hold a spot on the page to link it to your notes",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            val current = source
            if (current == null) {
                CenteredHint("Pick a lecture PDF")
            } else {
                PdfPane(
                    source = current,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    anchors = pageAnchors,
                    onPageLongPress = viewModel::placeAnchor,
                    onAnchorTap = viewModel::flashAnchor,
                    onAnchorRemove = viewModel::removeAnchor,
                    onWebLink = { pendingWebLink = it },
                    scrollToPage = outlineTarget,
                    onScrollHandled = { outlineTarget = null },
                )
            }
        }

        pendingWebLink?.let { url ->
            AlertDialog(
                onDismissRequest = { pendingWebLink = null },
                title = { Text("Open link?") },
                text = { Text(Uri.parse(url).host ?: url) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingWebLink = null
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    }) { Text("Open") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingWebLink = null }) { Text("Cancel") }
                },
            )
        }

        if (showOutline) {
            val outline = source?.outline().orEmpty()
            ModalBottomSheet(onDismissRequest = { showOutline = false }) {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(outline) { node ->
                        Text(
                            text = node.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showOutline = false
                                    outlineTarget = node.pageIndex
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

        VerticalDivider()

        Box(Modifier.weight(1f).fillMaxHeight()) {
            InkCanvas(
                strokes = strokeList,
                tool = tool,
                penColor = Pens.DEFAULT_COLOR,
                penSizePx = Pens.DEFAULT_SIZE_PX,
                onStrokeFinished = viewModel::onStrokeFinished,
                onErase = viewModel::eraseAt,
                modifier = Modifier.fillMaxSize(),
            )

            Row(
                Modifier.align(Alignment.TopEnd).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = tool == InkTool.PEN,
                    onClick = { viewModel.setTool(InkTool.PEN) },
                    label = { Text("Pen") },
                )
                FilterChip(
                    selected = tool == InkTool.ERASER,
                    onClick = { viewModel.setTool(InkTool.ERASER) },
                    label = { Text("Eraser") },
                )
                TextButton(onClick = viewModel::undo) { Text("Undo") }
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

private fun displayName(context: android.content.Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
    }
    return uri.lastPathSegment ?: "document.pdf"
}
