package com.serendeep.marginalia.shell

import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoAwesomeMotion
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serendeep.marginalia.BuildConfig
import com.serendeep.marginalia.data.ReadingStatus
import com.serendeep.marginalia.library.LibraryFilter
import com.serendeep.marginalia.library.statusLabel
import com.serendeep.marginalia.ui.theme.BodyFamily
import com.serendeep.marginalia.ui.theme.CoursePalette
import com.serendeep.marginalia.ui.theme.DimInkDark
import com.serendeep.marginalia.ui.theme.DisplayFamily
import com.serendeep.marginalia.ui.theme.Lime
import com.serendeep.marginalia.ui.theme.MonoFamily
import com.serendeep.marginalia.ui.theme.Violet
import com.serendeep.marginalia.ui.theme.OnViolet
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/** Where the app is. Notebook is full-screen; everything else lives inside [AppShell]. */
@Immutable
sealed interface Screen {
    data object Today : Screen
    data class Library(val filter: LibraryFilter = LibraryFilter.All) : Screen
    data object Review : Screen
    data object Search : Screen
    data object Ask : Screen
    data object Highlights : Screen
    data object Stats : Screen
    data class Notebook(val lectureId: String, val returnTo: Screen, val page: Int? = null) : Screen
}

private val SidebarWidth = 220.dp
private val GoalTrack = Color(0xFF26262B)

