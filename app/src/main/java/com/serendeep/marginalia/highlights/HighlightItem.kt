package com.serendeep.marginalia.highlights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serendeep.marginalia.data.HighlightRow
import com.serendeep.marginalia.ui.theme.BodyFamily
import com.serendeep.marginalia.ui.theme.MonoFamily
import java.util.Locale

/** A highlighted passage: tinted fill, solid left bar, mono page cite. */
@Composable
fun HighlightItem(
    row: HighlightRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = false,
    text: @Composable () -> Unit = { HighlightText(row.highlight.text) },
) {
    val color = remember(row.highlight.color) { Color(row.highlight.color.toInt()).copy(alpha = 1f) }
    val cite = remember(row.highlight.page, row.lectureTitle, showTitle) {
        val page = "P.${row.highlight.page + 1}"
        if (showTitle) "$page · ${row.lectureTitle.uppercase(Locale.ROOT)}" else page
    }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
            .drawBehind {
                drawRect(color.copy(alpha = 0.25f))
                drawRect(color, size = Size(3.dp.toPx(), size.height))
            }
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    ) {
        text()
        Text(
            cite,
            fontFamily = MonoFamily,
            fontSize = 10.5.sp,
            letterSpacing = 0.63.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
fun HighlightText(text: String) {
    Text(text, fontFamily = BodyFamily, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
}
