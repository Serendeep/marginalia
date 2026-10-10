package com.serendeep.marginalia.review

import com.serendeep.marginalia.ui.theme.marginalia
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.serendeep.marginalia.cards.InkAnswerView
import com.serendeep.marginalia.cards.TypedCardSheet
import com.serendeep.marginalia.study.Grade
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.theme.BodyFamily
import com.serendeep.marginalia.ui.theme.DisplayFamily
import com.serendeep.marginalia.ui.theme.MonoFamily
import java.io.File
import java.util.Locale

private const val FLIP_MS = 180

@Composable
fun ReviewScreen(
    onOpen: (lectureId: String, page: Int) -> Unit,
    onDone: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var newCard by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.start() }
    DisposableEffect(Unit) { onDispose { viewModel.endSession() } }
    if (newCard) {
        TypedCardSheet(onDismiss = {
            newCard = false
            viewModel.reloadIfEmpty()
        })
    }
    Box(Modifier.fillMaxSize()) {
        when (val state = ui) {
            ReviewUi.Loading -> Unit
            ReviewUi.Empty -> EmptyReview(onNewCard = { newCard = true })
            is ReviewUi.Card -> CardSession(
                state = state,
                imageLoader = viewModel.imageLoader,
                onGrade = viewModel::grade,
                onOpen = onOpen,
                onNewCard = { newCard = true },
            )
            is ReviewUi.Summary -> SummaryView(state, onDone)
        }
    }
}

@Composable
private fun EmptyReview(onNewCard: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Nothing due.", fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
        Text(
            "Lasso anything in a notebook to make a card.",
            fontFamily = BodyFamily,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
        )
        GlassButton("+ New card", onClick = onNewCard)
    }
}

@Composable
private fun CardSession(
    state: ReviewUi.Card,
    imageLoader: ImageLoader,
    onGrade: (Grade) -> Unit,
    onOpen: (String, Int) -> Unit,
    onNewCard: () -> Unit,
) {
    val card = state.card
    // A fresh card always starts on its front.
    var flipped by remember(card.id) { mutableStateOf(false) }
    val rotation = remember(card.id) { Animatable(0f) }
    LaunchedEffect(flipped, card.id) { rotation.animateTo(if (flipped) 180f else 0f, tween(FLIP_MS)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onKeyEvent {
                if (it.key == Key.Spacebar && it.type == KeyEventType.KeyUp) {
                    flipped = !flipped
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${state.remaining} LEFT",
                fontFamily = MonoFamily,
                fontSize = 11.sp,
                letterSpacing = 0.66.sp,
                color = MaterialTheme.marginalia.dimInk,
                modifier = Modifier.weight(1f),
            )
            Text(
                "+ New card",
                fontFamily = BodyFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onNewCard).padding(8.dp),
            )
        }
        Box(Modifier.weight(1f).widthIn(max = 640.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CardFaces(card, imageLoader, rotation, onTap = { flipped = !flipped })
        }
        if (card.source != null && card.lectureId != null) {
            SourceChip(card.source) { onOpen(card.lectureId, card.page ?: 0) }
        }
        Box(Modifier.height(88.dp).padding(top = 14.dp), contentAlignment = Alignment.Center) {
            if (flipped) {
                GradeRow(card.labels, onGrade)
            } else {
                Text(
                    "TAP OR PRESS SPACE TO FLIP",
                    fontFamily = MonoFamily,
                    fontSize = 11.sp,
                    letterSpacing = 1.1.sp,
                    color = MaterialTheme.marginalia.dimInk,
                )
            }
        }
    }
}

/** Both faces stay composed; the flip only changes graphicsLayer values, so no frame recomposes. */
@Composable
private fun CardFaces(card: ReviewCard, imageLoader: ImageLoader, rotation: Animatable<Float, *>, onTap: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val surface = MaterialTheme.colorScheme.surface
    val hairline = MaterialTheme.colorScheme.outline
    Box(
        Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(shape)
            .clickable(onClick = onTap),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = rotation.value
                    cameraDistance = 16f * density
                    alpha = if (rotation.value < 90f) 1f else 0f
                }
                .background(surface, shape)
                .border(1.dp, hairline, shape)
                .padding(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (card.frontImagePath != null) {
                AsyncImage(
                    model = File(card.frontImagePath),
                    imageLoader = imageLoader,
                    contentDescription = "Card front",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CardText(card.frontText.orEmpty())
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = rotation.value + 180f
                    cameraDistance = 16f * density
                    alpha = if (rotation.value >= 90f) 1f else 0f
                }
                .background(surface, shape)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), shape)
                .padding(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!card.backText.isNullOrBlank()) CardText(card.backText)
                if (card.backInk != null) InkAnswerView(card.backInk, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun CardText(text: String) {
    Text(
        text,
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 33.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SourceChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontFamily = MonoFamily,
        fontSize = 10.5.sp,
        letterSpacing = 0.63.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

private val GradeNames = listOf("Again", "Hard", "Good", "Easy")

@Composable
private fun GradeRow(labels: List<String>, onGrade: (Grade) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Grade.entries.forEachIndexed { i, grade ->
            val primary = grade == Grade.GOOD
            val tint = when (grade) {
                Grade.AGAIN -> MaterialTheme.marginalia.danger
                Grade.GOOD -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurface
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(min = 112.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onGrade(grade) }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text(
                    labels.getOrElse(i) { "" },
                    fontFamily = MonoFamily,
                    fontSize = 11.sp,
                    color = if (primary) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.marginalia.dimInk,
                )
                Text(GradeNames[i], fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = tint)
            }
        }
    }
}

@Composable
private fun SummaryView(summary: ReviewUi.Summary, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Session complete", fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
        Row(Modifier.padding(vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Stat("${summary.reviewed}", "REVIEWED")
            Stat("${summary.goodPct}%", "GOOD OR EASY")
            Stat(
                "%d:%02d".format(Locale.ROOT, summary.seconds / 60, summary.seconds % 60),
                "TIME",
            )
        }
        GlassButton("Back to Today", onClick = onDone)
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(label, fontFamily = MonoFamily, fontSize = 11.sp, letterSpacing = 1.1.sp, color = MaterialTheme.marginalia.dimInk)
    }
}
