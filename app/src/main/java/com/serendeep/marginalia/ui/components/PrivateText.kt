package com.serendeep.marginalia.ui.components

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Text that stays blurred until the eye is tapped, for things like an account email that
 * shouldn't show up in screenshots or screen recordings. Hidden text is also kept out of
 * the accessibility tree.
 */
@Composable
fun PrivateText(
    text: String,
    label: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    prefix: String = "",
) {
    var shown by rememberSaveable { mutableStateOf(false) }
    // Blur needs Android 12; older versions get a mask instead.
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val radius by animateDpAsState(if (shown) 0.dp else 7.dp, tween(160), label = "privateBlur")
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (prefix.isNotEmpty()) Text(prefix, style = style, color = color)
        Text(
            if (shown || canBlur) text else "•".repeat(text.length.coerceIn(6, 18)),
            style = style,
            color = color,
            modifier = Modifier
                .then(if (canBlur && radius > 0.dp) Modifier.blur(radius) else Modifier)
                .clearAndSetSemantics { contentDescription = if (shown) text else "$label hidden" },
        )
        IconButton(onClick = { shown = !shown }, modifier = Modifier.size(36.dp)) {
            Icon(
                if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = if (shown) "Hide $label" else "Show $label",
                tint = color.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
