package com.serendeep.marginalia.library

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.serendeep.marginalia.ui.components.GlassMenu
import com.serendeep.marginalia.ui.components.GlassMenuEntry
import com.serendeep.marginalia.ui.components.GlassDialog
import com.serendeep.marginalia.ui.components.GlassTextButton
import com.serendeep.marginalia.ui.components.MarginLabel
import com.serendeep.marginalia.ui.theme.DisplayFamily
import com.serendeep.marginalia.ui.theme.GlassSmokeDark
import com.serendeep.marginalia.ui.theme.GlassTintDark
import com.serendeep.marginalia.ui.theme.GlassTintLight
import com.serendeep.marginalia.ui.theme.LocalDarkTheme
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
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

    val hazeState = remember { HazeState() }
    val dark = LocalDarkTheme.current

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (shelf.isEmpty) {
            EmptyShelf(onImport = { launchImport("quick") })
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 188.dp),
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                contentPadding = PaddingValues(
                    start = 24.dp,
                    end = 24.dp,
                    top = 100.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                    bottom = 32.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                shelf.hero?.let { hero ->
                    item(key = "hero", span = { GridItemSpan(minOf(2, maxLineSpan)) }) {
                        ContinueCard(
                            item = hero,
                            viewModel = viewModel,
                            onOpen = { onOpenLecture(hero.lecture.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                shelf.sections.forEach { section ->
                    item(key = "hdr:${section.course?.id ?: "unsorted"}", span = { GridItemSpan(maxLineSpan) }) {
                        SectionLabel(
                            title = section.course?.name ?: "Notebooks",
                            onAddPdf = {
                                launchImport(section.course?.let { "quick:${it.id}" } ?: "quick")
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    items(section.items, key = { it.lecture.id }) { item ->
                        CoverCard(
                            item = item,
                            viewModel = viewModel,
                            onOpen = { onOpenLecture(item.lecture.id) },
                            onReplace = { launchImport("replace:${item.lecture.id}") },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }

        // Frosted header; the shelf scrolls beneath it.
        Row(
            Modifier
                .fillMaxWidth()
                .hazeEffect(
                    state = hazeState,
                    style = HazeStyle(
                        backgroundColor = MaterialTheme.colorScheme.background,
                        // White covers scroll under this bar; smoke keeps the
                        // title legible when they do.
                        tint = HazeTint(if (dark) GlassSmokeDark else GlassTintLight),
                        blurRadius = 24.dp,
                        noiseFactor = 0.02f,
                    ),
                ) {
                    inputScale = HazeInputScale.Fixed(0.5f)
                }
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Library",
                fontFamily = DisplayFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassTextButton("New course", onClick = { showNewCourse = true })
                Spacer(Modifier.width(12.dp))
                GlassButton("Import PDFs", onClick = { launchImport("quick") })
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
        NamePromptDialog(
            title = "New course",
            onDismiss = { showNewCourse = false },
            onConfirm = {
                viewModel.createCourse(it, colorIndex = 0, emoji = null)
                showNewCourse = false
            },
        )
    }
}

/** The margin rule: Marginalia's namesake accent, marking every label. */
@Composable
private fun MarginTick(height: androidx.compose.ui.unit.Dp = 12.dp) {
    Box(
        Modifier
            .width(3.dp)
            .height(height)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.primary),
    )
}

@Composable
private fun SectionLabel(title: String, onAddPdf: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MarginLabel(title)
        GlassTextButton("Add PDF", onClick = onAddPdf)
    }
}

/** Wide resume card: the one thing the screen is for. */
@Composable
private fun ContinueCard(
    item: ShelfItem,
    viewModel: LibraryViewModel,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "heroPress",
    )
    val shape = RoundedCornerShape(24.dp)

    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
            .padding(14.dp),
    ) {
        Box(
            Modifier
                .width(132.dp)
                .aspectRatio(0.72f)
                .sharedCover("pdf-${item.lecture.id}")
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            item.document?.let { doc ->
                AsyncImage(
                    model = PdfCover(doc.localPath),
                    imageLoader = viewModel.imageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.align(Alignment.CenterVertically)) {
            MarginLabel("Continue", tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(
                item.lecture.title,
                fontFamily = DisplayFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                listOfNotNull(
                    item.document?.let { "${it.pageCount} pages" },
                    item.lastWrittenAt?.let { "written ${relative(it)}" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CoverCard(
    item: ShelfItem,
    viewModel: LibraryViewModel,
    onOpen: () -> Unit,
    onReplace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "coverPress",
    )
    val coverShape = RoundedCornerShape(18.dp)

    Column(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .sharedCover("pdf-${item.lecture.id}")
                .clip(coverShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, coverShape),
        ) {
            val document = item.document
            if (document != null) {
                AsyncImage(
                    model = PdfCover(document.localPath),
                    imageLoader = viewModel.imageLoader,
                    contentDescription = item.lecture.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ScallopedGlyph(size = 52.dp, alpha = 0.3f)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No PDF yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            GlassMenu(
                entries = listOf(
                    GlassMenuEntry(if (item.document == null) "Import PDF" else "Replace PDF", onReplace),
                ),
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.MoreHoriz,
                        contentDescription = "More",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            item.lecture.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 20.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            listOfNotNull(
                item.document?.let { "${it.pageCount} pages" },
                item.lastWrittenAt?.let { relative(it) },
            ).joinToString(" · ").ifEmpty { "Empty notebook" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
