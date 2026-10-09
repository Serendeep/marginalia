package com.serendeep.marginalia.ai.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
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

    private val transcript = listOf(
        AnswerMessage("0-u", AnswerRole.USER, "Why does Dijkstra fail with negative edges?"),
        AnswerMessage("0-0", AnswerRole.STATUS, "Reading Lecture 7 p.14"),
        AnswerMessage(
            "0-1", AnswerRole.ASSISTANT,
            "A settled node can still get a shorter path through a negative edge [Lecture 7 p.14], so the **greedy choice breaks**.\n\n" +
                "- Keep a priority queue of `(distance, node)` pairs\n- Bellman-Ford handles negative weights in O(VE) [Lecture 7 p.15]",
        ),
    )

    private fun chat(ready: Boolean, messages: List<AnswerMessage>, streaming: Boolean = false) = @Composable {
        ChatPaneContent(
            messages = messages, streaming = streaming, ready = ready, needsSetup = false,
            drafts = emptyList(), savedCount = 0,
            suggestions = listOf("Quiz me on Lecture 7", "Compare Lecture 7 and Lecture 8", "What did I highlight this week?"),
            onSend = {}, onStop = {}, onNewChat = {}, onCitation = { _, _ -> },
            onDraft = { _, _, _, _ -> }, onSaveDrafts = {}, onSetup = {},
        )
    }

    @Test
    fun askScreen() = shoot("ask-screen") {
        Row(Modifier.width(1120.dp).height(680.dp).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.weight(0.6f)) { chat(true, transcript)() }
            PreviewPaneContent(null, null, false, {}, Modifier.weight(0.4f))
        }
    }

    @Test
    fun askScreenEmpty() = shoot("ask-empty") {
        Box(Modifier.width(700.dp).height(560.dp).padding(18.dp)) { chat(true, emptyList())() }
    }

    @Test
    fun notebookPanelDisconnected() = shoot("notebook-disconnected") {
        Box(Modifier.width(460.dp).height(560.dp).padding(18.dp)) { chat(false, emptyList())() }
    }

    @Test
    fun notebookPanelChat() = shoot("notebook-ask") {
        Box(Modifier.width(460.dp).height(640.dp).padding(18.dp)) { chat(true, transcript, streaming = true)() }
    }

    @Test
    fun todayCardDisconnected() = shoot("today-card-disconnected") { todayCard(false)() }

    @Test
    fun todayCard() = shoot("today-card") { todayCard(true)() }

    private fun todayCard(ready: Boolean) = @Composable {
        Box(Modifier.width(300.dp).padding(18.dp)) {
            AskChatGptCardContent(ready = ready, onAsk = {}, onSetup = {}, modifier = Modifier.fillMaxWidth())
        }
    }
}
