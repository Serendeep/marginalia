package com.serendeep.marginalia.library

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.serendeep.marginalia.data.ReadingStatus
import com.serendeep.marginalia.sharedCover
import com.serendeep.marginalia.ui.theme.BodyFamily
import com.serendeep.marginalia.ui.theme.CoursePalette
import com.serendeep.marginalia.ui.theme.DimInkDark
import com.serendeep.marginalia.ui.theme.MonoFamily
import com.serendeep.marginalia.ui.theme.Violet
import java.util.Locale

private val RowDivider = Color(0xFF18181C)
private val ChipBorder = Color(0xFF2A2A30)
private val ChipText = Color(0xFFA0A0AB)
private const val MAX_ROW_TAGS = 2

/** Dense list row: thumbnail, title with mono meta, a chip, reading progress. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LectureRow(
    row: RowModel,
    imageLoader: ImageLoader,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chip: ChipKind = ChipKind.Course,
    showProgress: Boolean = true,
    menu: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .drawBehind {
                drawRect(RowDivider, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
            }
            .padding(horizontal = 8.dp, vertical = 9.dp),
    ) {
        RowThumb(row, imageLoader)
        Column(Modifier.weight(1.3f)) {
            Text(
                row.lecture.title,
                fontFamily = BodyFamily,
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                meta,
                fontFamily = MonoFamily,
                fontSize = 11.sp,
                letterSpacing = 0.44.sp,
                color = DimInkDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        // Chips share the row with the title; whichever no longer fit drop out whole, course/status first to stay.
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            maxLines = 1,
        ) {
            when (chip) {
                ChipKind.Course -> Chip(row.course?.name ?: "Notebook", dot = CoursePalette.color(row.colorIndex))
                ChipKind.Status -> Chip(statusLabel(row.status))
            }
            row.tags.take(MAX_ROW_TAGS).forEach { Chip(it.name) }
            if (row.tags.size > MAX_ROW_TAGS) Chip("+${row.tags.size - MAX_ROW_TAGS}")
        }
        // Progress, pages and the menu keep fixed slots so they line up down the list whatever the chips need.
        val pages = row.document?.pageCount ?: 0
        if (showProgress) {
            Row(Modifier.width(ProgressSlot), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (pages > 0 && row.status != ReadingStatus.TO_READ) {
                    val page = (row.lecture.lastPage + 1).coerceIn(1, pages)
                    ProgressBar(page.toFloat() / pages)
                    Text(
                        "$page/$pages",
                        fontFamily = MonoFamily,
                        fontSize = 11.5.sp,
                        color = DimInkDark,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.width(64.dp),
                    )
                }
            }
        }
        Box(Modifier.width(MenuSlot), contentAlignment = Alignment.Center) { menu?.invoke() }
    }
}

private val ProgressSlot = 90.dp + 14.dp + 64.dp
private val MenuSlot = 48.dp

enum class ChipKind { Course, Status }

fun statusLabel(status: ReadingStatus) = when (status) {
    ReadingStatus.TO_READ -> "To read"
    ReadingStatus.READING -> "Reading"
    ReadingStatus.DONE -> "Done"
}

@Composable
private fun Chip(label: String, dot: Color? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, ChipBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(dot))
        Text(label, fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = ChipText, maxLines = 1)
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(
        Modifier
            .width(90.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.outline),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(Violet))
    }
}

/** 30x40dp page-one thumbnail; blank notebooks show their course colour and emoji. Also the shared-element source. */
@Composable
private fun RowThumb(row: RowModel, imageLoader: ImageLoader) {
    val shape = RoundedCornerShape(3.dp)
    val doc = row.document
    val base = Modifier.size(30.dp, 40.dp).sharedCover("pdf-${row.lecture.id}").clip(shape)
    if (doc != null) {
        val model = remember(doc.localPath) { PdfCover(doc.localPath) }
        AsyncImage(
            model = model,
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = base.background(Color.White),
        )
    } else {
        Box(
            base.background(CoursePalette.color(row.colorIndex).copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(row.course?.emoji ?: "✎", fontSize = 14.sp)
        }
    }
}

/** "2h ago" style label; cheap enough to compute while composing. */
fun relativeTime(at: Long, now: Long = System.currentTimeMillis()): String =
    if (now - at < DateUtils.MINUTE_IN_MILLIS) "JUST NOW" else DateUtils.getRelativeTimeSpanString(
        at,
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString().uppercase(Locale.ROOT)
