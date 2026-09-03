package com.example.digitaldelta.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.example.digitaldelta.theme.DigitalDeltaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setup() {
        composeTestRule.setContent {
            DigitalDeltaTheme(darkTheme = false) {
                DigitalDeltaApp(showBootSequence = false)
            }
        }
    }

    @Test
    fun operationsScreen_isBanglaFirst_andCanSwitchToEnglish() {
        composeTestRule.onNodeWithText("P0 • জরুরি চিকিৎসা").assertIsDisplayed()
        composeTestRule.onNodeWithText("English").performClick()

        composeTestRule.onNodeWithText("P0 • Critical medical").assertIsDisplayed()
        composeTestRule.onNodeWithText("বাংলা").assertIsDisplayed()
    }

    @Test
    fun requestState_survivesLanguageSwitch_andCanQueueOffline() {
        composeTestRule.onNodeWithText("English").performClick()
        composeTestRule.onNodeWithText("Request").performClick()
        composeTestRule.onNodeWithContentDescription("Increase Medicine").performClick()
        composeTestRule.onNodeWithText("11").assertIsDisplayed()
        composeTestRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        composeTestRule.onNodeWithText("Send request").performClick()
        composeTestRule.onNodeWithText("Request encrypted and queued offline").assertIsDisplayed()

        composeTestRule.onNodeWithText("বাংলা").performClick()
        composeTestRule.onNodeWithText("11").assertIsDisplayed()
        composeTestRule.onNodeWithText("অনুরোধ এনক্রিপ্ট করে অফলাইনে সারিবদ্ধ হয়েছে").assertIsDisplayed()
    }

    @Test
    fun replayAttempt_isRejectedWithoutChangingVerifiedCustody() {
        composeTestRule.onNodeWithText("English").performClick()
        composeTestRule.onNodeWithText("Handoff").performClick()
        composeTestRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        composeTestRule.onNodeWithText("Verify signed handoff").performClick()

        composeTestRule.onNodeWithText("Replay rejected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Handoff verified").assertExists()
    }
}
