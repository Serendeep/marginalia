package com.serendeep.marginalia

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.serendeep.marginalia.notebook.NotebookScreen
import com.serendeep.marginalia.notebook.NotebookViewModel
import com.serendeep.marginalia.ui.theme.MarginaliaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notebookViewModel: NotebookViewModel by viewModels()
    private var lastPencilToggleAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MarginaliaTheme {
                NotebookScreen(notebookViewModel)
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
