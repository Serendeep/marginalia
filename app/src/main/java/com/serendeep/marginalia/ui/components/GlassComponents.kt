package com.serendeep.marginalia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.core.Dialog
import com.composables.core.DialogPanel
import com.composables.core.Menu
import com.composables.core.MenuButton
import com.composables.core.MenuContent
import com.composables.core.MenuItem
import com.composables.core.Scrim
import com.composables.core.rememberDialogState
import com.composables.core.rememberMenuState
import com.serendeep.marginalia.ui.theme.GlassBorderDark
import com.serendeep.marginalia.ui.theme.GlassBorderLight
import com.serendeep.marginalia.ui.theme.LocalDarkTheme

// The one place surfaces are styled. Screens compose these; they never touch
// raw dialogs, menus, or button styling directly.

private val PanelShape = RoundedCornerShape(28.dp)

@Composable
fun glassBorder(): Color = if (LocalDarkTheme.current) GlassBorderDark else GlassBorderLight

/**
 * Modal panel on a dimmed scrim. Behavior (focus, back, outside-tap) comes
 * from the unstyled primitive; the shell is ours.
 */
@Composable
fun GlassDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberDialogState(initiallyVisible = true)
    LaunchedEffect(state.visible) {
        if (!state.visible) onDismiss()
    }
    Dialog(state = state) {
        Scrim(scrimColor = Color.Black.copy(alpha = 0.55f))
        DialogPanel(
            Modifier
                .widthIn(min = 320.dp, max = 480.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .clip(PanelShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, glassBorder(), PanelShape)
                .padding(24.dp),
        ) {
            Column(content = { content() })
        }
    }
}

@Composable
fun glassTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** Primary action: the periwinkle pill. One height, one shape, everywhere. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color? = null,
) {
    val background = containerColor ?: MaterialTheme.colorScheme.primary
    val foreground = if (containerColor == null) {
        MaterialTheme.colorScheme.onPrimary
    } else if (background.luminance() > 0.5f) {
        Color.Black
    } else {
        Color.White
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = background, contentColor = foreground),
    ) { Text(text, fontWeight = FontWeight.Medium) }
}

/** Quiet action: text-only, secondary ink. */
@Composable
fun GlassTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

data class GlassMenuEntry(val label: String, val onClick: () -> Unit)

/**
 * Anchored menu on the unstyled primitive: it owns expansion, dismissal, and
 * positioning; [trigger] is plain content, never separately clickable.
 */
@Composable
fun GlassMenu(
    entries: List<GlassMenuEntry>,
    modifier: Modifier = Modifier,
    trigger: @Composable () -> Unit,
) {
    val menuShape = RoundedCornerShape(16.dp)
    Menu(state = rememberMenuState(), modifier = modifier) {
        MenuButton { trigger() }
        MenuContent(
            Modifier
                .clip(menuShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, glassBorder(), menuShape)
                .padding(vertical = 4.dp),
        ) {
            entries.forEach { entry ->
                MenuItem(onClick = entry.onClick) {
                    Text(
                        entry.label,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/** Tracked uppercase micro-label with the margin tick; section headers, eyebrows. */
@Composable
fun MarginLabel(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(3.dp, 12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text.uppercase(java.util.Locale.ROOT),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else tint,
        )
    }
}
