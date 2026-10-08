package com.serendeep.marginalia.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.serendeep.marginalia.data.LectureEntity
import com.serendeep.marginalia.data.HighlightRow
import com.serendeep.marginalia.study.clozeFront
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.components.GlassTextButton
import com.serendeep.marginalia.ui.components.MarginLabel
import com.serendeep.marginalia.ui.components.glassBorder
import com.serendeep.marginalia.ui.components.glassTextFieldColors
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

/**
 * Bottom sheet for writing a card. With [frontImagePath] the front is that picture;
 * otherwise it is an editable text field. [lectures] non-null adds a lecture picker.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CardEditorSheet(
    imageLoader: ImageLoader,
    onDismiss: () -> Unit,
    onSave: (front: String, back: String, lectureId: String?) -> Unit,
    modifier: Modifier = Modifier,
    frontImagePath: String? = null,
    initialFront: String = "",
    initialBack: String = "",
    lectures: List<LectureEntity>? = null,
) {
    var front by remember { mutableStateOf(initialFront) }
    var back by remember { mutableStateOf(initialBack) }
    var lectureId by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canSave = back.isNotBlank() && (frontImagePath != null || front.isNotBlank())
    // With the keyboard up a tablet in landscape has little height left, so the preview shrinks
    // and the sheet scrolls rather than squeezing the fields and hiding Save.
    val imeOpen = WindowInsets.isImeVisible
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MarginLabel("Make card")
            if (frontImagePath != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
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
                        modifier = Modifier.heightIn(max = if (imeOpen) 96.dp else 220.dp),
                    )
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
            }
            OutlinedTextField(
                value = back,
                onValueChange = { back = it },
                label = { Text("Answer") },
                minLines = 3,
                maxLines = 6,
                colors = glassTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (lectures != null) LecturePicker(lectures, lectureId) { lectureId = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                GlassTextButton("Cancel", onClick = onDismiss)
                Spacer(Modifier.width(8.dp))
                GlassButton("Save", enabled = canSave, onClick = { onSave(front.trim(), back.trim(), lectureId) })
            }
            Spacer(Modifier.padding(bottom = 8.dp))
        }
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
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("None") }, onClick = { open = false; onSelect(null) })
            lectures.forEach { lecture ->
                DropdownMenuItem(text = { Text(lecture.title) }, onClick = { open = false; onSelect(lecture.id) })
            }
        }
    }
}

/** Sheet for turning a highlight into a cloze card. */
@Composable
fun HighlightCardSheet(row: HighlightRow, onDismiss: () -> Unit, viewModel: CardsViewModel = hiltViewModel()) {
    val text = row.highlight.text
    CardEditorSheet(
        imageLoader = viewModel.imageLoader,
        onDismiss = onDismiss,
        initialFront = remember(text) { clozeFront(text) },
        initialBack = text,
        onSave = { front, back, _ ->
            viewModel.saveHighlightCard(row, front, back)
            onDismiss()
        },
    )
}

/** Sheet for a free-form card, with an optional lecture. */
@Composable
fun TypedCardSheet(onDismiss: () -> Unit, viewModel: CardsViewModel = hiltViewModel()) {
    val lectures by viewModel.lectures.collectAsStateWithLifecycle()
    CardEditorSheet(
        imageLoader = viewModel.imageLoader,
        onDismiss = onDismiss,
        lectures = lectures,
        onSave = { front, back, lectureId ->
            viewModel.saveTyped(front, back, lectureId)
            onDismiss()
        },
    )
}
