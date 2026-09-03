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
import com.example.digitaldelta.domain.sync.ConflictSide
import com.example.digitaldelta.domain.sync.MissionConflictSnapshot
import com.example.digitaldelta.domain.sync.MissionField
import com.example.digitaldelta.domain.sync.VectorClock
import com.example.digitaldelta.domain.routing.DynamicRouteDecision
import com.example.digitaldelta.domain.routing.PlannedRoute
import com.example.digitaldelta.domain.routing.RouteScenarioSnapshot
import com.example.digitaldelta.domain.routing.VehicleType
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var requestState: MutableState<RequestQueueUiState>
    private lateinit var meshState: MutableState<MeshRuntimeState>
    private lateinit var conflictState: MutableState<MissionConflictSnapshot>
    private lateinit var routeState: MutableState<RouteScenarioSnapshot>
    private var relayStartRequested = false

    @Before
    fun setup() {
        requestState = mutableStateOf(RequestQueueUiState.Idle)
        meshState = mutableStateOf(MeshRuntimeState())
        conflictState = mutableStateOf(MissionConflictSnapshot.Idle)
        routeState = mutableStateOf(routeSnapshot(flooded = false))
        relayStartRequested = false
        composeTestRule.setContent {
            DigitalDeltaTheme(darkTheme = false) {
                DigitalDeltaApp(
                    showBootSequence = false,
                    requestQueueState = requestState.value,
                    meshRuntimeState = meshState.value,
                    onStartRelay = { relayStartRequested = true },
                    conflictState = conflictState.value,
                    onSimulateConflict = {
                        conflictState.value = MissionConflictSnapshot.Open(
                            conflictId = "conflict-1",
                            missionId = "mission-sylhet-01",
                            field = MissionField.DESTINATION,
                            leftValue = "N3",
                            rightValue = "N6",
                            leftClock = VectorClock(mapOf("phone-a" to 2L, "phone-b" to 1L)),
                            rightClock = VectorClock(mapOf("phone-a" to 1L, "phone-b" to 2L)),
                        )
                    },
                    onResolveConflict = { _, side ->
                        conflictState.value = MissionConflictSnapshot.Resolved(
                            conflictId = "conflict-1",
                            missionId = "mission-sylhet-01",
                            field = MissionField.DESTINATION,
                            selectedValue = if (side == ConflictSide.LEFT) "N3" else "N6",
                            resolverIdentityId = "coordinator-sylhet-01",
                            convergenceHash = "a4e96ff28c89d214d02a3c87f01778e7ad3f139307376afaacd1a10da45a9b22",
                        )
                    },
                    routeState = routeState.value,
                    onToggleRouteFailure = {
                        routeState.value = routeSnapshot(flooded = "E3" !in routeState.value.failedEdgeIds)
                    },
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

    @Test
    fun concurrentSafetyEditRequiresHumanChoiceAndShowsConvergenceInBothLanguages() {
        composeTestRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        composeTestRule.onNodeWithText("সমসাময়িক অফলাইন সম্পাদনা চালান").performClick()
        composeTestRule.onNodeWithText("মানব সিদ্ধান্ত প্রয়োজন").assertIsDisplayed()
        composeTestRule.onNodeWithText("সুনামগঞ্জ সদর ক্যাম্প").assertIsDisplayed()
        composeTestRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        composeTestRule.onNodeWithText("হবিগঞ্জ মেডিকেল").assertIsDisplayed()

        composeTestRule.onNodeWithText("English").performClick()
        composeTestRule.onNodeWithText("Human decision required").assertExists()
        composeTestRule.onNodeWithText("Use Habiganj Medical").performClick()

        composeTestRule.onNodeWithText("Conflict resolved • devices converge").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hash • a4e96ff28c89").assertIsDisplayed()
    }

    @Test
    fun routeFailureRunsRealVehicleConstrainedFallbackWithBilingualEvidence() {
        composeTestRule.onNodeWithText("পথ ও মেশ").performClick()
        composeTestRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        composeTestRule.onNodeWithText("অফলাইনে ট্রাকের পথ প্রস্তুত").assertIsDisplayed()
        composeTestRule.onNode(hasTestTag("toggle-route-failure")).performClick()

        composeTestRule.onNodeWithText("E3 বন্ধ • নৌযানে পুনর্নির্দেশ").assertIsDisplayed()
        composeTestRule.onNodeWithText("নৌযান • N1 → N3 → N4 • E6 + E7").assertIsDisplayed()
        composeTestRule.onNode(hasTestTag("route-latency")).assertIsDisplayed()

        composeTestRule.onNodeWithText("English").performClick()
        composeTestRule.onNodeWithText("E3 failed • rerouted by boat").assertIsDisplayed()
        composeTestRule.onNodeWithText("Boat • N1 → N3 → N4 • E6 + E7").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reset offline route").assertIsDisplayed()
    }

    private fun routeSnapshot(flooded: Boolean): RouteScenarioSnapshot {
        val vehicle = if (flooded) VehicleType.BOAT else VehicleType.TRUCK
        return RouteScenarioSnapshot(
            failedEdgeIds = if (flooded) setOf("E3") else emptySet(),
            decision = DynamicRouteDecision(
                route = PlannedRoute(
                    nodeIds = if (flooded) listOf("N1", "N3", "N4") else listOf("N1", "N2", "N4"),
                    edgeIds = if (flooded) listOf("E6", "E7") else listOf("E1", "E3"),
                    totalMinutes = if (flooded) 200 else 65,
                    riskAdjusted = false,
                    explanation = "fixture route",
                ),
                preferredVehicle = VehicleType.TRUCK,
                routeVehicle = vehicle,
                fallbackUsed = flooded,
                computationNanos = if (flooded) 870_000 else 340_000,
            ),
        )
    }
}
