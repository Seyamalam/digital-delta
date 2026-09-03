package com.example.digitaldelta.ui.main

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import com.example.digitaldelta.MainActivity
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ProductionIdentityFlowTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun deviceBoundEnrollmentBecomesVisibleThroughProductionGraph() {
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("identity-open"), timeoutMillis = 4_000)
        composeTestRule.onNode(hasTestTag("identity-open")).performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("enrollment-card"), timeoutMillis = 8_000)
        composeTestRule.onNode(hasTestTag("enrollment-card")).assertIsDisplayed()
    }
}
