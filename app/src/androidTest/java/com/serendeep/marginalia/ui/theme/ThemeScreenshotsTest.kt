package com.serendeep.marginalia.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.serendeep.marginalia.shell.NightlyMoon
import com.serendeep.marginalia.shell.nightSky
import com.serendeep.marginalia.today.Tile
import com.serendeep.marginalia.today.TileLabel
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.components.GlassTextButton
import com.serendeep.marginalia.ui.components.MarginLabel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Renders key surfaces in both themes and writes PNGs to the app's files dir. */
@RunWith(AndroidJUnit4::class)
class ThemeScreenshotsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun shoot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        composeRule.setContent { MarginaliaTheme(darkTheme = dark) { content() } }
        composeRule.waitForIdle()
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        File(dir, "theme-$name-${if (dark) "dark" else "light"}.png").outputStream().use {
            composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun both(name: String, content: @Composable () -> Unit) {
        shoot(name, dark = false, content)
        shoot(name, dark = true, content)
    }

    @Test
    fun tokens() = both("tokens") {
        Column(Modifier.width(520.dp).padding(20.dp)) {
            MarginLabel("Appearance")
            Text("Ink on paper", style = MaterialTheme.typography.titleMedium)
            Text("Muted caption", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Dim meta line", color = MaterialTheme.marginalia.dimInk)
            Text("Violet link", color = MaterialTheme.colorScheme.primary)
            Text("Lime for time", color = MaterialTheme.marginalia.limeInk)
            Text("Something went wrong", color = MaterialTheme.marginalia.errorInk)
            Row {
                GlassButton("Save", onClick = {})
                GlassTextButton("Cancel", onClick = {})
            }
            Row(Modifier.padding(top = 8.dp)) {
                MaterialTheme.marginalia.heat.forEach { Box(Modifier.width(24.dp).height(24.dp).background(it)) }
            }
        }
    }

    @Test
    fun tiles() = both("tiles") {
        Row(Modifier.width(560.dp).height(160.dp).padding(16.dp)) {
            Tile(Modifier.width(240.dp), Modifier.background(MaterialTheme.colorScheme.surface), MaterialTheme.colorScheme.outline) {
                Column {
                    TileLabel("Streak", MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("12 days")
                }
            }
            Tile(Modifier.width(240.dp), Modifier.background(Lime), Lime) {
                TileLabel("Focus", Color(0xFF4A5A00))
            }
        }
    }

    @Test
    fun sidebarSky() = both("sky") {
        Box(Modifier.width(240.dp).height(220.dp).nightSky()) {
            Box(Modifier.padding(16.dp)) { NightlyMoon(24.dp) }
        }
    }
}
