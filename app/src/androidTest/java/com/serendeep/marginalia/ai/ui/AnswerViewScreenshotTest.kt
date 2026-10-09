package com.serendeep.marginalia.ai.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.serendeep.marginalia.ui.theme.MarginaliaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Renders a rich answer in the real WebView and writes a PNG to the app's files dir. */
@RunWith(AndroidJUnit4::class)
class AnswerViewScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val answer = """
        ## Scaled dot-product attention

        Attention weights values by query–key similarity [Attention Is All You Need p.4]:

        $$\mathrm{Attention}(Q,K,V) = \mathrm{softmax}\left(\frac{QK^\top}{\sqrt{d_k}}\right)V$$

        Dividing by ${'$'}\sqrt{d_k}${'$'} keeps the softmax out of its flat regions.

        | Layer | Complexity | Sequential ops |
        |---|---|---|
        | Self-attention | ${'$'}O(n^2 d)${'$'} | ${'$'}O(1)${'$'} |
        | Recurrent | ${'$'}O(n d^2)${'$'} | ${'$'}O(n)${'$'} |

        ```python
        weights = softmax(q @ k.T / math.sqrt(d_k))
        ```

        ```mermaid
        flowchart LR
          Q --> S[Scores] --> M[Softmax] --> O[Output]
          K --> S
          V --> O
        ```
    """.trimIndent()

    @Test
    fun richAnswer() {
        composeRule.setContent {
            MarginaliaTheme(darkTheme = true) {
                Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
                    AnswerView(
                        messages = listOf(
                            AnswerMessage("u", AnswerRole.USER, "Explain scaled attention"),
                            AnswerMessage("a", AnswerRole.ASSISTANT, answer),
                        ),
                        streaming = false,
                        onCitation = { _, _ -> },
                        onLink = {},
                        modifier = Modifier.size(640.dp, 900.dp),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        Thread.sleep(6_000)
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        File(dir, "answer-rich.png").outputStream().use {
            composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
