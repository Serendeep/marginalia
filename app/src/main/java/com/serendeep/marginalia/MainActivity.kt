package com.serendeep.marginalia

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.serendeep.marginalia.library.LibraryScreen
import com.serendeep.marginalia.notebook.NotebookScreen
import com.serendeep.marginalia.notebook.NotebookViewModel
import com.serendeep.marginalia.ui.theme.MarginaliaTheme
import dagger.hilt.android.AndroidEntryPoint

private sealed class Screen {
    data object Library : Screen()
    data class Notebook(val lectureId: String) : Screen()
}

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MarginaliaTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Library) }
                SharedTransitionLayout(Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = screen,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            // Opening a notebook slides content in from the right;
                            // returning to the library slides back the other way.
                            val forward = targetState is Screen.Notebook
                            val dir = if (forward) 1 else -1
                            (slideInHorizontally(tween(260)) { dir * it / 10 } + fadeIn(tween(260)))
                                .togetherWith(
                                    slideOutHorizontally(tween(260)) { -dir * it / 10 } + fadeOut(tween(200)),
                                )
                        },
                        label = "screen",
                    ) { current ->
                        CompositionLocalProvider(
                            LocalSharedTransition provides this@SharedTransitionLayout,
                            LocalNavAnimation provides this@AnimatedContent,
                        ) {
                            when (current) {
                                is Screen.Library -> LibraryScreen(
                                    onOpenLecture = { screen = Screen.Notebook(it) },
                                )

                                is Screen.Notebook -> {
                                    BackHandler { screen = Screen.Library }
                                    NotebookScreen(
                                        viewModel = notebookViewModel,
                                        lectureId = current.lectureId,
                                        onBack = { screen = Screen.Library },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
    }
}
