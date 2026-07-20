package com.serendeep.marginalia.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope

/** A dot pinned to a spot on a page, holding the link to related notes. */
data class PageAnchor(
    val id: String,
    val page: Int,
    val xFraction: Float,
    val yFraction: Float,
    val label: Int,
)

/** Vertical scroll of a PDF's pages, each rendered to fit the pane width. */
@Composable
fun PdfPane(
    source: PdfDocumentSource,
    modifier: Modifier = Modifier,
    anchors: List<PageAnchor> = emptyList(),
    onPageLongPress: ((page: Int, xFraction: Float, yFraction: Float) -> Unit)? = null,
    onAnchorTap: ((id: String) -> Unit)? = null,
    onAnchorRemove: ((id: String) -> Unit)? = null,
    onWebLink: ((String) -> Unit)? = null,
    scrollToPage: Int? = null,
    onScrollHandled: (() -> Unit)? = null,
    onFirstVisiblePage: ((Int) -> Unit)? = null,
) {
    BoxWithConstraints(modifier) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        // Rebuild the list from scratch when the document changes so a new PDF
        // doesn't reuse the previous one's cached pages or scroll position.
        key(source) {
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()

            LaunchedEffect(listState, onFirstVisiblePage) {
                if (onFirstVisiblePage != null) {
                    snapshotFlow { listState.firstVisibleItemIndex }
                        .collect { onFirstVisiblePage(it) }
                }
            }

            // Outside callers (the outline drawer) request a page here; consume
            // the request once done so the same page can be asked for again.
            LaunchedEffect(scrollToPage) {
                if (scrollToPage != null) {
                    listState.animateScrollToItem(scrollToPage.coerceIn(0, source.pageCount - 1))
                    onScrollHandled?.invoke()
                }
            }

            // Tell interested callers which page currently tops the pane.
            LaunchedEffect(listState, onFirstVisiblePage) {
                if (onFirstVisiblePage != null) {
                    snapshotFlow { listState.firstVisibleItemIndex }.collect(onFirstVisiblePage)
                }
            }

            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(source.pageCount) { index ->
                    PdfPageItem(
                        source = source,
                        index = index,
                        widthPx = widthPx,
                        anchors = anchors.filter { it.page == index },
                        onLongPress = onPageLongPress?.let { cb ->
                            { fx, fy -> cb(index, fx, fy) }
                        },
                        onAnchorTap = onAnchorTap,
                        onAnchorRemove = onAnchorRemove,
                        onLinkTap = { link ->
                            val dest = link.destPage
                            if (dest != null) {
                                scope.launch {
                                    listState.animateScrollToItem(dest.coerceIn(0, source.pageCount - 1))
                                }
                            } else {
                                link.uri?.let { onWebLink?.invoke(it) }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfPageItem(
    source: PdfDocumentSource,
    index: Int,
    widthPx: Int,
    anchors: List<PageAnchor> = emptyList(),
    onLongPress: ((xFraction: Float, yFraction: Float) -> Unit)? = null,
    onAnchorTap: ((id: String) -> Unit)? = null,
    onAnchorRemove: ((id: String) -> Unit)? = null,
    onLinkTap: ((PageLink) -> Unit)? = null,
) {
    var bitmap by remember(source, index, widthPx) { mutableStateOf<Bitmap?>(null) }
    var aspect by remember(source, index) { mutableStateOf(0.7f) }
    var links by remember(source, index) { mutableStateOf<List<PageLink>>(emptyList()) }

    LaunchedEffect(source, index, widthPx) {
        if (widthPx <= 0) return@LaunchedEffect
        aspect = source.pageAspectRatio(index)
        bitmap = withContext(Dispatchers.Default) { source.renderFullPage(index, widthPx) }
        links = source.pageLinks(index)
    }

    // Height follows the page aspect (width / (w:h ratio)).
    val heightDp = with(LocalDensity.current) { (widthPx / aspect).toInt().toDp() }
    val current = bitmap
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .height(heightDp)
            .padding(vertical = 4.dp)
            .background(if (current == null) Color(0xFFEAEAEA) else Color.White)
            .pointerInput(index, onLongPress) {
                detectTapGestures(
                    onLongPress = { offset ->
                        if (size.width > 0 && size.height > 0) {
                            onLongPress?.invoke(offset.x / size.width, offset.y / size.height)
                        }
                    },
                )
            },
    ) {
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "Page ${index + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillWidth,
            )
        }
        val widthDp = with(LocalDensity.current) { widthPx.toDp() }
        // Invisible tap targets over the link annotations, scaled to the item size.
        links.forEach { link ->
            androidx.compose.foundation.layout.Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = widthDp * link.bounds.left,
                        y = heightDp * link.bounds.top,
                    )
                    .size(
                        width = widthDp * link.bounds.width(),
                        height = heightDp * link.bounds.height(),
                    )
                    .pointerInput(link) {
                        detectTapGestures(onTap = { onLinkTap?.invoke(link) })
                    },
            )
        }
        anchors.forEach { anchor ->
            AnchorMarker(
                anchor = anchor,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = widthDp * anchor.xFraction - 11.dp,
                        y = heightDp * anchor.yFraction - 11.dp,
                    ),
                onTap = { onAnchorTap?.invoke(anchor.id) },
                onRemove = { onAnchorRemove?.invoke(anchor.id) },
            )
        }
    }
    // ponytail: bitmaps for scrolled-away pages are dropped by LazyColumn and left to GC.
    // Add an LRU bitmap cache with safe recycling if very large PDFs cause memory pressure.
}

@Composable
private fun AnchorMarker(
    anchor: PageAnchor,
    modifier: Modifier,
    onTap: () -> Unit,
    onRemove: () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier
            .size(22.dp)
            .background(Color(0xFF3557A6), CircleShape)
            .pointerInput(anchor.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onRemove() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = anchor.label.toString(),
            color = Color.White,
            fontSize = 11.sp,
        )
    }
}
