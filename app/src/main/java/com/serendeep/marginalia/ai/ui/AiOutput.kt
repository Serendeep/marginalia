package com.serendeep.marginalia.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serendeep.marginalia.ai.AiError
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.components.WebPopup
import com.serendeep.marginalia.ui.theme.BodyFamily
import com.serendeep.marginalia.ui.theme.MonoFamily
import com.serendeep.marginalia.ui.theme.Violet
import kotlinx.coroutines.flow.StateFlow

private val CodeBg = Color(0x228B7CF6)
private val ErrorInk = Color(0xFFFF8A80)

/** One streamed answer, sized to its content. Reads [text] itself, so a token flush recomposes only this view. */
@Composable
fun StreamingText(
    text: StateFlow<String>,
    streaming: Boolean,
    modifier: Modifier = Modifier,
    onCitation: (title: String, page: Int) -> Unit = { _, _ -> },
) {
    val value by text.collectAsStateWithLifecycle()
    var popup by remember { mutableStateOf<String?>(null) }
    AnswerView(
        messages = remember(value) { listOf(AnswerMessage("answer", AnswerRole.ASSISTANT, value)) },
        streaming = streaming,
        onCitation = onCitation,
        onLink = { popup = it },
        modifier = modifier.fillMaxWidth(),
        wrapContent = true,
    )
    popup?.let { WebPopup(it) { popup = null } }
}

@Composable
fun AiErrorBlock(error: AiError, onSetup: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(error.message, fontFamily = BodyFamily, fontSize = 13.sp, lineHeight = 19.sp, color = ErrorInk)
        if (error.needsSetup()) GlassButton("Open AI settings", onClick = onSetup)
    }
}

@Composable
fun AiChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, enabled: Boolean = true) {
    val shape = RoundedCornerShape(18.dp)
    val ink = if (selected) Violet else MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .clip(shape)
            .background(if (selected) CodeBg else Color.Transparent)
            .border(1.dp, if (selected) Violet else MaterialTheme.colorScheme.outline, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            fontFamily = BodyFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = if (enabled) ink else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
fun MonoNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontFamily = MonoFamily,
        fontSize = 10.5.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 2.dp),
    )
}
