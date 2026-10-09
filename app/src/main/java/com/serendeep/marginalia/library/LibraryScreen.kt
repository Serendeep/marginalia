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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.serendeep.marginalia.data.TagEntity
import com.serendeep.marginalia.ui.theme.OnViolet
import com.serendeep.marginalia.ui.theme.Violet
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
import com.serendeep.marginalia.ui.components.CenterPanel
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
    val canSort by viewModel.canSort.collectAsStateWithLifecycle()
    val sorting by viewModel.sorting.collectAsStateWithLifecycle()
    var showNewCourse by remember { mutableStateOf(false) }
    var showNewNotebook by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<RowModel?>(null) }
    var deleting by remember { mutableStateOf<RowModel?>(null) }
    var taggingId by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(Unit) {
        viewModel.citation.collect { citation ->
            clipboard.setText(AnnotatedString(citation.text))
            snackbar.showSnackbar(
                when {
                    citation.fetched -> "Citation copied"
                    citation.hasIdentifier -> "Offline — copied title only"
                    else -> "No DOI or arXiv ID — copied title only"
                },
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.sorted.collect { batch ->
            val message = if (batch.size == 1) {
                "Sorted ‘${batch[0].title}’ into ${batch[0].courseName}"
            } else {
                "Sorted ${batch.size} PDFs into ${batch.map { it.courseId }.distinct().size} courses"
            }
            val outcome = snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Long)
            if (outcome == SnackbarResult.ActionPerformed) viewModel.undoSort(batch)
        }
    }

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
        add(GlassMenuEntry("Tags…") { taggingId = item.lecture.id })
        add(GlassMenuEntry("Copy citation") { viewModel.copyCitation(item.lecture.id) })
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
                        CourseHeader(
                            section,
                            sortLabel = when {
                                section.course != null || !canSort || section.items.isEmpty() -> null
                                else -> sorting?.let { (done, total) -> "Sorting $done of $total…" } ?: "Sort with AI"
                            },
                            onSort = if (sorting == null) viewModel::sortUnsorted else null,
                        )
                    }
                    items(section.items, key = { it.lecture.id }, contentType = { "row" }) { item ->
                        LectureRow(
                            row = item,
                            imageLoader = viewModel.imageLoader,
                            meta = remember(item.lecture.id, item.document?.pageCount, item.touchedAt, item.lecture.arxivId, item.lecture.doi) {
                                listOfNotNull(
                                    item.document?.pageCount?.let { if (it == 1) "1 PAGE" else "$it PAGES" },
                                    relativeTime(item.touchedAt),
                                    item.lecture.arxivId?.let { "ARXIV $it" } ?: item.lecture.doi?.let { "DOI $it" },
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

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(24.dp))

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

    taggingId?.let { id ->
        val row = data?.rows?.firstOrNull { it.lecture.id == id }
        if (row == null) {
            taggingId = null
        } else {
            TagsDialog(
                allTags = data?.tags.orEmpty(),
                selected = row.tags.map { it.id }.toSet(),
                onToggle = { tagId, on -> viewModel.setTagged(id, tagId, on) },
                onCreate = { viewModel.addTag(id, it) },
                onDismiss = { taggingId = null },
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
        CenterPanel(
            onDismiss = { deleting = null },
            title = "Delete ${target.lecture.title}?",
            eyebrow = "Confirm",
            footer = {
                GlassTextButton("Cancel", { deleting = null })
                GlassButton(
                    "Delete",
                    {
                        viewModel.deleteLecture(target.lecture.id)
                        deleting = null
                    },
                    containerColor = Danger,
                )
            },
        ) {
            Text(
                "This removes the imported PDF copy and all your notes on it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CourseHeader(section: ShelfSection, sortLabel: String?, onSort: (() -> Unit)?) {
    val color = CoursePalette.color(section.course?.colorIndex ?: 0)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 6.dp),
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
        if (sortLabel != null) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onSort?.invoke() }, enabled = onSort != null) {
                Text(sortLabel, fontFamily = MonoFamily, fontSize = 11.sp, letterSpacing = 0.6.sp)
            }
        }
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
    CenterPanel(
        onDismiss = onDismiss,
        title = title,
        eyebrow = "Library",
        footer = {
            GlassTextButton("Cancel", onDismiss)
            GlassButton("Create", { onConfirm(text) }, enabled = text.isNotBlank())
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            colors = glassTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsDialog(
    allTags: List<TagEntity>,
    selected: Set<String>,
    onToggle: (tagId: String, on: Boolean) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    CenterPanel(
        onDismiss = onDismiss,
        title = "Tags",
        eyebrow = "Library",
        footer = {
            GlassTextButton("Done", onDismiss)
            GlassButton("Add", {
                onCreate(text)
                text = ""
            }, enabled = text.isNotBlank())
        },
    ) {
        if (allTags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                allTags.forEach { tag ->
                    val on = tag.id in selected
                    Text(
                        tag.name,
                        fontSize = 12.sp,
                        color = if (on) OnViolet else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (on) Violet else Color.Transparent)
                            .border(1.dp, if (on) Violet else MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                            .clickable { onToggle(tag.id, !on) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            placeholder = { Text("New tag") },
            colors = glassTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
