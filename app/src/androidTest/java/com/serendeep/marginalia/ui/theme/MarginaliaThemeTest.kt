package com.serendeep.marginalia.ui.theme

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarginaliaThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun themeComposesInLightMode() {
        composeRule.setContent {
            MarginaliaTheme(darkTheme = false) {
                Text("light")
            }
        }
        composeRule.onNodeWithText("light").assertExists()
    }

    @Test
    fun themeComposesInDarkMode() {
        composeRule.setContent {
            MarginaliaTheme(darkTheme = true) {
                Text("dark")
            }
        }
        composeRule.onNodeWithText("dark").assertExists()
    }
}
