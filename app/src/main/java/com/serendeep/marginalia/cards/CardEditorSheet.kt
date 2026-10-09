package com.serendeep.marginalia.cards

import com.serendeep.marginalia.ui.components.GlassDropdownMenu
import com.serendeep.marginalia.ui.components.GlassMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.ink.strokes.Stroke
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.serendeep.marginalia.data.LectureEntity
import com.serendeep.marginalia.data.HighlightRow
import com.serendeep.marginalia.study.clozeFront
import com.serendeep.marginalia.ui.components.CenterPanel
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.components.GlassTextButton
import com.serendeep.marginalia.ui.components.glassBorder
import com.serendeep.marginalia.ui.components.glassTextFieldColors
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

/**
 * Panel for writing a card. With [frontImagePath] the front is that picture;
 * otherwise it is an editable text field. [lectures] non-null adds a lecture picker. [onAskAi] adds an "Ask AI" action beside the image.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardEditorSheet(
    imageLoader: ImageLoader,
    onDismiss: () -> Unit,
    onSave: (front: String, back: String, lectureId: String?, backInk: ByteArray?) -> Unit,
    modifier: Modifier = Modifier,
    frontImagePath: String? = null,
    initialFront: String = "",
    initialBack: String = "",
    lectures: List<LectureEntity>? = null,
    onAskAi: (() -> Unit)? = null,
) {
    var front by remember { mutableStateOf(initialFront) }
    var back by remember { mutableStateOf(initialBack) }
    var lectureId by remember { mutableStateOf<String?>(null) }
    var writing by remember { mutableStateOf(false) }
    var ink by remember { mutableStateOf(emptyList<Stroke>()) }
    val canSave = (back.isNotBlank() || ink.isNotEmpty()) && (frontImagePath != null || front.isNotBlank())
    // With the keyboard up a tablet in landscape has little height left, so the preview shrinks
    // and the panel scrolls rather than squeezing the fields and hiding Save.
    val imeOpen = WindowInsets.isImeVisible
    val answer = @Composable { fieldModifier: Modifier ->
        Column(fieldModifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassTextButton(if (writing) "Type" else "Write", onClick = { writing = !writing })
                if (writing) {
                    GlassTextButton("Undo", enabled = ink.isNotEmpty(), onClick = { ink = ink.dropLast(1) })
                    GlassTextButton("Clear", enabled = ink.isNotEmpty(), onClick = { ink = emptyList() })
                }
            }
            if (writing) {
                InkAnswerPad(ink, onFinished = { ink = ink + it })
            } else {
                OutlinedTextField(
                    value = back,
                    onValueChange = { back = it },
                    label = { Text("Answer") },
                    minLines = 3,
                    maxLines = 6,
                    colors = glassTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    CenterPanel(
        onDismiss = onDismiss,
        title = "Make card",
        eyebrow = "Cards",
        maxWidth = if (frontImagePath != null) 760.dp else 600.dp,
        footer = {
            GlassTextButton("Cancel", onClick = onDismiss)
            if (onAskAi != null && frontImagePath != null) GlassTextButton("Ask AI", onClick = onAskAi)
            GlassButton("Save", enabled = canSave, onClick = { onSave(front.trim(), back.trim(), lectureId, encodeInkAnswer(ink)) })
        },
    ) {
        if (frontImagePath != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, glassBorder(), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = File(frontImagePath),
                        imageLoader = imageLoader,
                        contentDescription = "Card front",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.heightIn(max = if (imeOpen) 120.dp else 260.dp),
                    )
                }
                answer(Modifier.weight(1f))
            }
        } else {
            OutlinedTextField(
                value = front,
                onValueChange = { front = it },
                label = { Text("Front") },
                minLines = 2,
                maxLines = 5,
                colors = glassTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            answer(Modifier.fillMaxWidth())
        }
        if (lectures != null) LecturePicker(lectures, lectureId) { lectureId = it }
    }
}

@Composable
private fun LecturePicker(lectures: List<LectureEntity>, selected: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            "Lecture: ${lectures.firstOrNull { it.id == selected }?.title ?: "None"}  ▾",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(vertical = 6.dp),
        )
        GlassDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            GlassMenuItem("None", onClick = { open = false; onSelect(null) })
            lectures.forEach { lecture ->
                GlassMenuItem(lecture.title, onClick = { open = false; onSelect(lecture.id) })
            }
        }
    }
}

/** Panel for turning a highlight into a cloze card. */
@Composable
fun HighlightCardSheet(row: HighlightRow, onDismiss: () -> Unit, viewModel: CardsViewModel = hiltViewModel()) {
    val text = row.highlight.text
    CardEditorSheet(
        imageLoader = viewModel.imageLoader,
        onDismiss = onDismiss,
        initialFront = remember(text) { clozeFront(text) },
        initialBack = text,
        onSave = { front, back, _, ink ->
            viewModel.saveHighlightCard(row, front, back, ink)
            onDismiss()
        },
    )
}

/** Panel for a free-form card, with an optional lecture. */
@Composable
fun TypedCardSheet(onDismiss: () -> Unit, viewModel: CardsViewModel = hiltViewModel()) {
    val lectures by viewModel.lectures.collectAsStateWithLifecycle()
    CardEditorSheet(
        imageLoader = viewModel.imageLoader,
        onDismiss = onDismiss,
        lectures = lectures,
        onSave = { front, back, lectureId, ink ->
            viewModel.saveTyped(front, back, lectureId, ink)
            onDismiss()
        },
    )
}
