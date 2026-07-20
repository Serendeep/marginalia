package com.serendeep.marginalia.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** A dot pinned to a spot on a page, holding the link to related notes. */
data class PageAnchor(
    val id: String,
    val page: Int,
    val xFraction: Float,
    val yFraction: Float,
    val label: Int,
)

/** A sharp re-render of the visible slice of one page, placed in screen space. */
private data class SharpSlice(
    val bitmap: Bitmap,
    val screenPos: IntOffset,
    val sizePx: IntSize,
)

/** Vertical scroll of a PDF's pages with finger pinch-zoom; each page fits the pane width. */
@OptIn(FlowPreview::class)
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
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        val viewportHeightPx = with(density) { maxHeight.roundToPx() }
        val pagePadPx = with(density) { 4.dp.roundToPx() }
        // Rebuild the list from scratch when the document changes so a new PDF
        // doesn't reuse the previous one's cached pages, zoom, or scroll position.
        key(source) {
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            var zoom by remember { mutableStateOf(PdfZoomState()) }
            var sharp by remember { mutableStateOf<SharpSlice?>(null) }

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

            // Once the zoom settles, replace the scaled-up (soft) pixels with a
            // sharp render of just the slice on screen. Any movement drops it.
            LaunchedEffect(source, widthPx, viewportHeightPx) {
                snapshotFlow {
                    Triple(zoom, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                }
                    .debounce(150)
                    .collect { (settledZoom, _, _) ->
                        sharp = if (settledZoom.zoomed) {
                            renderSharpSlice(
                                source, listState, settledZoom, widthPx, viewportHeightPx, pagePadPx,
                            )
                        } else {
                            null
                        }
                    }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(source) {
                        detectTapGestures(onDoubleTap = { tap ->
                            sharp = null
                            zoom = zoom.doubleTap(tap.x, tap.y).clampToContent(
                                size.width.toFloat(),
                                size.height.toFloat(),
                                size.width.toFloat(),
                                size.height.toFloat(),
                            )
                        })
                    }
                    .pointerInput(source) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                // The stylus is ink-only, and a single finger at 1x
                                // belongs to the list's own scrolling.
                                if (pressed.any { it.type == PointerType.Stylus }) continue
                                val multiTouch = pressed.size >= 2
                                if (!multiTouch && !zoom.zoomed) continue

                                val zoomChange = if (multiTouch) event.calculateZoom() else 1f
                                val pan = event.calculatePan()
                                if (zoomChange == 1f && pan == Offset.Zero) continue
                                val centroid = event.calculateCentroid(useCurrent = true)

                                sharp = null
                                val before = zoom
                                zoom = zoom
                                    .applyGesture(centroid.x, centroid.y, pan.x, pan.y, zoomChange)
                                    .clampToContent(
                                        size.width.toFloat(),
                                        size.height.toFloat(),
                                        size.width.toFloat(),
                                        size.height.toFloat(),
                                    )
                                // Panning past the vertical clamp hands the leftover
                                // motion to the page list so reading keeps flowing.
                                if (!multiTouch) {
                                    val leftover = pan.y - (zoom.offsetY - before.offsetY)
                                    if (leftover != 0f) {
                                        scope.launch { listState.scrollBy(-leftover / zoom.scale) }
                                    }
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = zoom.scale
                            scaleY = zoom.scale
                            translationX = zoom.offsetX
                            translationY = zoom.offsetY
                        },
                ) {
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

                sharp?.let { slice ->
                    Image(
                        bitmap = slice.bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .offset { slice.screenPos }
                            .size(
                                with(density) { slice.sizePx.width.toDp() },
                                with(density) { slice.sizePx.height.toDp() },
                            ),
                        contentScale = ContentScale.FillBounds,
                    )
                }
            }
        }
    }
}

/**
 * Sharp render of the page slice at the viewport center. Screen and layout
 * space differ by the zoom transform: screen = layout * scale + offset.
 */
private suspend fun renderSharpSlice(
    source: PdfDocumentSource,
    listState: LazyListState,
    zoom: PdfZoomState,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    pagePadPx: Int,
): SharpSlice? {
    val centerLayoutY = (viewportHeightPx / 2f - zoom.offsetY) / zoom.scale
    val item = listState.layoutInfo.visibleItemsInfo
        .firstOrNull { centerLayoutY >= it.offset && centerLayoutY < it.offset + it.size }
        ?: listState.layoutInfo.visibleItemsInfo.firstOrNull()
        ?: return null

    // Visible layout-space rect, clipped to this page's bitmap area.
    val leftL = ((0f - zoom.offsetX) / zoom.scale).coerceIn(0f, viewportWidthPx.toFloat())
    val rightL = ((viewportWidthPx - zoom.offsetX) / zoom.scale).coerceIn(0f, viewportWidthPx.toFloat())
    val topL = ((0f - zoom.offsetY) / zoom.scale).coerceAtLeast((item.offset + pagePadPx).toFloat())
    val bottomL = ((viewportHeightPx - zoom.offsetY) / zoom.scale)
        .coerceAtMost((item.offset + item.size - pagePadPx).toFloat())
    if (rightL <= leftL || bottomL <= topL) return null

    val outW = ((rightL - leftL) * zoom.scale).roundToInt().coerceIn(1, viewportWidthPx * 2)
    val outH = ((bottomL - topL) * zoom.scale).roundToInt().coerceIn(1, viewportHeightPx * 2)

    val pageHeightPx = item.size - 2 * pagePadPx
    if (pageHeightPx <= 0) return null

    val bitmap = withContext(Dispatchers.Default) {
        runCatching {
            source.renderRegion(
                index = item.index,
                scaledPageWidthPx = (viewportWidthPx * zoom.scale).roundToInt(),
                scaledPageHeightPx = (pageHeightPx * zoom.scale).roundToInt(),
                srcLeftPx = (leftL * zoom.scale).roundToInt(),
                srcTopPx = ((topL - item.offset - pagePadPx) * zoom.scale).roundToInt().coerceAtLeast(0),
                outWidthPx = outW,
                outHeightPx = outH,
            )
        }.getOrNull()
    } ?: return null

    return SharpSlice(
        bitmap = bitmap,
        screenPos = IntOffset(
            (leftL * zoom.scale + zoom.offsetX).roundToInt(),
            (topL * zoom.scale + zoom.offsetY).roundToInt(),
        ),
        sizePx = IntSize(outW, outH),
    )
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
    Box(
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
            Box(
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
    Box(
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
