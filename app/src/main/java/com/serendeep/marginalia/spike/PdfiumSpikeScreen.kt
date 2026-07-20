package com.serendeep.marginalia.spike

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pick a PDF and show its render times, page sizes, and extracted text. */
@Composable
fun PdfiumSpikeScreen() {
    val context = LocalContext.current
    var report by remember { mutableStateOf<SpikeReport?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        running = true
        report = null
        preview = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { analyze(context, uri) }
            report = result.first
            preview = result.second
            running = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Marginalia · pdfium spike", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Pick a real lecture PDF. Try a scanned one, a rotated one, and a 300+ page one.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = { picker.launch(arrayOf("application/pdf")) }, enabled = !running) {
            Text("Pick a lecture PDF")
        }

        if (running) {
            CircularProgressIndicator()
        }

        report?.let { ReportCard(it) }

        preview?.let { bmp ->
            Text("Page 1 render", style = MaterialTheme.typography.titleMedium)
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "First page rendered by pdfium",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ReportCard(r: SpikeReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(r.fileName, style = MaterialTheme.typography.titleMedium)
            if (r.error != null) {
                Text("ERROR: ${r.error}", color = MaterialTheme.colorScheme.error)
                return@Column
            }
            Metric("Size", "${r.sizeBytes / 1024} KB")
            Metric("Pages", "${r.pageCount}")
            Metric("Open time", "${r.openMs} ms")
            Metric("Avg render", "%.1f ms".format(r.avgRenderMs))
            Metric("Max render", "${r.maxRenderMs} ms")
            Metric("Avg text extract", "%.1f ms".format(r.avgTextMs))
            Metric(
                "Text layer",
                if (r.hasTextLayer) "YES (searchable)" else "NO (scanned / image-only)",
            )
            Spacer(Modifier.height(8.dp))
            Text("Sampled pages", style = MaterialTheme.typography.titleSmall)
            r.sampledPages.forEach { p -> PageRow(p) }
        }
    }
}

@Composable
private fun PageRow(p: PageResult) {
    Column(Modifier.padding(vertical = 4.dp)) {
        if (p.error != null) {
            Text("p${p.index}: ERROR ${p.error}", color = MaterialTheme.colorScheme.error)
            return
        }
        Text(
            "p${p.index}  ${p.widthPt}x${p.heightPt}pt  ->  ${p.bitmapW}x${p.bitmapH}px  " +
                "| render ${p.renderMs}ms  | ${p.charCount} chars ${p.textMs}ms",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
        if (p.textSample.isNotEmpty()) {
            Text(
                "\"${p.textSample}\"",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Light,
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Text(
        buildLabel(label, value),
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodyMedium,
    )
}

private fun buildLabel(label: String, value: String): String =
    label.padEnd(18) + value

// Open the file twice so the report and the preview each get their own descriptor.
private fun analyze(context: Context, uri: Uri): Pair<SpikeReport, Bitmap?> {
    val cr = context.contentResolver
    val name = queryName(context, uri)
    val size = querySize(context, uri)

    val report = cr.openFileDescriptor(uri, "r")?.use { pfd ->
        PdfiumSpike.run(context, pfd, name, size)
    } ?: SpikeReport(name, size, 0, 0, emptyList(), error = "could not open file descriptor")

    val preview = cr.openFileDescriptor(uri, "r")?.use { pfd ->
        PdfiumSpike.renderPage(context, pfd, 0)
    }
    return report to preview
}

private fun queryName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
    }
    return uri.lastPathSegment ?: "unknown.pdf"
}

private fun querySize(context: Context, uri: Uri): Long {
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.SIZE)
        if (idx >= 0 && c.moveToFirst()) return c.getLong(idx)
    }
    return -1
}
