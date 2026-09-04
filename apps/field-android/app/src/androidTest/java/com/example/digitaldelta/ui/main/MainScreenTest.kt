package com.example.digitaldelta.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.example.digitaldelta.theme.DigitalDeltaTheme
import com.example.digitaldelta.domain.mesh.MeshRuntimeState
import com.example.digitaldelta.domain.mesh.NearbyMeshState
import com.example.digitaldelta.domain.mesh.NearbyPeerCandidate
import com.example.digitaldelta.domain.identity.Permission
import com.example.digitaldelta.domain.identity.Role
import com.example.digitaldelta.domain.sync.ConflictSide
import com.example.digitaldelta.domain.sync.MissionConflictSnapshot
import com.example.digitaldelta.domain.sync.MissionField
import com.example.digitaldelta.domain.sync.VectorClock
import com.example.digitaldelta.domain.routing.DynamicRouteDecision
import com.example.digitaldelta.domain.routing.PlannedRoute
import com.example.digitaldelta.domain.routing.RouteScenarioSnapshot
import com.example.digitaldelta.domain.routing.VehicleType
import com.example.digitaldelta.domain.routing.RouteDecisionCause
import com.example.digitaldelta.domain.prediction.RouteRiskFeatures
import com.example.digitaldelta.domain.prediction.RouteRiskPrediction
import com.example.digitaldelta.domain.prediction.RouteRiskRuntime
import com.example.digitaldelta.domain.triage.DefaultTriageWorkflow
import com.example.digitaldelta.domain.triage.TriageWorkflowSnapshot
import com.example.digitaldelta.domain.pod.CustodyReceiptRecord
import com.example.digitaldelta.domain.pod.DeliveryOfferReady
import com.example.digitaldelta.domain.pod.DeliveryOfferRejection
import com.example.digitaldelta.domain.fleet.FleetOrchestrator
import com.example.digitaldelta.domain.fleet.BoatDelayReport
import com.example.digitaldelta.domain.fleet.GeoPoint
import com.example.digitaldelta.domain.fleet.HybridFleetInputs
import com.example.digitaldelta.domain.fleet.HybridFleetMission
import com.example.digitaldelta.domain.fleet.HybridFleetPlan
import com.example.digitaldelta.domain.fleet.HybridFleetState
import com.example.digitaldelta.domain.fleet.NamedPoint
import com.example.digitaldelta.domain.fleet.Reachability
import com.example.digitaldelta.domain.routing.EdgeMode
import com.example.digitaldelta.domain.routing.MapEdge
import com.example.digitaldelta.domain.routing.MapNode
import com.example.digitaldelta.domain.routing.TransportGraph
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
    private lateinit var triageState: MutableState<TriageWorkflowSnapshot>
    private lateinit var proofState: MutableState<ProofOfDeliveryUiState>
    private lateinit var riskState: MutableState<RouteRiskUiState>
    private lateinit var hybridState: MutableState<HybridFleetState>
    private lateinit var authorizationState: MutableState<FieldAuthorizationUiState>
    private var relayStartRequested = false

    @Before
    fun setup() {
        requestState = mutableStateOf(RequestQueueUiState.Idle)
        meshState = mutableStateOf(MeshRuntimeState())
        conflictState = mutableStateOf(MissionConflictSnapshot.Idle)
        routeState = mutableStateOf(routeSnapshot(flooded = false))
        triageState = mutableStateOf(DefaultTriageWorkflow().evaluate(65))
        proofState = mutableStateOf(ProofOfDeliveryUiState.Ready(podOffer()))
        riskState = mutableStateOf(RouteRiskUiState.Idle)
        hybridState = mutableStateOf(HybridFleetState.Ready(hybridPlan()))
        authorizationState = mutableStateOf(
            FieldAuthorizationUiState(Role.COORDINATOR, Permission.entries.toSet()),
        )
        relayStartRequested = false
        composeTestRule.setContent {
            DigitalDeltaTheme(darkTheme = false) {
                DigitalDeltaApp(
                    showBootSequence = false,
                    authorizationState = authorizationState.value,
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
                        val flooded = "E3" !in routeState.value.failedEdgeIds
                        routeState.value = routeSnapshot(flooded = flooded)
                        triageState.value = DefaultTriageWorkflow().evaluate(if (flooded) 200 else 65)
                    },
                    routeRiskState = riskState.value,
                    onToggleRouteRisk = {
                        if (riskState.value is RouteRiskUiState.Active) {
                            routeState.value = routeSnapshot(flooded = false)
                            riskState.value = RouteRiskUiState.Idle
                        } else {
                            val before = routeState.value.decision
                            val updated = routeSnapshot(flooded = true).decision.copy(
                                cause = RouteDecisionCause.PREDICTED_RISK,
                            )
                            routeState.value = RouteScenarioSnapshot(emptySet(), updated)
                            riskState.value = RouteRiskUiState.Active(
                                edgeId = "E3",
                                features = RouteRiskFeatures(82.0, 3.0, 0.92),
                                prediction = RouteRiskPrediction(
                                    probability = 0.96,
                                    impassableWithinTwoHours = true,
                                    threshold = 0.285,
                                    modelVersion = "route-risk-logreg-v1",
                                    simulatedInputs = true,
                                    runtime = RouteRiskRuntime.ONNX,
                                ),
                                previousDecision = before,
                                updatedDecision = updated,
                            )
                        }
                    },
                    triageState = triageState.value,
                    onConfirmPreemption = {
                        val proposed = triageState.value as TriageWorkflowSnapshot.Proposed
                        triageState.value = TriageWorkflowSnapshot.Confirmed(
                            decision = proposed.decision,
                            proposal = proposed.proposal,
                            eventId = "preemption-event-1",
                            confirmedAtUnixMs = 1_800_000_000_000,
                        )
                    },
                    proofOfDeliveryState = proofState.value,
                    onVerifyHandoff = { tampered ->
                        val offer = when (val current = proofState.value) {
                            is ProofOfDeliveryUiState.Ready -> current.offer
                            is ProofOfDeliveryUiState.Verified -> current.offer
                            is ProofOfDeliveryUiState.Rejected -> current.offer
                            else -> podOffer()
                        }
                        val receipt = podReceipt()
                        proofState.value = when {
                            tampered -> ProofOfDeliveryUiState.Rejected(
                                offer,
                                DeliveryOfferRejection.INVALID_SIGNATURE,
                                if (proofState.value is ProofOfDeliveryUiState.Verified) listOf(receipt) else emptyList(),
                            )
                            proofState.value is ProofOfDeliveryUiState.Verified -> ProofOfDeliveryUiState.Rejected(
                                offer,
                                DeliveryOfferRejection.REPLAY_REJECTED,
                                listOf(receipt),
                            )
                            else -> ProofOfDeliveryUiState.Verified(offer, receipt, listOf(receipt))
                        }
                    },
                    onPrepareNextHandoff = { proofState.value = ProofOfDeliveryUiState.Ready(podOffer()) },
                    hybridFleetState = hybridState.value,
                    onReportBoatDelay = {
                        val previous = (hybridState.value as HybridFleetState.Ready).plan
                        val report = BoatDelayReport(18, GeoPoint(25.04, 91.80))
                        val revisedMission = previous.mission.copy(
                            rendezvousInputs = previous.mission.rendezvousInputs.copy(
                                boatPosition = report.observedPosition,
                                boatStartDelayMinutes = report.delayMinutes.toDouble(),
                            ),
                        )
                        val revised = HybridFleetPlan(
                            revisedMission,
                            previous.reachability,
                            FleetOrchestrator().computeRendezvous(revisedMission.rendezvousInputs),
                        )
                        hybridState.value = HybridFleetState.Replanned(previous, revised, report)
                    },
                    onAdvanceHybridFleet = {
                        hybridState.value = when (val current = hybridState.value) {
                            is HybridFleetState.Ready -> HybridFleetState.BoatArrived(current.plan)
                            is HybridFleetState.BoatArrived -> HybridFleetState.DroneArrived(
                                current.plan,
                                podOffer().copy(
                                    deliveryId = "DELTA-DRONE-0001",
                                    recipientIdentityId = "simulated-drone-07",
                                ),
                            )
                            is HybridFleetState.DroneArrived -> {
                                val receipt = podReceipt().copy(
                                    deliveryId = "DELTA-DRONE-0001",
                                    recipientIdentityId = "simulated-drone-07",
                                )
                                HybridFleetState.Transferred(current.plan, receipt, listOf(receipt))
                            }
                            else -> current
                        }
                    },
                    onResetHybridFleet = { hybridState.value = HybridFleetState.Ready(hybridPlan()) },
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
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("verify-handoff"))
        composeTestRule.onNode(hasTestTag("verify-handoff")).performClick()
        composeTestRule.onNodeWithText("Handoff verified").assertIsDisplayed()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Verify the same QR again").assertIsDisplayed()
        composeTestRule.onNode(hasTestTag("verify-handoff")).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Replay rejected").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 • linked chain valid").assertExists()
    }

    @Test
    fun hybridFleetJourneyIsDroneRequiredSimulatedAndBilingual() {
        composeTestRule.onNodeWithText("English").performClick()
        composeTestRule.onNodeWithText("Handoff").performClick()
        composeTestRule.onNodeWithText("DRONE-REQUIRED").assertIsDisplayed()
        composeTestRule.onNodeWithText("SIMULATED • Simulated").assertIsDisplayed()
        composeTestRule.onNodeWithText("Boat → drone last-mile handoff").assertIsDisplayed()
        composeTestRule.onNodeWithText("N7 • Tanguar Haor Clinic").assertExists()
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("scan-handoff"))
        composeTestRule.onNode(hasTestTag("scan-handoff")).assertIsDisplayed()

        composeTestRule.onNode(hasTestTag("hybrid-fleet-action")).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Boat arrived at rendezvous").assertExists()
        composeTestRule.onNode(hasTestTag("hybrid-fleet-action")).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Simulated drone arrived").assertExists()
        composeTestRule.onNode(hasTestTag("hybrid-fleet-action")).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Drone custody transferred").assertExists()
        composeTestRule.onNode(hasTestTag("hybrid-fleet-receipt")).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("simulated-drone-07 • 04040404…04040404").assertExists()

        composeTestRule.onNodeWithText("বাংলা").performClick()
        composeTestRule.onNodeWithText("ড্রোনের কাছে হেফাজত হস্তান্তরিত").assertExists()
        composeTestRule.onNodeWithText("দুই পক্ষের রসিদ হেফাজত ধারায় সংযুক্ত").assertExists()
        composeTestRule.onNodeWithText("SIMULATED • সিমুলেটেড").assertExists()
    }

    @Test
    fun delayedBoatReplansToNewRendezvousInBothLanguages() {
        composeTestRule.onNodeWithText("English").performClick()
        composeTestRule.onNodeWithText("Handoff").performClick()
        composeTestRule.onNode(hasTestTag("hybrid-fleet-delay")).performScrollTo().performClick()

        composeTestRule.onNodeWithText("Boat delay rerouted the rendezvous").assertExists()
        composeTestRule.onNodeWithText("Local replan R3 → R2 • 18 min").assertExists()
        composeTestRule.onNodeWithText("R2 • 25.0715, 91.7554").assertExists()

        composeTestRule.onNodeWithText("বাংলা").performClick()
        composeTestRule.onNodeWithText("নৌযান বিলম্বে মিলনস্থল পুনর্নির্ধারিত").assertExists()
        composeTestRule.onNodeWithText("স্থানীয় পুনঃপরিকল্পনা R3 → R2 • 18 মিনিট").assertExists()
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
        composeTestRule.runOnIdle {
            meshState.value = meshState.value.copy(
                nearby = NearbyMeshState(
                    running = true,
                    authenticatingNodeIds = setOf("N6"),
                ),
            )
        }
        composeTestRule.onNodeWithText("Verifying signed device credential • N6").assertIsDisplayed()
        composeTestRule.runOnIdle {
            meshState.value = meshState.value.copy(
                nearby = NearbyMeshState(
                    running = true,
                    connectedNodeIds = setOf("N6"),
                    authenticatedPeerKeyIds = mapOf("N6" to "rsa-n6-signing"),
                ),
            )
        }
        composeTestRule.onNodeWithText("Provisioned peer verified • N6").assertIsDisplayed()
        composeTestRule.onNodeWithText("Signing key • rsa-n6-signing").assertIsDisplayed()
        composeTestRule.onNodeWithText("বাংলা").performClick()
        composeTestRule.onNodeWithText("নিবন্ধিত পিয়ার যাচাইকৃত • N6").assertIsDisplayed()
        composeTestRule.onNodeWithText("নিকটবর্তী রিলে বন্ধ করুন").assertIsDisplayed()
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

    @Test
    fun routeScreenRendersVerifiedOfflineGeographicMapInBothLanguages() {
        composeTestRule.onNodeWithText("পথ ও মেশ").performClick()
        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            composeTestRule.onAllNodes(hasTestTag("offline-geographic-map"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNode(hasTestTag("offline-geographic-map")).assertIsDisplayed()
        composeTestRule.onNodeWithText("স্থানীয় OSM • যাচাইকৃত").assertIsDisplayed()
        composeTestRule.onNodeWithText("© OpenStreetMap অবদানকারীরা").assertIsDisplayed()
        composeTestRule.onNode(hasTestTag("offline-map-fallback")).assertDoesNotExist()
        composeTestRule.onNode(hasTestTag("map-renderer-failed")).assertDoesNotExist()

        composeTestRule.onNodeWithText("English").performClick()
        composeTestRule.onNodeWithText("LOCAL OSM • VERIFIED").assertIsDisplayed()
        composeTestRule.onNodeWithText("© OpenStreetMap contributors").assertIsDisplayed()
    }

    @Test
    fun onDeviceRiskPredictionIsVisiblySimulatedAndProactivelyReroutes() {
        composeTestRule.onNodeWithText("পথ ও মেশ").performClick()
        composeTestRule.onNode(hasTestTag("toggle-route-risk")).performScrollTo().performClick()

        composeTestRule.onNodeWithText("E3 ঝুঁকির পূর্বাভাস • আগেই নৌযানে পুনর্নির্দেশ")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("পূর্বাভাস ঝুঁকি E3 • 96.0% / 28.5%")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("ONNX • route-risk-logreg-v1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("নৌযান • N1 → N3 → N4 • E6 + E7").assertExists()

        composeTestRule.onNodeWithText("English").performClick()
        composeTestRule.onNodeWithText("E3 predicted at risk • proactively rerouted by boat")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Prediction adds a route cost; it is not treated as a confirmed closure.")
            .assertExists()
    }

    @Test
    fun routeDelayTriggersHumanConfirmedPreemptionWithoutLosingLanguageState() {
        composeTestRule.onNodeWithText("পথ ও মেশ").performClick()
        composeTestRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        composeTestRule.onNode(hasTestTag("toggle-route-failure")).performClick()
        composeTestRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        composeTestRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }

        composeTestRule.onNodeWithText("P0 SLA ভঙ্গের পূর্বাভাস").assertIsDisplayed()
        composeTestRule.onNode(hasTestTag("sla-countdown")).assertIsDisplayed()
        composeTestRule.onNodeWithText("P2 তারপলিন রেখে দিন: সুনামগঞ্জ সদর ক্যাম্প").assertIsDisplayed()
        composeTestRule.onNodeWithText("সমসাময়িক জরুরি সারি অক্ষত • 1 P0").assertIsDisplayed()
        composeTestRule.onNode(hasTestTag("confirm-preemption")).performClick()
        composeTestRule.onNodeWithText("অগ্রাধিকার পরিবর্তন নিশ্চিত").assertIsDisplayed()
        composeTestRule.onNodeWithText("স্থানীয় কার্গো বরাদ্দ একই লেনদেনে হালনাগাদ").assertIsDisplayed()

        composeTestRule.onNodeWithText("English").performClick()
        composeTestRule.onNodeWithText("Preemption confirmed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Local cargo assignment updated atomically").assertIsDisplayed()
        composeTestRule.onNodeWithText("P2 • Sunamganj Sadar Camp • P0 continues by boat").assertIsDisplayed()
    }

    @Test
    fun clinicRoleShowsBilingualRestrictionAndCannotResolveCoordinatorConflict() {
        authorizationState.value = FieldAuthorizationUiState(
            role = Role.REQUESTER,
            permissions = setOf(Permission.CREATE_REQUEST, Permission.INSPECT_AUDIT),
        )
        composeTestRule.onNode(hasTestTag("simulate-conflict")).performScrollTo().assertIsDisplayed().performClick()

        composeTestRule.onNode(hasTestTag("role-restricted-resolve_conflict")).assertIsDisplayed()
        composeTestRule.onNodeWithText("এই স্বাক্ষরিত ভূমিকায় কাজটি করার অনুমতি নেই।").assertIsDisplayed()
        composeTestRule.onNodeWithText("গন্তব্য করুন: সুনামগঞ্জ সদর ক্যাম্প")
            .assertIsNotEnabled()

        composeTestRule.onNodeWithText("English").performClick()
        composeTestRule.onNodeWithText("This signed role cannot perform this action.").assertIsDisplayed()
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

    private fun podOffer() = DeliveryOfferReady(
        qrCode = "DIGITALDELTA:POD:test",
        deliveryId = "DELTA-2026-0001",
        senderIdentityId = "boat-operator-02",
        recipientIdentityId = "hospital-operator-01",
        senderSigningKeyId = "rsa-signing-key-1",
        payloadSha256 = ByteArray(32) { 1 },
        nonce = ByteArray(16) { 2 },
        timestampUnixMs = 1_800_000_000_000,
        previousReceiptSha256 = ByteArray(32) { 3 },
        simulatedVehicle = true,
    )

    private fun podReceipt() = CustodyReceiptRecord(
        eventId = "custody-event-1",
        deliveryId = "DELTA-2026-0001",
        senderIdentityId = "boat-operator-02",
        recipientIdentityId = "hospital-operator-01",
        previousReceiptSha256 = ByteArray(32) { 3 },
        receiptHash = ByteArray(32) { 4 },
        recordedAtUnixMs = 1_800_000_000_100,
    )

    private fun hybridPlan(): HybridFleetPlan {
        val mission = HybridFleetMission(
            missionId = "mission-drone-demo-01",
            originNodeId = "N1",
            destinationNodeId = "N7",
            boatVehicleId = "boat-02",
            droneVehicleId = "drone-07",
            graph = TransportGraph(
                nodes = listOf(
                    MapNode("N1", "Sylhet Hub", 24.8949, 91.8687),
                    MapNode("N7", "Tanguar Haor Clinic", 25.12, 91.68),
                ),
                edges = listOf(MapEdge("A2", "N1", "N7", EdgeMode.AIRWAY, 28, simulated = true)),
            ),
            rendezvousInputs = HybridFleetInputs(
                boatPosition = GeoPoint(25.04, 91.57),
                droneBase = GeoPoint(24.9632, 91.8668),
                droneDestination = GeoPoint(25.12, 91.68),
                candidates = listOf(
                    NamedPoint("R1", GeoPoint(25.0658, 91.6073)),
                    NamedPoint("R2", GeoPoint(25.0715, 91.7554)),
                    NamedPoint("R3", GeoPoint(25.0200, 91.7000)),
                ),
                boatSpeedKph = 24.0,
                droneSpeedKph = 55.0,
                droneBatteryPercent = 74,
                droneRangeAtFullChargeKm = 60.0,
                reserveBatteryPercent = 20,
            ),
            simulated = true,
        )
        return HybridFleetPlan(
            mission,
            Reachability.DRONE_REQUIRED,
            FleetOrchestrator().computeRendezvous(mission.rendezvousInputs),
        )
    }
}
