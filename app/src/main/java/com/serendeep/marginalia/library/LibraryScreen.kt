package com.serendeep.marginalia.library

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.serendeep.marginalia.ui.components.GlassMenu
import com.serendeep.marginalia.ui.components.GlassMenuEntry
import com.serendeep.marginalia.ui.components.GlassTextButton
import com.serendeep.marginalia.ui.components.glassTextFieldColors
import com.serendeep.marginalia.ui.theme.CoursePalette
import com.serendeep.marginalia.ui.theme.Danger
import com.serendeep.marginalia.ui.theme.MonoFamily
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun LibraryScreen(
    filter: LibraryFilter = LibraryFilter.All,
    viewModel: LibraryViewModel = hiltViewModel(),
    incomingPdfUri: Uri? = null,
    onIncomingPdfHandled: () -> Unit = {},
    onOpenLecture: (String) -> Unit,
) {
    val shelf by viewModel.shelf.collectAsStateWithLifecycle()
    val data = shelf
    val sections = remember(data, filter) { data?.sections(filter).orEmpty() }
    val moveTargets = remember(data) {
        data?.courses.orEmpty().filter { it.name != LibraryViewModel.UNSORTED_NAME }
    }
    val error by viewModel.error.collectAsStateWithLifecycle()
    var showNewCourse by remember { mutableStateOf(false) }
    var showNewNotebook by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<RowModel?>(null) }
    var deleting by remember { mutableStateOf<RowModel?>(null) }

    LaunchedEffect(incomingPdfUri) {
        incomingPdfUri?.let { uri ->
            viewModel.quickImport(listOf(uri))
            onIncomingPdfHandled()
        }
    }

    // One screen-level launcher for every import flow; a per-card launcher in a
    // lazy grid would unregister when its card is recycled while the system
    // picker is open. The target string encodes where the picked PDFs go.
    // Live today: "quick" (filename-titled lectures, uncategorized). Reserved
    // for later call sites, branches kept: "quick:<courseId>" (import into a
    // specific course) and "replace:<lectureId>" (re-import over an existing
    // lecture's PDF).
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

    fun menuEntries(item: RowModel) = buildList {
        add(GlassMenuEntry("Rename") { renaming = item })
        moveTargets
            .filter { it.id != item.lecture.courseId }
            .forEach { course ->
                add(GlassMenuEntry("Move to ${course.name}") {
                    viewModel.moveLecture(item.lecture.id, course.id)
                })
            }
        add(GlassMenuEntry("Delete notebook") { deleting = item })
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (data != null && data.rows.isEmpty()) {
            EmptyShelf(onImport = { launchImport("quick") })
        } else if (data != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 120.dp),
            ) {
                if (sections.all { it.items.isEmpty() } && filter !is LibraryFilter.All) {
                    item(key = "none", contentType = "none") {
                        Text(
                            "Nothing here yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                }
                sections.forEach { section ->
                    item(key = "hdr:${section.course?.id ?: "unsorted"}", contentType = "header") {
                        CourseHeader(section)
                    }
                    items(section.items, key = { it.lecture.id }, contentType = { "row" }) { item ->
                        LectureRow(
                            row = item,
                            imageLoader = viewModel.imageLoader,
                            meta = remember(item.lecture.id, item.document?.pageCount, item.touchedAt) {
                                listOfNotNull(
                                    item.document?.pageCount?.let { if (it == 1) "1 PAGE" else "$it PAGES" },
                                    relativeTime(item.touchedAt),
                                ).joinToString(" · ")
                            },
                            onClick = { onOpenLecture(item.lecture.id) },
                            chip = ChipKind.Status,
                            menu = {
                                GlassMenu(entries = menuEntries(item)) {
                                    Icon(
                                        Icons.Filled.MoreHoriz,
                                        contentDescription = "More",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }

        GlassMenu(
            entries = listOf(
                GlassMenuEntry("Import PDFs") { launchImport("quick") },
                GlassMenuEntry("New notebook") { showNewNotebook = true },
                GlassMenuEntry("New course") { showNewCourse = true },
            ),
            modifier = Modifier.align(Alignment.BottomEnd).padding(28.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                Text(
                    "Add",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
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

    if (showNewNotebook) {
        NamePromptDialog(
            title = "New notebook",
            onDismiss = { showNewNotebook = false },
            onConfirm = { title ->
                viewModel.createNotebook(title) { lectureId ->
                    showNewNotebook = false
                    onOpenLecture(lectureId)
                }
            },
        )
    }

    renaming?.let { target ->
        NamePromptDialog(
            title = "Rename notebook",
            onDismiss = { renaming = null },
            onConfirm = { name ->
                viewModel.renameLecture(target.lecture.id, name)
                renaming = null
            },
        )
    }

    deleting?.let { target ->
        GlassDialog(onDismiss = { deleting = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Delete ${target.lecture.title}?", style = MaterialTheme.typography.titleMedium)
                Text(
                    "This removes the imported PDF copy and all your notes on it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    GlassTextButton("Cancel", { deleting = null })
                    GlassButton(
                        "Delete",
                        {
                            viewModel.deleteLecture(target.lecture.id)
                            deleting = null
                        },
                        containerColor = Danger,
                    )
                }
            }
        }
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
            (section.course?.name ?: "Notebooks").uppercase(Locale.ROOT),
            fontFamily = MonoFamily,
            fontSize = 11.sp,
            letterSpacing = 2.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyShelf(onImport: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "NO LECTURES YET",
            fontFamily = MonoFamily,
            fontSize = 12.sp,
            letterSpacing = 3.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Box(contentAlignment = Alignment.Center) {
            ScallopedGlyph(size = 148.dp, alpha = 0.16f)
            Text("+", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        }
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
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            colors = glassTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GlassTextButton("Cancel", onDismiss)
            Spacer(Modifier.width(8.dp))
            GlassButton("Create", { onConfirm(text) }, enabled = text.isNotBlank())
        }
    }
}
