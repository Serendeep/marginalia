package com.serendeep.marginalia.library

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.serendeep.marginalia.sharedCover
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.components.GlassDialog
import com.serendeep.marginalia.ui.components.GlassTextButton
import com.serendeep.marginalia.ui.theme.CoursePalette
import com.serendeep.marginalia.ui.theme.MonoFamily
import kotlinx.coroutines.delay

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onOpenLecture: (String) -> Unit,
) {
    val shelf by viewModel.shelf.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var showNewCourse by remember { mutableStateOf(false) }

    // One screen-level launcher for every import flow; a per-card launcher in a
    // lazy grid would unregister when its card is recycled while the system
    // picker is open. The target string encodes where the picked PDFs go:
    // "quick" or "quick:<courseId>" spawn filename-titled lectures, and
    // "replace:<lectureId>" adds a new version to an existing lecture.
    var importTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        val target = importTarget
        importTarget = null
        when {
            uris.isEmpty() || target == null -> Unit
            target == "quick" -> viewModel.quickImport(uris)
            target.startsWith("quick:") -> viewModel.quickImport(uris, target.removePrefix("quick:"))
            target.startsWith("replace:") -> viewModel.importPdf(target.removePrefix("replace:"), uris.first())
        }
    }
    val launchImport: (String) -> Unit = {
        importTarget = it
        picker.launch(arrayOf("application/pdf"))
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (shelf.isEmpty) {
            EmptyShelf(onImport = { launchImport("quick") })
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp),
                    ) {
                        Text("Library", style = MaterialTheme.typography.displaySmall)
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlassTextButton("New course", onClick = { showNewCourse = true })
                                Spacer(Modifier.width(12.dp))
                                GlassButton("Import PDFs", onClick = { launchImport("quick") })
                            }
                            Spacer(Modifier.height(8.dp))
                            val notebooks = shelf.sections.sumOf { it.items.size }
                            Text(
                                "%03d NOTEBOOKS · %d COURSES".format(notebooks, shelf.sections.size),
                                fontFamily = MonoFamily,
                                fontSize = 12.sp,
                                letterSpacing = 1.6.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                shelf.hero?.let { hero ->
                    item(key = "hero") {
                        ContinueBanner(
                            item = hero,
                            viewModel = viewModel,
                            onOpen = { onOpenLecture(hero.lecture.id) },
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                shelf.sections.forEach { section ->
                    item(key = "hdr:${section.course?.id ?: "unsorted"}") {
                        CourseHeader(section)
                    }
                    val sectionColor = CoursePalette.color(section.course?.colorIndex ?: 0)
                    itemsIndexed(section.items, key = { _, item -> item.lecture.id }) { _, item ->
                        NotebookRow(
                            item = item,
                            courseColor = sectionColor,
                            viewModel = viewModel,
                            onOpen = { onOpenLecture(item.lecture.id) },
                            menu = {},
                        )
                    }
                }
            }
        }

        // Import success stays quiet: one confirming tick, nothing on screen.
        val celebration by viewModel.celebration.collectAsStateWithLifecycle()
        val haptics = LocalHapticFeedback.current
        if (celebration > 0) {
            LaunchedEffect(celebration) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            }
        }

        error?.let { message ->
            LaunchedEffect(message) {
                delay(4_000)
                viewModel.dismissError()
            }
            Text(
                message,
                color = MaterialTheme.colorScheme.onError,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
                    .background(MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }

    if (showNewCourse) {
        CourseEditorDialog(
            onDismiss = { showNewCourse = false },
            onSave = { name, colorIndex, emoji ->
                viewModel.createCourse(name, colorIndex, emoji)
                showNewCourse = false
            },
        )
    }
}

/** Wide resume banner: the one thing the screen is for. */
@Composable
private fun ContinueBanner(
    item: ShelfItem,
    viewModel: LibraryViewModel,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(16.dp),
    ) {
        CoverThumb(item, viewModel, width = 52.dp)
        Column(Modifier.weight(1f)) {
            Text(item.lecture.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "CONTINUE · ${pageCountLabel(item.document?.pageCount)}",
                fontFamily = MonoFamily,
                fontSize = 11.sp,
                letterSpacing = 1.4.sp,
                color = accent,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        GlassButton("Resume", onClick = onOpen)
    }
}

@Composable
private fun CourseHeader(section: ShelfSection) {
    val color = CoursePalette.color(section.course?.colorIndex ?: 0)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 26.dp, bottom = 6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        section.course?.emoji?.let { Text(it, fontSize = 14.sp) }
        Text(
            (section.course?.name ?: "Notebooks").uppercase(),
            fontFamily = MonoFamily,
            fontSize = 11.sp,
            letterSpacing = 2.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NotebookRow(
    item: ShelfItem,
    courseColor: Color,
    viewModel: LibraryViewModel,
    onOpen: () -> Unit,
    menu: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(courseColor),
        )
        CoverThumb(item, viewModel, width = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(item.lecture.title, style = MaterialTheme.typography.titleSmall)
            Text(
                listOfNotNull(
                    pageCountLabel(item.document?.pageCount),
                    item.lastWrittenAt?.let { relative(it).uppercase() },
                ).joinToString(" · ").ifEmpty { "EMPTY NOTEBOOK" },
                fontFamily = MonoFamily,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        menu()
    }
}

/** Cover art shared between the banner and the rows; also the reader's shared-element source. */
@Composable
private fun CoverThumb(item: ShelfItem, viewModel: LibraryViewModel, width: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .width(width)
            .aspectRatio(0.72f)
            .sharedCover("pdf-${item.lecture.id}")
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
    ) {
        item.document?.let { doc ->
            AsyncImage(
                model = PdfCover(doc.localPath),
                imageLoader = viewModel.imageLoader,
                contentDescription = item.lecture.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun pageCountLabel(count: Int?): String? = count?.let {
    if (it == 1) "1 PAGE" else "$it PAGES"
}

private fun relative(at: Long): String =
    DateUtils.getRelativeTimeSpanString(
        at,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()

@Composable
private fun EmptyShelf(onImport: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            ScallopedGlyph(size = 148.dp, alpha = 0.16f)
            Text("+", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(20.dp))
        Text("Import a PDF to get started", style = MaterialTheme.typography.titleMedium)
        Text(
            "Each PDF becomes a notebook, named after the file",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        GlassButton("Import PDFs", onClick = onImport)
    }
}

/** Soft scalloped badge from graphics-shapes; the shelf's one playful accent. */
@Composable
private fun ScallopedGlyph(size: androidx.compose.ui.unit.Dp, alpha: Float) {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val polygon = RoundedPolygon.star(
            numVerticesPerRadius = 9,
            radius = r,
            innerRadius = r * 0.86f,
            rounding = CornerRounding(r * 0.18f),
            centerX = r,
            centerY = r,
        )
        drawPath(polygon.toPath().asComposePath(), color)
    }
}

@Composable
private fun NamePromptDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    GlassDialog(onDismiss = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GlassTextButton("Cancel", onDismiss)
            Spacer(Modifier.width(8.dp))
            GlassButton("Create", { onConfirm(text) }, enabled = text.isNotBlank())
        }
    }
}
