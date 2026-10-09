package com.serendeep.marginalia.handwriting

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.components.GlassTextButton
import com.serendeep.marginalia.ui.components.SidePanel
import com.serendeep.marginalia.ui.components.glassTextFieldColors

const val MODEL_DOWNLOADING = "Downloading handwriting model… (one-time, ~20 MB)"

/** Status line for the recognition model, or null when there is nothing to say. */
fun modelStatus(state: ModelState): String? = when (state) {
    ModelState.Downloading -> MODEL_DOWNLOADING
    is ModelState.Unavailable -> state.reason
    else -> null
}

/** Editable result of reading a selection as text; [text] is null while recognition runs. */
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
    val ready = text != null && edited.isNotBlank()
    SidePanel(
        onDismiss = onDismiss,
        title = "Handwriting to text",
        eyebrow = "Ink",
        footer = {
            GlassTextButton("Close", onClick = onDismiss)
            GlassTextButton("Copy", enabled = ready, onClick = { clipboard.setText(AnnotatedString(edited.trim())) })
            GlassButton("Make card", enabled = ready, onClick = { onCard(edited.trim()) })
        },
    ) {
        val status = modelStatus(modelState) ?: if (text == null) "Reading handwriting\u2026" else null
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
    }
}
