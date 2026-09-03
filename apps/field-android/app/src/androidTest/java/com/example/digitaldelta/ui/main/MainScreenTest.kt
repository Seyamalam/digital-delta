package com.example.digitaldelta.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.example.digitaldelta.theme.DigitalDeltaTheme
import com.example.digitaldelta.domain.mesh.MeshRuntimeState
import com.example.digitaldelta.domain.mesh.NearbyMeshState
import com.example.digitaldelta.domain.mesh.NearbyPeerCandidate
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var requestState: MutableState<RequestQueueUiState>
    private lateinit var meshState: MutableState<MeshRuntimeState>
    private var relayStartRequested = false

    @Before
    fun setup() {
        requestState = mutableStateOf(RequestQueueUiState.Idle)
        meshState = mutableStateOf(MeshRuntimeState())
        relayStartRequested = false
        composeTestRule.setContent {
            DigitalDeltaTheme(darkTheme = false) {
                DigitalDeltaApp(
                    showBootSequence = false,
                    requestQueueState = requestState.value,
                    meshRuntimeState = meshState.value,
                    onStartRelay = { relayStartRequested = true },
                )
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

    @Test
    fun identityProvisioning_isBilingualAndUsesPurposefulLoadingState() {
        composeTestRule.onNode(hasTestTag("identity-open")).performClick()
        composeTestRule.onNodeWithText("পরিচয় ও অফলাইন কী").assertIsDisplayed()
        composeTestRule.onNodeWithText("সুরক্ষিত ডিভাইস কী প্রস্তুত হচ্ছে").assertIsDisplayed()

        composeTestRule.onNodeWithText("English").performClick()
        composeTestRule.onNodeWithText("Identity and offline keys").assertIsDisplayed()
        composeTestRule.onNodeWithText("Preparing protected device keys").assertIsDisplayed()
    }

    @Test
    fun missingRecipientKey_hasActionableBanglaAndEnglishMessage() {
        composeTestRule.onNodeWithText("অনুরোধ").performClick()
        composeTestRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        composeTestRule.runOnIdle {
            requestState.value = RequestQueueUiState.Failed(RequestFailure.RECIPIENT_NOT_PROVISIONED)
        }
        composeTestRule.onNodeWithText("পাঠানোর আগে গন্তব্যের পরিচয় নিবন্ধন করুন।").assertIsDisplayed()

        composeTestRule.onNodeWithText("English").performClick()
        composeTestRule.onNodeWithText("Provision the destination identity before sending.").assertIsDisplayed()
    }

    @Test
    fun nearbyRelayShowsManualAuthenticationAndBilingualRuntimeState() {
        composeTestRule.onNodeWithText("পথ ও মেশ").performClick()
        composeTestRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        composeTestRule.onNode(hasTestTag("mesh-relay-toggle")).performClick()
        composeTestRule.runOnIdle {
            assertTrue(relayStartRequested)
            meshState.value = MeshRuntimeState(
                nearby = NearbyMeshState(
                    running = true,
                    pendingCandidates = listOf(NearbyPeerCandidate("endpoint-c", "N6", "482 193")),
                ),
                batteryPercent = 28,
                broadcastIntervalMillis = 25_000,
            )
        }
        composeTestRule.onNodeWithText("দুই ফোনে কোড মিলান: 482 193").assertIsDisplayed()
        composeTestRule.onNodeWithText("English").performClick()
        composeTestRule.onNodeWithText("Compare on both phones: 482 193").assertIsDisplayed()
        composeTestRule.onNodeWithText("Stop nearby relay").assertIsDisplayed()
    }
}
