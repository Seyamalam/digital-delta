package com.example.digitaldelta.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.example.digitaldelta.theme.DigitalDeltaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainScreenLargeTextTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun criticalBanglaAndEnglishLabelsRenderAtLargeFontScale() {
        composeTestRule.setContent {
            val deviceDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(deviceDensity.density, fontScale = 1.5f),
            ) {
                DigitalDeltaTheme(darkTheme = false) {
                    DigitalDeltaApp(showBootSequence = false)
                }
            }
        }

        composeTestRule.onNodeWithText("অফলাইন • Offline").assertIsDisplayed()
        composeTestRule.onNodeWithText("P0 • জরুরি চিকিৎসা").assertIsDisplayed()
        composeTestRule.onNodeWithText("English").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("Offline • অফলাইন").assertIsDisplayed()
        composeTestRule.onNodeWithText("P0 • Critical medical").assertIsDisplayed()

        val bangla = "জরুরি চিকিৎসা"
        assertTrue(
            "fixture must exercise Bengali combining marks",
            bangla.any { Character.getType(it) == Character.COMBINING_SPACING_MARK.toInt() },
        )
    }
}
