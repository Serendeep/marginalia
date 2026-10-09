package com.serendeep.marginalia.highlights

import com.serendeep.marginalia.ui.theme.marginalia
import com.serendeep.marginalia.ui.components.GlassDropdownMenu
import com.serendeep.marginalia.ui.components.GlassMenuItem
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serendeep.marginalia.cards.HighlightCardSheet
import com.serendeep.marginalia.data.HighlightRow
import com.serendeep.marginalia.ui.theme.BodyFamily
import com.serendeep.marginalia.ui.theme.DisplayFamily
import com.serendeep.marginalia.ui.theme.MonoFamily
import kotlinx.coroutines.launch

@Composable
fun HighlightsScreen(
    onOpen: (lectureId: String, page: Int) -> Unit,
    viewModel: HighlightsViewModel = hiltViewModel(),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var cardFor by remember { mutableStateOf<HighlightRow?>(null) }
    val loaded = groups ?: return
    if (loaded.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No highlights yet", fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text(
                    "Sweep the highlighter across PDF text and it lands here.",
                    fontFamily = BodyFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    cardFor?.let { HighlightCardSheet(it, onDismiss = { cardFor = null }) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        loaded.forEach { group ->
            item(key = "g:${group.lectureId}", contentType = "header") {
                GroupHeader(group, onExport = {
                    scope.launch {
                        val markdown = viewModel.markdown(group)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/markdown"
                            putExtra(Intent.EXTRA_SUBJECT, group.title)
                            putExtra(Intent.EXTRA_TEXT, markdown)
                        }
                        runCatching { context.startActivity(Intent.createChooser(send, "Export Markdown")) }
                    }
                })
            }
            items(group.items, key = { it.highlight.id }, contentType = { "highlight" }) { row ->
                HighlightItem(
                    row,
                    onClick = { onOpen(row.highlight.lectureId, row.highlight.page) },
                    onMakeCard = { cardFor = row },
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(group: HighlightGroup, onExport: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                group.title,
                fontFamily = DisplayFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                "  ${group.items.size}",
                fontFamily = MonoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.marginalia.dimInk,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Document actions", tint = MaterialTheme.marginalia.dimInk)
            }
            GlassDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                GlassMenuItem(
                    "Export Markdown",
                    onClick = {
                        menuOpen = false
                        onExport()
                    },
                )
            }
        }
    }
}
