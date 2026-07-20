package com.serendeep.marginalia

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.serendeep.marginalia.library.LibraryScreen
import com.serendeep.marginalia.notebook.NotebookScreen
import com.serendeep.marginalia.notebook.NotebookViewModel
import com.serendeep.marginalia.ui.theme.MarginaliaTheme
import dagger.hilt.android.AndroidEntryPoint

private sealed class Screen {
    data object Library : Screen()
    data class Notebook(val lectureId: String) : Screen()
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notebookViewModel: NotebookViewModel by viewModels()
    private var lastPencilToggleAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MarginaliaTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Library) }
                when (val current = screen) {
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
