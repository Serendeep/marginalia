package com.serendeep.marginalia.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.serendeep.marginalia.ui.theme.MonoFamily
import kotlinx.coroutines.launch
import java.util.Locale

enum class PanelSide { START, END }

private val PanelRadius = 20.dp
private const val ScrimAlpha = 0.45f

/** Full-height panel sliding in from an edge; browsable or long content with a pinned footer. */
@Composable
fun SidePanel(
    onDismiss: () -> Unit,
    title: String,
    eyebrow: String? = null,
    side: PanelSide = PanelSide.END,
    width: Dp = 440.dp,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val end = side == PanelSide.END
    val shape = if (end) {
        RoundedCornerShape(topStart = PanelRadius, bottomStart = PanelRadius)
    } else {
        RoundedCornerShape(topEnd = PanelRadius, bottomEnd = PanelRadius)
    }
    PanelHost(
        onDismiss = onDismiss,
        enter = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
    ) { progress, close ->
        Box(
            Modifier
                .align(if (end) Alignment.CenterEnd else Alignment.CenterStart)
                .fillMaxHeight()
                .widthIn(max = width)
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = (1f - progress()) * size.width * if (end) 1f else -1f
                }
                .panelSurface(shape)
                .consumeTaps(),
        ) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))) {
                PanelHeader(eyebrow, title, close)
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
                if (footer != null) PanelFooter(footer)
            }
        }
    }
}

/** Centered card for short forms and confirmations. */
@Composable
fun CenterPanel(
    onDismiss: () -> Unit,
    title: String,
    eyebrow: String? = null,
    maxWidth: Dp = 560.dp,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(PanelRadius)
    PanelHost(
        onDismiss = onDismiss,
        enter = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium),
    ) { progress, close ->
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .widthIn(min = 360.dp, max = maxWidth)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = progress()
                        alpha = p.coerceIn(0f, 1f)
                        scaleX = 0.94f + 0.06f * p
                        scaleY = 0.94f + 0.06f * p
                    }
                    .panelSurface(shape)
                    .consumeTaps(),
            ) {
                PanelHeader(eyebrow, title, close)
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
                if (footer != null) PanelFooter(footer)
            }
        }
    }
}

/** Mono eyebrow, display-face title and a close button, over a hairline. */
@Composable
fun PanelHeader(
    eyebrow: String?,
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = if (leading == null) 24.dp else 8.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(
                    eyebrow.uppercase(Locale.ROOT),
                    fontFamily = MonoFamily,
                    fontSize = 10.5.sp,
                    letterSpacing = 1.26.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        actions()
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(color = glassBorder())
}

/** Hairline between sections of a panel. */
@Composable
fun PanelDivider() = HorizontalDivider(color = glassBorder())

/** Mono uppercase section label above a group of rows. */
@Composable
fun PanelSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label.uppercase(Locale.ROOT),
            fontFamily = MonoFamily,
            fontSize = 10.5.sp,
            letterSpacing = 1.26.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun PanelFooter(footer: @Composable RowScope.() -> Unit) {
    HorizontalDivider(color = glassBorder())
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        content = footer,
    )
}

@Composable
private fun Modifier.panelSurface(shape: Shape): Modifier = this
    .clip(shape)
    .background(MaterialTheme.colorScheme.surface)
    .border(1.dp, glassBorder(), shape)

@Composable
private fun Modifier.consumeTaps(): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}

/**
 * Edge-to-edge dialog window that owns the scrim, enter spring and exit fade.
 * [content] gets the animation progress (0..1) and a close that plays the exit first.
 */
@Composable
private fun PanelHost(
    onDismiss: () -> Unit,
    enter: AnimationSpec<Float>,
    content: @Composable BoxScope.(progress: () -> Float, close: () -> Unit) -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val latestDismiss by rememberUpdatedState(onDismiss)
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { progress.animateTo(1f, enter) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                progress.animateTo(0f, tween(180))
                latestDismiss()
            }
        }
    }
    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.apply {
            setDimAmount(0f)
            setWindowAnimations(0)
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = ScrimAlpha * progress.value.coerceIn(0f, 1f)))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close),
        ) {
            content({ progress.value }, close)
        }
    }
}
