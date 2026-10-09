package com.serendeep.marginalia.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serendeep.marginalia.ai.AiError
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.theme.BodyFamily
import com.serendeep.marginalia.ui.theme.MonoFamily
import com.serendeep.marginalia.ui.theme.Violet
import kotlinx.coroutines.flow.StateFlow

private val CodeBg = Color(0x228B7CF6)
private val ErrorInk = Color(0xFFFF8A80)

/** Reads [text] itself, so a token flush recomposes only this Text. */
@Composable
fun StreamingText(text: StateFlow<String>, streaming: Boolean, modifier: Modifier = Modifier) {
    val value by text.collectAsStateWithLifecycle()
    val ink = MaterialTheme.colorScheme.onSurface
    val rendered = remember(value, streaming) { renderMarkdown(value, streaming) }
    Text(
        rendered,
        fontFamily = BodyFamily,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        color = ink,
        modifier = modifier,
    )
}

fun renderMarkdown(src: String, caret: Boolean = false): AnnotatedString = buildAnnotatedString {
    var previous: MdBlock? = null
    parseMarkdown(src).forEach { block ->
        if (previous != null) append(if (block is MdBlock.Bullet && previous is MdBlock.Bullet) "\n" else "\n\n")
        previous = block
        when (block) {
            is MdBlock.Paragraph -> appendSpans(block.spans)
            is MdBlock.Bullet -> {
                append("${block.marker}  ")
                appendSpans(block.spans)
            }
            is MdBlock.Quote -> {
                withStyle(SpanStyle(color = Violet)) { append("┃  ") }
                appendSpans(block.spans)
            }
            is MdBlock.Code -> withStyle(SpanStyle(fontFamily = MonoFamily, fontSize = 12.5.sp, background = CodeBg)) { append(block.text) }
        }
    }
    if (caret) withStyle(SpanStyle(color = Violet)) { append(" ▍") }
}

private fun AnnotatedString.Builder.appendSpans(spans: List<MdSpan>) {
    spans.forEach { s ->
        val style = SpanStyle(
            fontWeight = if (s.bold) FontWeight.SemiBold else null,
            fontFamily = if (s.code) MonoFamily else null,
            fontSize = if (s.code) 12.5.sp else TextUnit.Unspecified,
            background = if (s.code) CodeBg else Color.Unspecified,
        )
        withStyle(style) { append(s.text) }
    }
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
