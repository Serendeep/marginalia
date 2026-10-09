package com.serendeep.marginalia.ai.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.serendeep.marginalia.ai.agent.LIBRARY_SCOPE
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.components.glassBorder
import com.serendeep.marginalia.ui.theme.BodyFamily
import com.serendeep.marginalia.ui.theme.DisplayFamily
import com.serendeep.marginalia.ui.theme.MonoFamily

/** A cited page shown beside the chat. [page] is one-based. */
data class PagePreview(val lectureId: String, val title: String, val page: Int)

private const val PREVIEW_WIDTH_PX = 1100

/** Chat on the left, the page behind the tapped citation on the right. A [pendingQuestion] is sent once, then reported handled. */
@Composable
fun AskScreen(
    pendingQuestion: String?,
    onPendingHandled: () -> Unit,
    onOpenAt: (lectureId: String, page: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    var preview by remember { mutableStateOf<PagePreview?>(null) }
    LaunchedEffect(Unit) { viewModel.enter(LIBRARY_SCOPE) }
    LaunchedEffect(pendingQuestion) {
        if (pendingQuestion != null) {
            viewModel.send(pendingQuestion)
            onPendingHandled()
        }
    }
    val rendered by produceState<Rendered?>(null, preview) {
        value = null
        preview?.let { value = Rendered(viewModel.renderPage(it.lectureId, it.page - 1, PREVIEW_WIDTH_PX)) }
    }
    Row(
        modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ChatPane(
            onSend = viewModel::send,
            onCitation = { title, page ->
                viewModel.resolve(title)?.let { preview = PagePreview(it, viewModel.title(it) ?: title, page) }
            },
            modifier = Modifier.weight(0.6f),
        )
        PreviewPaneContent(
            preview = preview,
            image = rendered?.bitmap,
            loading = preview != null && rendered == null,
            onOpen = { preview?.let { onOpenAt(it.lectureId, it.page - 1) } },
            modifier = Modifier.weight(0.4f),
        )
    }
}

private class Rendered(val bitmap: Bitmap?)

@Composable
fun PreviewPaneContent(
    preview: PagePreview?,
    image: Bitmap?,
    loading: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxSize()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, glassBorder(), shape),
    ) {
        if (preview == null) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Tap a citation to preview the page",
                    fontFamily = BodyFamily,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)) {
            Text(
                "PAGE ${preview.page}",
                fontFamily = MonoFamily,
                fontSize = 10.5.sp,
                letterSpacing = 1.26.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                preview.title,
                fontFamily = DisplayFamily,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
        }
        HorizontalDivider(color = glassBorder())
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                image != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = "${preview.title}, page ${preview.page}",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)),
                    )
                }
                loading -> CircularProgressIndicator(Modifier.padding(24.dp), strokeWidth = 2.dp)
                else -> Text(
                    "Couldn't render this page",
                    fontFamily = BodyFamily,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = glassBorder())
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.End) {
            GlassButton("Open in notebook", onClick = onOpen)
        }
    }
}
