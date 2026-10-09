package com.serendeep.marginalia.ai.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serendeep.marginalia.ai.AiError
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.theme.BodyFamily
import com.serendeep.marginalia.ui.theme.MonoFamily
import com.serendeep.marginalia.ui.theme.Violet

private val CodeBg = Color(0x228B7CF6)
private val ErrorInk = Color(0xFFFF8A80)

@Composable
internal fun ThinkingLabel(modifier: Modifier = Modifier) {
    val alpha by rememberInfiniteTransition(label = "thinking").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "thinking-alpha",
    )
    Text(
        "Thinking…",
        fontFamily = MonoFamily,
        fontSize = 12.sp,
        letterSpacing = 0.6.sp,
        color = Violet,
        modifier = modifier.alpha(alpha),
    )
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
