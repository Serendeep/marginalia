package com.serendeep.marginalia.ai.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.serendeep.marginalia.ui.theme.LocalDarkTheme
import org.json.JSONArray
import org.json.JSONObject

enum class AnswerRole { USER, ASSISTANT, STATUS }

data class AnswerMessage(val id: String, val role: AnswerRole, val markdown: String)

private const val ASSET_HOST = "appassets.androidplatform.net"
private const val PAGE_URL = "https://$ASSET_HOST/assets/answer/answer.html"
private const val MIN_WRAP_HEIGHT = 24

internal fun answerPayload(messages: List<AnswerMessage>, streaming: Boolean): String = JSONObject()
    .put("streaming", streaming)
    .put(
        "messages",
        JSONArray().apply {
            messages.forEach {
                put(JSONObject().put("id", it.id).put("role", it.role.name.lowercase()).put("markdown", it.markdown))
            }
        },
    )
    .toString()

private fun Color.css(): String =
    "rgba(${(red * 255).toInt()},${(green * 255).toInt()},${(blue * 255).toInt()},$alpha)"

private fun answerTheme(ink: Color, muted: Color, outline: Color, accent: Color, dark: Boolean, wrap: Boolean): String = JSONObject()
    .put("dark", dark)
    .put("wrap", wrap)
    .put(
        "vars",
        JSONObject()
            .put("ink", ink.css())
            .put("muted", muted.css())
            .put("outline", outline.css())
            .put("violet", accent.css())
            .put("code-bg", accent.copy(alpha = if (dark) 0.13f else 0.10f).css())
            .put("hl-string", if (dark) "#7fd6b0" else "#1f7a56")
            .put("hl-number", if (dark) "#e0b46c" else "#9a6a12")
            .put("body", "\"Plex Sans\", sans-serif"),
    )
    .toString()

private class AnswerHost {
    var ready = false
    var theme = ""
    var payload = ""
    private var sentTheme = ""
    private var sentPayload = ""

    fun flush(view: WebView) {
        if (!ready) return
        if (theme != sentTheme) {
            sentTheme = theme
            view.evaluateJavascript("setTheme($theme)", null)
        }
        if (payload != sentPayload) {
            sentPayload = payload
            view.evaluateJavascript("render($payload)", null)
        }
    }
}

private class AnswerBridge(
    private val view: View,
    private val onCitation: () -> ((String, Int) -> Unit),
    private val onLink: () -> ((String) -> Unit),
    private val onAction: () -> ((String) -> Unit),
    private val onHeight: (Int) -> Unit,
) {
    @JavascriptInterface
    fun post(kind: String, a: String, b: String) {
        view.post {
            when (kind) {
                "citation" -> b.toIntOrNull()?.let { onCitation()(a, it) }
                "link" -> onLink()(a)
                "action" -> onAction()(a)
                "height" -> a.toIntOrNull()?.let(onHeight)
            }
        }
    }
}

/**
 * Renders a whole transcript in one WebView. By default it fills the space it is given and scrolls itself,
 * following the bottom while [streaming] unless the reader scrolled up; with [wrapContent] it sizes to the content.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AnswerView(
    messages: List<AnswerMessage>,
    streaming: Boolean,
    onCitation: (title: String, page: Int) -> Unit,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAction: (String) -> Unit = {},
    wrapContent: Boolean = false,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val theme = answerTheme(scheme.onSurface, scheme.onSurfaceVariant, scheme.outline, scheme.primary, dark, wrapContent)
    val payload = remember(messages, streaming) { answerPayload(messages, streaming) }
    val host = remember { AnswerHost() }
    var contentHeight by remember { mutableIntStateOf(MIN_WRAP_HEIGHT) }
    val citation by rememberUpdatedState(onCitation)
    val link by rememberUpdatedState(onLink)
    val action by rememberUpdatedState(onAction)
    val loader = remember {
        WebViewAssetLoader.Builder()
            .setDomain(ASSET_HOST)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler("/res/", WebViewAssetLoader.ResourcesPathHandler(context))
            .build()
    }
    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            overScrollMode = View.OVER_SCROLL_NEVER
            settings.apply {
                javaScriptEnabled = true
                blockNetworkLoads = true
                allowFileAccess = false
                allowContentAccess = false
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
            }
            addJavascriptInterface(
                AnswerBridge(this, { citation }, { link }, { action }) { contentHeight = it },
                "Android",
            )
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    loader.shouldInterceptRequest(request.url)

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url
                    when {
                        url.host == ASSET_HOST -> return false
                        url.scheme == "http" || url.scheme == "https" -> link(url.toString())
                    }
                    return true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    host.ready = true
                    host.flush(view)
                }
            }
            loadUrl(PAGE_URL)
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.removeJavascriptInterface("Android")
            webView.destroy()
        }
    }
    AndroidView(
        factory = { webView },
        update = {
            host.theme = theme
            host.payload = payload
            host.flush(it)
        },
        modifier = if (wrapContent) modifier.height(contentHeight.dp) else modifier,
    )
}
