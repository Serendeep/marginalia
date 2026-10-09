package com.serendeep.marginalia.today

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serendeep.marginalia.cards.HighlightCardSheet
import com.serendeep.marginalia.data.HighlightRow
import com.serendeep.marginalia.highlights.HighlightItem
import com.serendeep.marginalia.library.ChipKind
import com.serendeep.marginalia.library.LectureRow
import com.serendeep.marginalia.library.LibraryFilter
import com.serendeep.marginalia.library.RowModel
import com.serendeep.marginalia.library.relativeTime
import com.serendeep.marginalia.shell.PREFS
import com.serendeep.marginalia.shell.Screen
import com.serendeep.marginalia.study.POMODORO_ROUNDS
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.theme.BodyFamily
import com.serendeep.marginalia.ui.theme.CoursePalette
import com.serendeep.marginalia.ui.theme.DimInkDark
import com.serendeep.marginalia.ui.theme.DisplayFamily
import com.serendeep.marginalia.ui.theme.Lime
import com.serendeep.marginalia.ui.theme.MonoFamily
import com.serendeep.marginalia.ui.theme.OnViolet
import com.serendeep.marginalia.ui.theme.Violet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val TileShape = RoundedCornerShape(20.dp)
private val FocusInk = Color(0xFF131600)
private val HeatColors = listOf(Color(0xFF222227), Color(0xFF3D4A14), Color(0xFF6E8A1C), Lime)
private val RingTrack = Color(0xFF2E2B45)
private val ReviewInner = Color(0xFF1C1934)
private val ReviewMuted = Color(0xFFA9A3D6)

@Composable
fun TodayScreen(
    onOpenLecture: (String) -> Unit,
    onOpenAt: (lectureId: String, page: Int) -> Unit,
    onNavigate: (Screen) -> Unit,
    onAsk: (String) -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var cardFor by remember { mutableStateOf<HighlightRow?>(null) }
    AskNotificationPermissionOnce()
    cardFor?.let { HighlightCardSheet(it, onDismiss = { cardFor = null }) }
    // The bento spans the full width: beside the highlights column it gets too narrow on 1120dp tablets.
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        com.serendeep.marginalia.update.UpdateBanners(Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp))
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 18.dp).height(232.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReviewTile(state.review, onStart = { onNavigate(Screen.Review) }, modifier = Modifier.weight(1.6f))
            FocusTile(viewModel, state.next, Modifier.weight(1f))
            StreakTile(state, Modifier.weight(1f))
        }
    Row(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 22.dp, vertical = 6.dp),
        ) {
            if (state.loaded && !state.hasLectures) {
                EmptyToday(onImport = { onNavigate(Screen.Library()) })
            } else {
                if (state.continueRows.isNotEmpty()) {
                    SectionHeader("Continue", "LIBRARY →") { onNavigate(Screen.Library()) }
                    state.continueRows.forEach { row ->
                        key(row.lecture.id) { ContinueRow(row, viewModel, onOpenLecture) }
                    }
                }
                if (state.toRead.isNotEmpty()) {
                    SectionHeader("To read", "${state.toReadCount} QUEUED") {
                        onNavigate(Screen.Library(LibraryFilter.Status(com.serendeep.marginalia.data.ReadingStatus.TO_READ)))
                    }
                    state.toRead.forEach { row ->
                        key(row.lecture.id) { ToReadRow(row, viewModel, onOpenLecture) }
                    }
                }
            }
        }
        Column(
            Modifier
                .width(300.dp)
                .padding(top = 20.dp)
                .drawBehind {
                    drawRect(
                        Color(0xFF222227),
                        size = Size(1.dp.toPx(), size.height),
                    )
                }
                .padding(18.dp),
        ) {
            SectionHeader("Recent highlights", "ALL →", topPadding = 0.dp) { onNavigate(Screen.Highlights) }
            if (state.highlights.isEmpty()) {
                Text(
                    "Highlights you make on PDF text show up here.",
                    fontFamily = BodyFamily,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.highlights.forEach { row ->
                        key(row.highlight.id) {
                            HighlightItem(
                                row = row,
                                showTitle = true,
                                onClick = { onOpenAt(row.highlight.lectureId, row.highlight.page) },
                                onMakeCard = { cardFor = row },
                            )
                        }
                    }
                }
            }
            if (com.serendeep.marginalia.ai.ui.rememberAiReady()) {
                com.serendeep.marginalia.ai.ui.AskChatGptCard(onAsk, Modifier.padding(top = 16.dp))
            }
        }
    }
    }
}

