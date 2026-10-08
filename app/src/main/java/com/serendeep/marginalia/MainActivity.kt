@file:Suppress("RestrictedApi")

package com.serendeep.marginalia

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.serendeep.marginalia.highlights.HighlightsScreen
import com.serendeep.marginalia.library.LibraryScreen
import com.serendeep.marginalia.search.SearchScreen
import com.serendeep.marginalia.shell.AppShell
import com.serendeep.marginalia.shell.ComingSoon
import com.serendeep.marginalia.shell.Screen
import com.serendeep.marginalia.today.TodayScreen
import com.serendeep.marginalia.notebook.NotebookScreen
import com.serendeep.marginalia.notebook.NotebookViewModel
import com.serendeep.marginalia.ui.theme.MarginaliaTheme
import dagger.hilt.android.AndroidEntryPoint

/** Scopes for cover-to-notebook shared-element flight; null outside navigation. */
val LocalSharedTransition = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimation = compositionLocalOf<AnimatedContentScope?> { null }

/** A library cover and the notebook's PDF pane fly as one surface. */
@Composable
fun Modifier.sharedCover(key: String): Modifier {
    val shared = LocalSharedTransition.current ?: return this
    val anim = LocalNavAnimation.current ?: return this
    return with(shared) {
        this@sharedCover.sharedBounds(rememberSharedContentState(key), anim)
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notebookViewModel: NotebookViewModel by viewModels()
    private var lastPencilToggleAt = 0L
    private var incomingPdfUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingPdfUri = pdfUri(intent)
        enableEdgeToEdge()
        setContent {
            MarginaliaTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Today) }
                val pendingPdf = incomingPdfUri
                LaunchedEffect(pendingPdf) {
                    if (pendingPdf != null) screen = Screen.Library()
                }
                // The shell destination stays put while a notebook is open, so
                // returning (and the shared-cover flight) lands where it left.
                val shellScreen = (screen as? Screen.Notebook)?.returnTo ?: screen
                val notebookId = (screen as? Screen.Notebook)?.lectureId
                SharedTransitionLayout(Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = notebookId,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            // Opening a notebook slides content in from the right;
                            // returning slides back the other way.
                            val forward = targetState != null
                            val dir = if (forward) 1 else -1
                            (slideInHorizontally(tween(260)) { dir * it / 10 } + fadeIn(tween(260)))
                                .togetherWith(
                                    slideOutHorizontally(tween(260)) { -dir * it / 10 } + fadeOut(tween(200)),
                                )
                        },
                        label = "screen",
                    ) { openId ->
                        CompositionLocalProvider(
                            LocalSharedTransition provides this@SharedTransitionLayout,
                            LocalNavAnimation provides this@AnimatedContent,
                        ) {
                            if (openId != null) {
                                BackHandler { screen = shellScreen }
                                NotebookScreen(
                                    viewModel = notebookViewModel,
                                    lectureId = openId,
                                    onBack = { screen = shellScreen },
                                    startPage = (screen as? Screen.Notebook)?.page,
                                )
                            } else {
                                val open: (String, Int?) -> Unit = { id, page -> screen = Screen.Notebook(id, shellScreen, page) }
                                AppShell(screen = shellScreen, onNavigate = { screen = it }) {
                                    when (val s = shellScreen) {
                                        Screen.Today -> TodayScreen(onOpenLecture = { open(it, null) }, onOpenAt = { id, page -> open(id, page) }, onNavigate = { screen = it })
                                        is Screen.Library -> LibraryScreen(
                                            filter = s.filter,
                                            incomingPdfUri = pendingPdf,
                                            onIncomingPdfHandled = { incomingPdfUri = null },
                                            onOpenLecture = { open(it, null) },
                                        )
                                        Screen.Review -> ComingSoon("Review")
                                        Screen.Search -> SearchScreen(onOpen = open)
                                        Screen.Highlights -> HighlightsScreen(onOpen = { id, page -> open(id, page) })
                                        Screen.Stats -> ComingSoon("Stats")
                                        is Screen.Notebook -> Unit
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingPdfUri = pdfUri(intent)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Huawei M-Pencil double-tap arrives as undocumented keyCode 718,
        // fired as two down/up pairs per gesture; debounce to one toggle.
        if (event.keyCode == MPENCIL_DOUBLE_TAP_KEYCODE && event.action == KeyEvent.ACTION_DOWN) {
            val elapsed = SystemClock.elapsedRealtime()
            if (elapsed - lastPencilToggleAt > 400) {
                lastPencilToggleAt = elapsed
                notebookViewModel.toggleTool()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private companion object {
        const val MPENCIL_DOUBLE_TAP_KEYCODE = 718

        fun pdfUri(intent: Intent?): Uri? = intent
            ?.takeIf { it.action == Intent.ACTION_VIEW && it.type == "application/pdf" }
            ?.data
    }
}
