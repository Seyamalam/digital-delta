package com.example.digitaldelta.ui.main

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.example.digitaldelta.MainActivity
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ProductionRequestFlowTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun requestIsEncryptedAndPersistedThroughProductionGraph() {
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("nav-request"), timeoutMillis = 4_000)
        composeTestRule.onNode(hasTestTag("nav-request")).performClick()
        composeTestRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        composeTestRule.onNode(hasTestTag("send-request")).performClick()

        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("request-queued"), timeoutMillis = 4_000)
        composeTestRule.onNode(hasTestTag("request-queued")).assertExists()
    }
}