@Composable
fun AppShell(
    screen: Screen,
    onNavigate: (Screen) -> Unit,
    viewModel: ShellViewModel = hiltViewModel(),
    content: @Composable BoxScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        Sidebar(screen, onNavigate, viewModel)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            TopBar(screen, viewModel, onSearch = { onNavigate(Screen.Search) })
            Box(Modifier.weight(1f).fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun Sidebar(screen: Screen, onNavigate: (Screen) -> Unit, viewModel: ShellViewModel) {
    val state by viewModel.sidebar.collectAsStateWithLifecycle()
    val hairline = MaterialTheme.colorScheme.outline
    val filter = (screen as? Screen.Library)?.filter
    var settingsOpen by remember { mutableStateOf(false) }
    Column(
        Modifier
            .width(SidebarWidth)
            .fillMaxHeight()
            .drawBehind {
                drawRect(hairline, topLeft = Offset(size.width - 1.dp.toPx(), 0f), size = Size(1.dp.toPx(), size.height))
            }
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 16.dp),
            ) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(Lime))
                Text(
                    "Marginalia",
                    fontFamily = DisplayFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    letterSpacing = (-0.34).sp,
                    modifier = Modifier.weight(1f),
                )
                if (BuildConfig.CHANNEL == "nightly") NightlyBrand()
                IconButton(onClick = { settingsOpen = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            NavItem("Today", Icons.Outlined.Home, screen == Screen.Today) { onNavigate(Screen.Today) }
            NavItem(
                "Library", Icons.Outlined.LibraryBooks,
                filter == LibraryFilter.All,
                count = state.libraryCount.toString(),
            ) { onNavigate(Screen.Library()) }
            NavItem(
                "Review", Icons.Outlined.Style, screen == Screen.Review,
                badge = state.reviewDue.takeIf { it > 0 }?.toString(),
            ) { onNavigate(Screen.Review) }
            NavItem("Search", Icons.Outlined.Search, screen == Screen.Search) { onNavigate(Screen.Search) }
            if (com.serendeep.marginalia.ai.ui.rememberAiReady()) {
                NavItem("Ask", Icons.Outlined.AutoAwesomeMotion, screen == Screen.Ask) { onNavigate(Screen.Ask) }
            }
            NavItem(
                "Highlights", Icons.Outlined.AutoAwesome, screen == Screen.Highlights,
                count = state.highlights.toString(),
            ) { onNavigate(Screen.Highlights) }
            NavItem("Stats", Icons.Outlined.BarChart, screen == Screen.Stats) { onNavigate(Screen.Stats) }

            SectionLabel("COURSES")
            state.courses.forEach { course ->
                NavItem(
                    course.name, null,
                    filter == LibraryFilter.Course(course.id),
                    count = course.count.toString(),
                    dot = CoursePalette.color(course.colorIndex),
                ) { onNavigate(Screen.Library(LibraryFilter.Course(course.id))) }
            }

            if (state.tags.isNotEmpty()) {
                SectionLabel("TAGS")
                state.tags.forEach { tag ->
                    NavItem(
                        tag.name, Icons.Outlined.Tag,
                        filter == LibraryFilter.Tag(tag.id),
                        count = tag.count.toString(),
                    ) { onNavigate(Screen.Library(LibraryFilter.Tag(tag.id))) }
                }
            }

            SectionLabel("READING")
            listOf(
                Triple(ReadingStatus.TO_READ, Icons.Outlined.Circle, state.toRead),
                Triple(ReadingStatus.READING, Icons.Filled.Contrast, state.reading),
                Triple(ReadingStatus.DONE, Icons.Filled.Circle, state.done),
            ).forEach { (status, icon, count) ->
                NavItem(
                    statusLabel(status), icon,
                    filter == LibraryFilter.Status(status),
                    count = count.toString(),
                ) { onNavigate(Screen.Library(LibraryFilter.Status(status))) }
            }
        }
        com.serendeep.marginalia.update.UpdateSidebarRow()
        com.serendeep.marginalia.ai.ui.AiSidebarItem()
        GoalCard(viewModel, settingsOpen) { settingsOpen = it }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        letterSpacing = 1.26.sp,
        color = DimInkDark,
        modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun NavItem(
    label: String,
    icon: ImageVector?,
    selected: Boolean,
    count: String? = null,
    badge: String? = null,
    dot: Color? = null,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        tween(150),
        label = "nav-bg",
    )
    val shape = RoundedCornerShape(9.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
            .drawBehind {
                drawRect(bg)
                if (selected) drawRect(Violet, size = Size(2.dp.toPx(), size.height))
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        val tint = if (selected) Color.White else Color(0xFFA0A0AB)
        when {
            dot != null -> Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(dot))
            icon != null -> Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Text(
            label,
            fontFamily = BodyFamily,
            fontSize = 13.5.sp,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (badge != null) {
            Text(
                badge,
                fontFamily = MonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = OnViolet,
                modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(Violet).padding(horizontal = 7.dp, vertical = 1.dp),
            )
        } else if (count != null) {
            Text(count, fontFamily = MonoFamily, fontSize = 11.sp, color = DimInkDark)
        }
    }
}

/** Minutes today against the daily goal. Reads its own state, so a ticking counter never recomposes the sidebar. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GoalCard(viewModel: ShellViewModel, settingsOpen: Boolean, onSettingsOpen: (Boolean) -> Unit) {
    val minutes by viewModel.minutesToday.collectAsStateWithLifecycle()
    val goal by viewModel.goalMin.collectAsStateWithLifecycle()
    val reminder by viewModel.reminder.collectAsStateWithLifecycle()
    val pencilAction by viewModel.pencilAction.collectAsStateWithLifecycle()
    val handwritingSearch by viewModel.handwritingSearch.collectAsStateWithLifecycle()
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()
    if (settingsOpen) {
        LaunchedEffect(Unit) { viewModel.refreshModel() }
        SettingsDialog(
            goal, reminder, pencilAction, handwritingSearch, modelState,
            onDownloadModel = viewModel::downloadModel,
            onDismiss = { onSettingsOpen(false) },
            onSave = { g, r, a, h ->
                onSettingsOpen(false)
                viewModel.saveSettings(g, r, a, h)
            },
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { viewModel.setGoal(nextGoal(goal)) },
                onLongClick = { onSettingsOpen(true) },
            )
            .padding(12.dp),
    ) {
        Canvas(Modifier.size(40.dp)) {
            val w = 5.dp.toPx()
            val inset = w / 2
            val arc = Size(size.width - w, size.height - w)
            drawArc(GoalTrack, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
            val frac = (minutes.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
            if (frac > 0f) {
                drawArc(Lime, -90f, 360f * frac, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Butt))
            }
        }
        Column {
            Text(
                "${duration(minutes)} / ${duration(goal)}",
                fontFamily = DisplayFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
            )
            Text("Daily goal", fontFamily = BodyFamily, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun nextGoal(current: Int): Int {
    val steps = listOf(30, 60, 90, 120, 180)
    return steps.firstOrNull { it > current } ?: steps.first()
}

private fun duration(min: Int): String =
    if (min >= 60) "${min / 60}h${if (min % 60 > 0) " ${min % 60}m" else ""}" else "${min}m"

@Composable
private fun TopBar(screen: Screen, viewModel: ShellViewModel, onSearch: () -> Unit) {
    val sidebar by viewModel.sidebar.collectAsStateWithLifecycle()
    val hairline = MaterialTheme.colorScheme.outline
    val (title, crumb) = remember(screen, sidebar) { titleFor(screen, sidebar) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .drawBehind {
                drawRect(hairline, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
            }
            .padding(horizontal = 24.dp),
    ) {
        Text(title, fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.4).sp, maxLines = 1)
        Text(crumb, fontFamily = MonoFamily, fontSize = 12.sp, letterSpacing = 0.72.sp, color = DimInkDark, maxLines = 1)
        Spacer(Modifier.weight(1f))
        if (screen != Screen.Search) Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .width(360.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, hairline, RoundedCornerShape(18.dp))
                .clickable(onClick = onSearch)
                .padding(horizontal = 14.dp),
        ) {
            Icon(Icons.Outlined.Search, null, tint = DimInkDark, modifier = Modifier.size(16.dp))
            Text("Search notes, PDFs, highlights", fontFamily = BodyFamily, fontSize = 13.sp, color = DimInkDark, maxLines = 1)
        }
    }
}

private fun titleFor(screen: Screen, sidebar: SidebarState): Pair<String, String> = when (screen) {
    Screen.Today -> {
        val now = LocalDateTime.now()
        val greeting = when (now.hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
        val d = LocalDate.now()
        greeting to "%s %02d %s".format(
            Locale.ROOT,
            d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
            d.dayOfMonth,
            d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
        ).uppercase(Locale.ROOT)
    }
    is Screen.Library -> when (val f = screen.filter) {
        LibraryFilter.All -> "Library" to "%03d NOTEBOOKS".format(Locale.ROOT, sidebar.libraryCount)
        is LibraryFilter.Course -> {
            val course = sidebar.courses.firstOrNull { it.id == f.courseId }
            (course?.name ?: "Library") to "%03d NOTEBOOKS".format(Locale.ROOT, course?.count ?: 0)
        }
        is LibraryFilter.Status -> "Library" to statusLabel(f.status).uppercase(Locale.ROOT)
        is LibraryFilter.Tag -> "Library" to (sidebar.tags.firstOrNull { it.id == f.tagId }?.name ?: "Tag").uppercase(Locale.ROOT)
    }
    Screen.Review -> "Review" to ""
    Screen.Search -> "Search" to ""
    Screen.Ask -> "Ask" to ""
    Screen.Highlights -> "Highlights" to ""
    Screen.Stats -> "Stats" to ""
    is Screen.Notebook -> "" to ""
}
