package com.serendeep.marginalia.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect

/** Vertical scroll of a PDF's pages, each rendered to fit the pane width. */
@Composable
fun PdfPane(source: PdfDocumentSource, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        LazyColumn(Modifier.fillMaxSize()) {
            items((0 until source.pageCount).toList()) { index ->
                PdfPageItem(source, index, widthPx)
            }
        }
    }
}

@Composable
private fun PdfPageItem(source: PdfDocumentSource, index: Int, widthPx: Int) {
    var bitmap by remember(index, widthPx) { mutableStateOf<Bitmap?>(null) }
    var aspect by remember(index) { mutableStateOf(0.7f) }

    LaunchedEffect(index, widthPx) {
        if (widthPx <= 0) return@LaunchedEffect
        aspect = source.pageAspectRatio(index)
        bitmap = withContext(Dispatchers.Default) { source.renderFullPage(index, widthPx) }
    }

    // Height follows the page aspect (width / (w:h ratio)).
    val heightDp = with(LocalDensity.current) { (widthPx / aspect).toInt().toDp() }
    val current = bitmap
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .height(heightDp)
            .padding(vertical = 4.dp)
            .background(if (current == null) Color(0xFFEAEAEA) else Color.White),
    ) {
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "Page ${index + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillWidth,
            )
        }
    }
    // ponytail: bitmaps for scrolled-away pages are dropped by LazyColumn and left to GC.
    // Add an LRU bitmap cache with safe recycling if very large PDFs cause memory pressure.
}
