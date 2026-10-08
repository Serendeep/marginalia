package com.serendeep.marginalia.ai.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.serendeep.marginalia.ai.AiConfig
import com.serendeep.marginalia.ai.ChatGptStatus
import com.serendeep.marginalia.ai.ChatModel
import com.serendeep.marginalia.ai.ProviderChoice
import com.serendeep.marginalia.ui.theme.MarginaliaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Renders the AI surfaces with fake state and writes PNGs to the app's external files dir. */
@RunWith(AndroidJUnit4::class)
class AiScreenshotsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun shoot(name: String, content: @Composable () -> Unit) {
        composeRule.setContent {
            MarginaliaTheme(darkTheme = true) {
                Box(Modifier.background(MaterialTheme.colorScheme.surface)) { content() }
            }
        }
        composeRule.waitForIdle()
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        File(dir, "p5ui-$name.png").outputStream().use {
            composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private val noop = {}
    private val models = ModelsState.Loaded(listOf(ChatModel("gpt-5.5", "GPT-5.5"), ChatModel("gpt-5.5-mini", "GPT-5.5 mini")))

    private fun settings(config: AiConfig, status: ChatGptStatus, state: ModelsState = ModelsState.Idle, slug: String? = null) =
        @Composable {
            AiSettingsContent(
                config, status, state, slug, null,
                onProvider = {}, onConnect = noop, onCancelSignIn = noop, onDisconnect = noop,
                onSelectModel = {}, onTest = { _, _ -> }, onCustomEdited = { _, _ -> },
            )
        }

    @Test
    fun settingsDisconnected() = shoot("settings-disconnected", settings(AiConfig(), ChatGptStatus.Disconnected))

    @Test
    fun settingsConnected() = shoot(
        "settings-connected",
        settings(AiConfig(), ChatGptStatus.Connected("me@example.com", "gpt-5.5"), models, "gpt-5.5"),
    )

    @Test
    fun settingsCustomEndpoint() = shoot(
        "settings-custom",
        settings(AiConfig(provider = ProviderChoice.COMPATIBLE), ChatGptStatus.Disconnected),
    )

    private fun notebook(ready: Boolean, action: AiAction?, state: AiRunState, text: String) = @Composable {
        NotebookAiContent(
            ready = ready, hasPdf = true, action = action, state = state, text = MutableStateFlow(text),
            drafts = emptyList(), savedCount = 0, pageLabel = "p.14",
            onExplain = {}, onSummarize = {}, onCards = {}, onAsk = {}, onStop = {}, onSetup = {},
            onDraft = { _, _, _, _ -> }, onSave = {},
        )
    }

    @Test
    fun notebookDisconnected() = shoot("notebook-disconnected", notebook(false, null, AiRunState.Idle, ""))

    @Test
    fun notebookStreaming() = shoot(
        "notebook-ask",
        notebook(
            true, AiAction.EXPLAIN, AiRunState.Streaming,
            "**Main idea.** Dijkstra's algorithm finds shortest paths from one source when every edge weight is non-negative.\n\n" +
                "- Keep a priority queue of `(distance, node)` pairs\n- Pop the closest node and relax its edges\n" +
                "- With negative edges a settled node can still improve, so the **greedy choice breaks**",
        ),
    )

    @Test
    fun todayCardDisconnected() = shoot("today-card-disconnected", todayCard(false, AiRunState.Idle, ""))

    @Test
    fun todayCardAnswer() = shoot(
        "today-card",
        todayCard(
            true, AiRunState.Done,
            "Dijkstra fails with negative edge weights because a settled node may later get a shorter path [Lecture 7 p.14]. " +
                "Bellman-Ford handles them in O(VE) [Lecture 7 p.15].",
        ),
    )

    private fun todayCard(ready: Boolean, state: AiRunState, text: String) = @Composable {
        Box(Modifier.width(300.dp).padding(18.dp)) {
            AskChatGptCardContent(
                ready = ready, state = state, text = MutableStateFlow(text),
                citations = if (state == AiRunState.Done) listOf(CitationChip("Lecture 7", 14, "l"), CitationChip("Lecture 7", 15, "l")) else emptyList(),
                onAsk = {}, onStop = {}, onClose = {}, onOpenAt = { _, _ -> }, onSetup = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
