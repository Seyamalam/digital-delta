package com.example.digitaldelta.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.digitaldelta.theme.DigitalDeltaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OfflineUnlockScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun firstRunCreatesSixDigitPinInBanglaWithoutOpeningFieldActions() {
        var configuredPin: String? = null
        composeTestRule.setContent {
            DigitalDeltaTheme(darkTheme = false) {
                DigitalDeltaApp(
                    showBootSequence = false,
                    unlockState = OfflineUnlockUiState.SetupRequired(),
                    onConfigurePin = { configuredPin = it },
                )
            }
        }

        composeTestRule.onNodeWithText("অফলাইন PIN তৈরি করুন").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pin-entry").performTextInput("284619")
        composeTestRule.onNodeWithTag("pin-confirm").performTextInput("284619")
        composeTestRule.onNodeWithTag("configure-pin").performClick()

        assertEquals("284619", configuredPin)
        composeTestRule.onNodeWithTag("nav-request").assertDoesNotExist()
    }
}
