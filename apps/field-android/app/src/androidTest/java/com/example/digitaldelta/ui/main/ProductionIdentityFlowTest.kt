package com.example.digitaldelta.ui.main

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.digitaldelta.MainActivity
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ProductionIdentityFlowTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun deviceBoundEnrollmentBecomesVisibleThroughProductionGraph() {
        chooseBanglaIfRequired("identity-open")
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("identity-open"), timeoutMillis = 4_000)
        composeTestRule.onNode(hasTestTag("identity-open")).performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("identity-screen"), timeoutMillis = 8_000)
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("profile-CLINIC_N4"))
        composeTestRule.onNode(hasTestTag("profile-CLINIC_N4")).assertIsDisplayed()
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("profile-HOSPITAL_N6"))
        composeTestRule.onNode(hasTestTag("profile-HOSPITAL_N6")).assertIsDisplayed()
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("profile-RELAY_R1"))
        composeTestRule.onNode(hasTestTag("profile-RELAY_R1")).assertIsDisplayed()
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("enrollment-card"))
        composeTestRule.onNode(hasTestTag("enrollment-card")).assertIsDisplayed()
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("scan-recipient-credential"))
        composeTestRule.onNode(hasTestTag("scan-recipient-credential")).assertIsDisplayed()
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("scan-credential-revocation"))
        composeTestRule.onNode(hasTestTag("scan-credential-revocation")).assertIsDisplayed()
    }

    private fun chooseBanglaIfRequired(destinationTag: String) {
        composeTestRule.waitUntil(timeoutMillis = 8_000) {
            composeTestRule.onAllNodes(hasTestTag("language-bangla")).fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodes(hasTestTag("pin-entry")).fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodes(hasTestTag(destinationTag)).fetchSemanticsNodes().isNotEmpty()
        }
        if (composeTestRule.onAllNodes(hasTestTag("language-bangla")).fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNode(hasTestTag("language-bangla")).performClick()
        }
        composeTestRule.waitUntil(timeoutMillis = 8_000) {
            composeTestRule.onAllNodes(hasTestTag("pin-entry")).fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodes(hasTestTag(destinationTag)).fetchSemanticsNodes().isNotEmpty()
        }
        if (composeTestRule.onAllNodes(hasTestTag("pin-entry")).fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNode(hasTestTag("pin-entry")).performTextInput("284619")
            if (composeTestRule.onAllNodes(hasTestTag("pin-confirm")).fetchSemanticsNodes().isNotEmpty()) {
                composeTestRule.onNode(hasTestTag("pin-confirm")).performTextInput("284619")
                composeTestRule.onNode(hasTestTag("configure-pin")).performClick()
            } else {
                composeTestRule.onNode(hasTestTag("unlock-pin")).performClick()
            }
        }
    }
}
