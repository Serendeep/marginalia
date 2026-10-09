package com.serendeep.marginalia.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serendeep.marginalia.data.SnippetSpan
import com.serendeep.marginalia.highlights.HighlightItem
import com.serendeep.marginalia.ui.theme.BodyFamily
import com.serendeep.marginalia.ui.theme.DimInkDark
import com.serendeep.marginalia.ui.theme.InkDark
import com.serendeep.marginalia.ui.theme.MonoFamily
import com.serendeep.marginalia.ui.theme.Violet

/** [onOpen] receives the notebook and the page to land on, or null for its saved place. */
@Composable
fun SearchScreen(
    onOpen: (lectureId: String, page: Int?) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val results by viewModel.results.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf(viewModel.query.value) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val open: (String, Int?) -> Unit = { id, page ->
        viewModel.commit()
        onOpen(id, page)
    }

    Column(Modifier.fillMaxSize()) {
        SearchField(
            value = text,
            onValueChange = {
                text = it
                viewModel.setQuery(it)
            },
            onSearch = viewModel::commit,
            focus = focus,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
        )
        val ui = results
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (ui == null) {
                if (recent.isNotEmpty()) {
                    item(key = "recent-h", contentType = "header") { SectionLabel("RECENT SEARCHES") }
                    items(recent, key = { "r:$it" }, contentType = { "recent" }) { q ->
                        PlainRow(q) {
                            text = q
                            viewModel.setQuery(q)
                        }
                    }
                } else {
                    item(key = "hint") { Hint("Search titles, PDF text, handwriting and highlights.") }
                }
            } else if (ui.isEmpty) {
                item(key = "none") { Hint("No matches for “${ui.query}”.") }
            } else {
                if (ui.documents.isNotEmpty()) {
                    item(key = "docs-h", contentType = "header") { SectionLabel("DOCUMENTS · ${ui.documents.size}") }
                    items(ui.documents, key = { it.key }, contentType = { "doc" }) { hit ->
                        PlainRow(spans = hit.spans) { open(hit.lectureId, null) }
                    }
                }
                // Your own notes outrank raw PDF text, which can match dozens of pages.
                if (ui.highlights.isNotEmpty()) {
                    item(key = "hl-h", contentType = "header") { SectionLabel("HIGHLIGHTS · ${ui.highlights.size}") }
                    items(ui.highlights, key = { it.key }, contentType = { "highlight" }) { hit ->
                        HighlightItem(
                            row = hit.row,
                            showTitle = true,
                            onClick = { open(hit.row.highlight.lectureId, hit.row.highlight.page) },
                            text = { Text(emphasised(hit.spans), fontFamily = BodyFamily, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 4, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
                if (ui.ink.isNotEmpty()) {
                    item(key = "ink-h", contentType = "header") { SectionLabel("HANDWRITING · ${ui.ink.size}") }
                    items(ui.ink, key = { it.key }, contentType = { "ink" }) { hit ->
                        InkRow(hit) { open(hit.lectureId, hit.page) }
                    }
                }
                if (ui.pages.isNotEmpty()) {
                    item(key = "pages-h", contentType = "header") { SectionLabel("PAGES · ${ui.pages.size}") }
                    items(ui.pages, key = { it.key }, contentType = { "page" }) { hit ->
                        PageRow(hit) { open(hit.lectureId, hit.page) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(Icons.Outlined.Search, null, tint = DimInkDark, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("Search notes, PDFs, highlights", fontFamily = BodyFamily, fontSize = 15.sp, color = DimInkDark)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = BodyFamily, fontSize = 15.sp, color = InkDark),
                cursorBrush = SolidColor(Violet),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Clear",
                tint = DimInkDark,
                modifier = Modifier.size(18.dp).clickable { onValueChange("") },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        letterSpacing = 1.26.sp,
        color = DimInkDark,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        fontFamily = BodyFamily,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 24.dp),
    )
}

@Composable
private fun PlainRow(text: String, onClick: () -> Unit) = PlainRow(listOf(SnippetSpan(text, false)), onClick)

@Composable
private fun PlainRow(spans: List<SnippetSpan>, onClick: () -> Unit) {
    Text(
        emphasised(spans),
        fontFamily = BodyFamily,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun PageRow(hit: PageResult, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(
            emphasised(hit.spans),
            fontFamily = BodyFamily,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "P.${hit.page + 1} · ${hit.title.uppercase(java.util.Locale.ROOT)}",
            fontFamily = MonoFamily,
            fontSize = 10.5.sp,
            letterSpacing = 0.63.sp,
            color = DimInkDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun InkRow(hit: InkResult, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(
            emphasised(hit.spans),
            fontFamily = BodyFamily,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            (hit.page?.let { "P.${it + 1} · " }.orEmpty() + hit.title).uppercase(java.util.Locale.ROOT),
            fontFamily = MonoFamily,
            fontSize = 10.5.sp,
            letterSpacing = 0.63.sp,
            color = DimInkDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun emphasised(spans: List<SnippetSpan>): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        if (span.match) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) { append(span.text) }
        } else {
            append(span.text)
        }
    }
}