@Composable
private fun ContinueRow(row: RowModel, viewModel: TodayViewModel, onOpen: (String) -> Unit) {
    LectureRow(
        row = row,
        imageLoader = viewModel.imageLoader,
        meta = remember(row.lecture.id, row.touchedAt) {
            "${(row.course?.name ?: "Notebook").uppercase(Locale.ROOT)} · ${relativeTime(row.touchedAt)}"
        },
        onClick = { onOpen(row.lecture.id) },
        chip = ChipKind.Course,
    )
}

@Composable
private fun ToReadRow(row: RowModel, viewModel: TodayViewModel, onOpen: (String) -> Unit) {
    LectureRow(
        row = row,
        imageLoader = viewModel.imageLoader,
        meta = remember(row.lecture.id) { "${(row.course?.name ?: "Notebook").uppercase(Locale.ROOT)} · NOT OPENED" },
        onClick = { onOpen(row.lecture.id) },
        chip = ChipKind.Status,
        showProgress = false,
    )
}

@Composable
private fun SectionHeader(title: String, action: String, topPadding: androidx.compose.ui.unit.Dp = 20.dp, onAction: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = topPadding, bottom = 6.dp),
    ) {
        Text(title, fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            action,
            fontFamily = MonoFamily,
            fontSize = 11.sp,
            letterSpacing = 0.66.sp,
            color = DimInkDark,
            modifier = Modifier.clickable(onClick = onAction),
        )
    }
}

@Composable
private fun EmptyToday(onImport: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Nothing to continue yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Import a PDF or start a notebook.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        GlassButton("Open library", onClick = onImport)
    }
}

@Composable
internal fun Tile(modifier: Modifier, background: Modifier = Modifier, border: Color, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxHeight()
            .clip(TileShape)
            .then(background)
            .border(1.dp, border, TileShape)
            .padding(18.dp),
    ) { content() }
}

@Composable
internal fun TileLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.ROOT),
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        letterSpacing = 1.26.sp,
        color = color,
        modifier = modifier,
    )
}

