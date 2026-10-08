package com.serendeep.marginalia.handwriting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.components.GlassTextButton
import com.serendeep.marginalia.ui.components.MarginLabel
import com.serendeep.marginalia.ui.components.glassTextFieldColors

const val MODEL_DOWNLOADING = "Downloading handwriting model… (one-time, ~20 MB)"

/** Status line for the recognition model, or null when there is nothing to say. */
fun modelStatus(state: ModelState): String? = when (state) {
    ModelState.Downloading -> MODEL_DOWNLOADING
    is ModelState.Unavailable -> state.reason
    else -> null
}

/** Editable result of reading a selection as text; [text] is null while recognition runs. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HandwritingTextSheet(
    text: String?,
    modelState: ModelState,
    onCard: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var edited by remember(text) { mutableStateOf(text.orEmpty()) }
    val clipboard = LocalClipboardManager.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
            MarginLabel("Handwriting to text")
            val status = modelStatus(modelState) ?: if (text == null) "Reading handwriting…" else null
            if (status != null) {
                Text(status, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (text != null) {
                OutlinedTextField(
                    value = edited,
                    onValueChange = { edited = it },
                    label = { Text("Text") },
                    placeholder = { Text("Nothing could be read") },
                    minLines = 3,
                    maxLines = 8,
                    colors = glassTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val ready = text != null && edited.isNotBlank()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                GlassTextButton("Close", onClick = onDismiss)
                Spacer(Modifier.width(8.dp))
                GlassTextButton("Copy", enabled = ready, onClick = { clipboard.setText(AnnotatedString(edited.trim())) })
                Spacer(Modifier.width(8.dp))
                GlassButton("Make card", enabled = ready, onClick = { onCard(edited.trim()) })
            }
            Spacer(Modifier.padding(bottom = 8.dp))
        }
    }
}
