package com.serendeep.marginalia.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serendeep.marginalia.today.Tile
import com.serendeep.marginalia.today.TileLabel
import com.serendeep.marginalia.ui.theme.BodyFamily
import com.serendeep.marginalia.ui.theme.CoursePalette
import com.serendeep.marginalia.ui.theme.DimInkDark
import com.serendeep.marginalia.ui.theme.DisplayFamily
import com.serendeep.marginalia.ui.theme.Lime
import com.serendeep.marginalia.ui.theme.MonoFamily
import com.serendeep.marginalia.ui.theme.Violet
import java.time.format.TextStyle as DayStyle
import java.util.Locale

private val ReviewBorder = Color(0xFF2D2756)

@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val surface = MaterialTheme.colorScheme.surface
    val hairline = MaterialTheme.colorScheme.outline
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(268.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Modifier.weight(2.2f), Modifier.background(surface), hairline) { FocusChart(state) }
            Tile(Modifier.weight(1f), Modifier.background(surface), hairline) { StreakPanel(state) }
        }
        Row(Modifier.fillMaxWidth().height(232.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Modifier.weight(1.4f), Modifier.background(surface), hairline) { CoursePanel(state.courses) }
            Tile(Modifier.weight(1f), Modifier.background(surface), ReviewBorder) { CardsPanel(state) }
        }
    }
}

@Composable
private fun FocusChart(state: StatsState) {
    val measurer = rememberTextMeasurer()
    val top = remember(state.focus) { niceMax(state.focus.max()) }
    val label = TextStyle(fontFamily = MonoFamily, fontSize = 9.5.sp, color = DimInkDark)
    val todayLabel = label.copy(color = Lime, fontWeight = FontWeight.Medium)
    val grid = MaterialTheme.colorScheme.outline
    val yLabels = remember(top) { listOf(0, top / 2, top).map { measurer.measure(if (it == 0) "0" else "${it}m", label) } }
    val xLabels = remember(state.focus, state.today) {
        state.focus.indices.map { i ->
            val day = state.today.minusDays((state.focus.size - 1 - i).toLong())
            val letter = day.dayOfWeek.getDisplayName(DayStyle.NARROW, Locale.ENGLISH)
            measurer.measure(letter, if (i == state.focus.lastIndex) todayLabel else label)
        }
    }
    Column(Modifier.fillMaxSize()) {
        TileLabel("Focus · 14 days", Lime)
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)) {
            val axisW = 34.dp.toPx()
            val axisH = 18.dp.toPx()
            val plotW = size.width - axisW
            val plotH = size.height - axisH
            val line = 1.dp.toPx()
            listOf(0f, 0.5f, 1f).forEachIndexed { i, f ->
                val y = plotH * (1f - f)
                drawRect(grid, Offset(axisW, y - line / 2), Size(plotW, line))
                val t = yLabels[i]
                val ty = (y - t.size.height / 2f).coerceIn(0f, size.height - axisH - t.size.height)
                drawText(t, topLeft = Offset(axisW - t.size.width - 6.dp.toPx(), ty))
            }
            val slot = plotW / state.focus.size
            val barW = slot * 0.56f
            state.focus.forEachIndexed { i, m ->
                val x = axisW + slot * i + (slot - barW) / 2
                val h = plotH * (m.coerceAtMost(top).toFloat() / top)
                val isToday = i == state.focus.lastIndex
                if (h > 0f) {
                    drawRoundRect(
                        Lime.copy(alpha = if (isToday) 1f else 0.38f),
                        Offset(x, plotH - h),
                        Size(barW, h),
                        CornerRadius(3.dp.toPx()),
                    )
                }
                val t = xLabels[i]
                drawText(t, topLeft = Offset(x + (barW - t.size.width) / 2, plotH + 5.dp.toPx()))
            }
        }
    }
}

@Composable
private fun StreakPanel(state: StatsState) {
    Column(Modifier.fillMaxSize()) {
        TileLabel("Streak", MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
            Text("${state.streak}", fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, letterSpacing = (-1.3).sp, color = Lime)
            Text(
                " days",
                fontFamily = BodyFamily,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Text("Best ${state.best} days", fontFamily = BodyFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f))
        TileLabel("Total time", MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
            Text(hoursLabel(state.totalMinutes), fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, letterSpacing = (-0.8).sp)
            Text(
                " h",
                fontFamily = BodyFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun CoursePanel(courses: List<CourseBar>) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TileLabel("This week by course", Lime)
        if (courses.isEmpty()) {
            Text("No study time yet this week.", fontFamily = BodyFamily, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val max = courses.maxOfOrNull { it.minutes } ?: 1
        courses.take(5).forEach { bar ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    bar.name,
                    fontFamily = BodyFamily,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(110.dp),
                )
                Box(Modifier.weight(1f).height(8.dp)) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(bar.minutes.toFloat() / max)
                            .clip(RoundedCornerShape(4.dp))
                            .background(CoursePalette.color(bar.colorIndex)),
                    )
                }
                Text(
                    "${bar.minutes}m",
                    fontFamily = MonoFamily,
                    fontSize = 11.sp,
                    color = DimInkDark,
                    modifier = Modifier.width(44.dp),
                )
            }
        }
    }
}

@Composable
private fun CardsPanel(state: StatsState) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TileLabel("Cards", Violet)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardStat("${state.reviewed7}", "Reviewed · 7d", Modifier.weight(1f))
            CardStat(state.retention?.let { "$it%" } ?: "—", "Retention · 30d", Modifier.weight(1f))
        }
        CardStat("${state.dueTomorrow}", "Due tomorrow", Modifier)
    }
}

@Composable
private fun CardStat(value: String, caption: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = (-0.9).sp, color = Violet)
        TileLabel(caption, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