@Composable
private fun ReviewTile(review: ReviewTileState, onStart: () -> Unit, modifier: Modifier) {
    val brush = remember {
        Brush.linearGradient(
            0f to Color(0xFF251F49),
            0.72f to Color(0xFF141417),
            start = Offset.Zero,
            end = Offset(900f, 700f),
        )
    }
    val progress = review.progress
    Tile(modifier, Modifier.background(brush), Color(0xFF2D2756)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(128.dp)
                    .drawBehind {
                        val w = 12.dp.toPx()
                        val inset = Offset(w / 2, w / 2)
                        val arc = Size(size.width - w, size.height - w)
                        drawArc(RingTrack, 0f, 360f, false, inset, arc, style = Stroke(w))
                        if (progress > 0f) {
                            drawArc(Violet, -90f, 360f * progress, false, inset, arc, style = Stroke(w, cap = StrokeCap.Round))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${review.due}", fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, color = Color.White)
                    Text("due", fontFamily = BodyFamily, fontSize = 12.sp, color = ReviewMuted)
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                TileLabel("Review queue", ReviewMuted)
                if (review.due > 0) {
                    val split = review.split
                    Text(
                        "${split.newCards} new · ${split.learning} learning · ${split.lapsed} lapsed",
                        fontFamily = BodyFamily,
                        fontSize = 12.5.sp,
                        color = ReviewMuted,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    review.courses.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 5.dp)) {
                            Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(CoursePalette.color(c.colorIndex)))
                            Text(
                                c.name,
                                fontFamily = BodyFamily,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp).weight(1f),
                            )
                            Text("${c.due}", fontFamily = MonoFamily, fontSize = 11.sp, color = ReviewMuted)
                        }
                    }
                    Text(
                        "Start · ${review.minutes} min",
                        fontFamily = BodyFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = OnViolet,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Violet)
                            .clickable(onClick = onStart)
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                } else {
                    Text(
                        if (review.hasCards) "All caught up. Nothing is due right now." else "Lasso anything in a notebook to make your first card",
                        fontFamily = BodyFamily,
                        fontSize = 12.5.sp,
                        lineHeight = 20.sp,
                        color = ReviewMuted,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusTile(viewModel: TodayViewModel, next: NextUp?, modifier: Modifier) {
    Tile(modifier, Modifier.background(Lime), Lime) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.align(Alignment.TopStart)) {
                TileLabel("Focus", Color(0xFF4A5A00))
                FocusTime(viewModel)
                Text(
                    if (next != null) "Next: ${next.title}, p.${next.page}" else "Nothing queued",
                    fontFamily = BodyFamily,
                    fontSize = 12.5.sp,
                    color = Color(0xFF3B4700),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            FocusChips(viewModel, Modifier.align(Alignment.BottomStart))
            FocusButton(viewModel, Modifier.align(Alignment.BottomEnd))
        }
    }
}

/** The only thing that recomposes each second. */
@Composable
private fun FocusTime(viewModel: TodayViewModel) {
    val focus = viewModel.focus.collectAsStateWithLifecycle()
    val seconds by remember(focus) { derivedStateOf { focus.value.remainingSec } }
    Text(
        "%02d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60),
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 52.sp,
        letterSpacing = (-2).sp,
        color = FocusInk,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun FocusChips(viewModel: TodayViewModel, modifier: Modifier) {
    val focus = viewModel.focus.collectAsStateWithLifecycle()
    val round by remember(focus) { derivedStateOf { focus.value.round } }
    Text(
        "ROUND $round/$POMODORO_ROUNDS",
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        color = FocusInk,
        modifier = modifier
            .padding(bottom = 4.dp)
            .border(1.5.dp, FocusInk, RoundedCornerShape(9.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun FocusButton(viewModel: TodayViewModel, modifier: Modifier) {
    val focus = viewModel.focus.collectAsStateWithLifecycle()
    val running by remember(focus) { derivedStateOf { focus.value.running } }
    Box(
        modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(FocusInk)
            .clickable(onClick = viewModel::toggleFocus),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (running) "Pause focus timer" else "Start focus timer",
            tint = Lime,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun StreakTile(state: TodayState, modifier: Modifier) {
    Tile(modifier, Modifier.background(MaterialTheme.colorScheme.surface), MaterialTheme.colorScheme.outline) {
        Column {
            TileLabel("Streak", MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
                Text("${state.streak}", fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 38.sp, letterSpacing = (-1.1).sp)
                Text(
                    " days",
                    fontFamily = BodyFamily,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Text(
                if (state.retention != null) "Best ${state.best} · ${state.retention}% retention" else "Best ${state.best}",
                fontFamily = BodyFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Heatmap(state.heat, Modifier.padding(top = 14.dp).fillMaxWidth().aspectRatio(14f / 3.3f))
        }
    }
}

/** 14 columns by 3 rows in one canvas; no per-cell composables. */
@Composable
private fun Heatmap(levels: List<Int>, modifier: Modifier) {
    Canvas(modifier) {
        if (levels.isEmpty()) return@Canvas
        val cols = 14
        val gap = 4.dp.toPx()
        val cell = (size.width - gap * (cols - 1)) / cols
        val radius = CornerRadius(3.dp.toPx())
        levels.forEachIndexed { i, level ->
            drawRoundRect(
                HeatColors[level.coerceIn(0, 3)],
                topLeft = Offset((i % cols) * (cell + gap), (i / cols) * (cell + gap)),
                size = Size(cell, cell),
                cornerRadius = radius,
            )
        }
    }
}

private const val NOTIF_ASKED_KEY = "notification_permission_asked"

/** Asks for notification permission the first time Today opens (Android 13+); older versions need nothing. */
@Composable
private fun AskNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val ask = withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            (!granted && !prefs.getBoolean(NOTIF_ASKED_KEY, false)).also {
                if (it) prefs.edit().putBoolean(NOTIF_ASKED_KEY, true).apply()
            }
        }
        if (ask) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
