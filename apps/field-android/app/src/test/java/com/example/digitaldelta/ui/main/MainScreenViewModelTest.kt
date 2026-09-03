package com.example.digitaldelta.ui.main

import com.example.digitaldelta.data.settings.LanguagePreference
import com.example.digitaldelta.data.settings.UserSettingsRepository
import com.example.digitaldelta.domain.request.QueueReceipt
import com.example.digitaldelta.domain.request.ReliefRequestDraft
import com.example.digitaldelta.domain.request.ReliefRequestSubmission
import com.example.digitaldelta.domain.identity.AcceptedRecipient
import com.example.digitaldelta.domain.identity.IdentityProvisioningCoordinator
import com.example.digitaldelta.domain.identity.IdentityProvisioningSnapshot
import com.example.digitaldelta.domain.mesh.RecipientKeyUnavailableException
import com.example.digitaldelta.domain.sync.ConflictCoordinator
import com.example.digitaldelta.domain.sync.ConflictSide
import com.example.digitaldelta.domain.sync.MissionConflictSnapshot
import com.example.digitaldelta.domain.routing.EdgeMode
import com.example.digitaldelta.domain.routing.OfflineRouteScenario
import com.example.digitaldelta.domain.routing.MapEdge
import com.example.digitaldelta.domain.routing.MapNode
import com.example.digitaldelta.domain.routing.RouteScenario
import com.example.digitaldelta.domain.routing.TransportGraph
import com.example.digitaldelta.domain.routing.VehicleType
import com.example.digitaldelta.domain.triage.TriageWorkflowSnapshot
import com.example.digitaldelta.domain.triage.TriageWorkflow
import com.example.digitaldelta.domain.triage.DefaultTriageWorkflow
import com.example.digitaldelta.domain.pod.CustodyChain
import com.example.digitaldelta.domain.pod.CustodyReceiptRecord
import com.example.digitaldelta.domain.pod.DeliveryOfferReady
import com.example.digitaldelta.domain.pod.DeliveryOfferRejection
import com.example.digitaldelta.domain.pod.DeliveryReceiptResult
import com.example.digitaldelta.domain.pod.ProofOfDeliveryWorkflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `language starts Bangla and persists English selection`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository()
        val viewModel = MainScreenViewModel(
            repository,
            FakeRequestSubmission(),
            FakeIdentityCoordinator(),
            FakeConflictCoordinator(),
            FakeRouteScenario(),
        )

        assertEquals(LanguagePreference.BANGLA, viewModel.language.value)
        viewModel.setBangla(false)
        advanceUntilIdle()

        assertEquals(LanguagePreference.ENGLISH, repository.languageState.value)
        assertEquals(LanguagePreference.ENGLISH, viewModel.language.value)
    }

    @Test
    fun `queue request exposes durable receipt to the interface`() = runTest(dispatcher) {
        val submission = FakeRequestSubmission()
        val viewModel = MainScreenViewModel(
            FakeSettingsRepository(),
            submission,
            FakeIdentityCoordinator(),
            FakeConflictCoordinator(),
            FakeRouteScenario(),
        )

        viewModel.queueRequest(medicine = 11, ors = 20, tarpaulin = 5, priorityCode = "P0")
        advanceUntilIdle()

        assertEquals(11, submission.received?.cargo?.first { it.itemCode == "medicine" }?.quantity)
        assertEquals(RequestQueueUiState.Queued("request-9", "message-9"), viewModel.requestQueueState.value)
    }

    @Test
    fun `request reports that destination identity must be provisioned`() = runTest(dispatcher) {
        val viewModel = MainScreenViewModel(
            FakeSettingsRepository(),
            FakeRequestSubmission(RecipientKeyUnavailableException("N6")),
            FakeIdentityCoordinator(),
            FakeConflictCoordinator(),
            FakeRouteScenario(),
        )

        viewModel.queueRequest(medicine = 10, ors = 20, tarpaulin = 5, priorityCode = "P0")
        advanceUntilIdle()

        assertEquals(
            RequestQueueUiState.Failed(RequestFailure.RECIPIENT_NOT_PROVISIONED),
            viewModel.requestQueueState.value,
        )
    }

    @Test
    fun `identity screen exposes enrollment then reflects trusted recipient import`() = runTest(dispatcher) {
        val coordinator = FakeIdentityCoordinator()
        val viewModel = MainScreenViewModel(
            FakeSettingsRepository(),
            FakeRequestSubmission(),
            coordinator,
            FakeConflictCoordinator(),
            FakeRouteScenario(),
        )
        advanceUntilIdle()

        assertEquals("enrollment-code", (viewModel.identityState.value as IdentityUiState.Ready).enrollmentCode)
        viewModel.pinAdministrator("trust-code")
        advanceUntilIdle()
        assertEquals("admin-abcd", (viewModel.identityState.value as IdentityUiState.Ready).trustedIssuerFingerprint)

        viewModel.importRecipientCredential("credential-code")
        advanceUntilIdle()
        val ready = viewModel.identityState.value as IdentityUiState.Ready
        assertEquals("Habiganj Medical", ready.acceptedRecipient?.displayName)
        assertEquals("credential-code", coordinator.importedCode)
    }

    @Test
    fun `failed trust code keeps identity available for a successful retry`() = runTest(dispatcher) {
        val coordinator = FakeIdentityCoordinator()
        val viewModel = MainScreenViewModel(
            FakeSettingsRepository(),
            FakeRequestSubmission(),
            coordinator,
            FakeConflictCoordinator(),
            FakeRouteScenario(),
        )
        advanceUntilIdle()

        viewModel.pinAdministrator("invalid")
        advanceUntilIdle()
        val failed = viewModel.identityState.value as IdentityUiState.Failed
        assertEquals("enrollment-code", failed.previous?.enrollmentCode)

        viewModel.pinAdministrator("still-invalid")
        advanceUntilIdle()
        assertEquals(
            "enrollment-code",
            (viewModel.identityState.value as IdentityUiState.Failed).previous?.enrollmentCode,
        )

        viewModel.pinAdministrator("trust-code")
        advanceUntilIdle()
        assertEquals(
            "admin-abcd",
            (viewModel.identityState.value as IdentityUiState.Ready).trustedIssuerFingerprint,
        )
    }

    @Test
    fun `conflict drill requires and persists an explicit human choice`() = runTest(dispatcher) {
        val conflicts = FakeConflictCoordinator()
        val viewModel = MainScreenViewModel(
            FakeSettingsRepository(),
            FakeRequestSubmission(),
            FakeIdentityCoordinator(),
            conflicts,
            FakeRouteScenario(),
        )
        advanceUntilIdle()

        viewModel.simulateConflict()
        advanceUntilIdle()
        assertEquals("conflict-1", (viewModel.conflictState.value as MissionConflictSnapshot.Open).conflictId)

        viewModel.resolveConflict("conflict-1", ConflictSide.RIGHT)
        advanceUntilIdle()
        assertEquals("N6", (viewModel.conflictState.value as MissionConflictSnapshot.Resolved).selectedValue)
    }

    @Test
    fun `route failure replaces unreachable truck with measured boat fallback`() = runTest(dispatcher) {
        val viewModel = MainScreenViewModel(
            FakeSettingsRepository(),
            FakeRequestSubmission(),
            FakeIdentityCoordinator(),
            FakeConflictCoordinator(),
            FakeRouteScenario(),
        )

        assertEquals(VehicleType.TRUCK, viewModel.routeState.value.decision.routeVehicle)
        viewModel.toggleRouteFailure()
        assertEquals(VehicleType.BOAT, viewModel.routeState.value.decision.routeVehicle)
        assertEquals(setOf("E3"), viewModel.routeState.value.failedEdgeIds)
        assertEquals(295, (viewModel.triageState.value as TriageWorkflowSnapshot.Proposed).decision.slowedArrivalMinutes)
    }

    @Test
    fun `preemption confirmation ignores duplicate taps while recording locally`() = runTest(dispatcher) {
        val triage = CountingTriageWorkflow()
        val viewModel = MainScreenViewModel(
            FakeSettingsRepository(),
            FakeRequestSubmission(),
            FakeIdentityCoordinator(),
            FakeConflictCoordinator(),
            FakeRouteScenario(),
            triage,
        )

        viewModel.toggleRouteFailure()
        viewModel.confirmPreemption()
        assertEquals(true, viewModel.triageState.value is TriageWorkflowSnapshot.Confirming)

        viewModel.confirmPreemption()
        advanceUntilIdle()

        assertEquals(1, triage.confirmCalls)
        assertEquals(true, viewModel.triageState.value is TriageWorkflowSnapshot.Confirmed)
    }

    @Test
    fun `handoff verifies once then reports replay without losing receipt chain`() = runTest(dispatcher) {
        val proof = FakeProofOfDeliveryWorkflow()
        val viewModel = MainScreenViewModel(
            FakeSettingsRepository(),
            FakeRequestSubmission(),
            FakeIdentityCoordinator(),
            FakeConflictCoordinator(),
            FakeRouteScenario(),
            DefaultTriageWorkflow(),
            proof,
        )
        advanceUntilIdle()
        assertEquals(true, viewModel.proofOfDeliveryState.value is ProofOfDeliveryUiState.Ready)

        viewModel.verifyHandoff()
        assertEquals(true, viewModel.proofOfDeliveryState.value is ProofOfDeliveryUiState.Verifying)
        viewModel.verifyHandoff()
        advanceUntilIdle()
        assertEquals(1, proof.verifyCalls)
        assertEquals(true, viewModel.proofOfDeliveryState.value is ProofOfDeliveryUiState.Verified)

        viewModel.verifyHandoff()
        advanceUntilIdle()
        val replay = viewModel.proofOfDeliveryState.value as ProofOfDeliveryUiState.Rejected
        assertEquals(DeliveryOfferRejection.REPLAY_REJECTED, replay.reason)
        assertEquals(1, replay.preservedChain.size)
    }
}

private class CountingTriageWorkflow : TriageWorkflow {
    private val delegate = DefaultTriageWorkflow()
    var confirmCalls = 0

    override fun evaluate(routeEtaMinutes: Int): TriageWorkflowSnapshot = delegate.evaluate(routeEtaMinutes)

    override suspend fun confirm(
        proposal: TriageWorkflowSnapshot.Proposed,
        confirmerIdentityId: String,
    ): TriageWorkflowSnapshot.Confirmed {
        confirmCalls += 1
        return TriageWorkflowSnapshot.Confirmed(
            decision = proposal.decision,
            proposal = proposal.proposal,
            eventId = "preemption-event-1",
            confirmedAtUnixMs = 1_800_000_000_000,
        )
    }
}

private class FakeProofOfDeliveryWorkflow : ProofOfDeliveryWorkflow {
    var verifyCalls = 0
    private val receipt = CustodyReceiptRecord(
        eventId = "custody-event-1",
        deliveryId = "DELTA-2026-0001",
        senderIdentityId = "boat-operator-02",
        recipientIdentityId = "hospital-operator-01",
        previousReceiptSha256 = ByteArray(32) { 1 },
        receiptHash = ByteArray(32) { 2 },
        recordedAtUnixMs = 1_800_000_000_100,
    )

    override suspend fun prepare() = DeliveryOfferReady(
        qrCode = "DIGITALDELTA:POD:test",
        deliveryId = "DELTA-2026-0001",
        senderIdentityId = "boat-operator-02",
        recipientIdentityId = "hospital-operator-01",
        senderSigningKeyId = "rsa-signing-key-1",
        payloadSha256 = ByteArray(32) { 3 },
        nonce = ByteArray(16) { 4 },
        timestampUnixMs = 1_800_000_000_000,
        previousReceiptSha256 = ByteArray(32) { 1 },
        simulatedVehicle = true,
    )

    override suspend fun verify(code: String): DeliveryReceiptResult {
        verifyCalls += 1
        return if (verifyCalls == 1) {
            DeliveryReceiptResult.Verified(receipt, listOf(receipt))
        } else {
            DeliveryReceiptResult.Rejected(DeliveryOfferRejection.REPLAY_REJECTED, listOf(receipt))
        }
    }

    override suspend fun reconstructChain() = CustodyChain(listOf(receipt), valid = true)
    override fun tamperForDemo(code: String): String = "$code-tampered"
}

private class FakeSettingsRepository : UserSettingsRepository {
    val languageState = MutableStateFlow(LanguagePreference.BANGLA)
    override val language: Flow<LanguagePreference> = languageState

    override suspend fun setLanguage(language: LanguagePreference) {
        languageState.value = language
    }
}

private class FakeRequestSubmission(private val failure: Throwable? = null) : ReliefRequestSubmission {
    var received: ReliefRequestDraft? = null

    override suspend fun submit(draft: ReliefRequestDraft): QueueReceipt {
        failure?.let { throw it }
        received = draft
        return QueueReceipt("request-9", "message-9")
    }
}

private class FakeIdentityCoordinator : IdentityProvisioningCoordinator {
    var importedCode: String? = null
    private var fingerprint: String? = null

    override suspend fun snapshot(): IdentityProvisioningSnapshot = IdentityProvisioningSnapshot(
        localNodeId = "N4",
        localEncryptionKeyId = "rsa-local-1",
        enrollmentCode = "enrollment-code",
        trustedIssuerFingerprint = fingerprint,
    )

    override suspend fun pinTrustAnchor(code: String): IdentityProvisioningSnapshot {
        require(code == "trust-code")
        fingerprint = "admin-abcd"
        return snapshot()
    }

    override suspend fun acceptRecipientCredential(code: String): AcceptedRecipient {
        importedCode = code
        return AcceptedRecipient("N6", "Habiganj Medical", "rsa-recipient-1")
    }
}

private class FakeConflictCoordinator : ConflictCoordinator {
    private var current: MissionConflictSnapshot = MissionConflictSnapshot.Idle

    override suspend fun snapshot(): MissionConflictSnapshot = current

    override suspend fun simulateDestinationConflict(): MissionConflictSnapshot =
        MissionConflictSnapshot.Open(
            conflictId = "conflict-1",
            missionId = "mission-sylhet-01",
            field = com.example.digitaldelta.domain.sync.MissionField.DESTINATION,
            leftValue = "N3",
            rightValue = "N6",
            leftClock = com.example.digitaldelta.domain.sync.VectorClock(mapOf("phone-a" to 1L)),
            rightClock = com.example.digitaldelta.domain.sync.VectorClock(mapOf("phone-b" to 1L)),
        ).also { current = it }

    override suspend fun resolve(
        conflictId: String,
        selectedSide: ConflictSide,
        resolverIdentityId: String,
    ): MissionConflictSnapshot = MissionConflictSnapshot.Resolved(
        conflictId = conflictId,
        missionId = "mission-sylhet-01",
        field = com.example.digitaldelta.domain.sync.MissionField.DESTINATION,
        selectedValue = if (selectedSide == ConflictSide.RIGHT) "N6" else "N3",
        resolverIdentityId = resolverIdentityId,
        convergenceHash = "a4e96ff28c89d214d02a3c87f01778e7ad3f139307376afaacd1a10da45a9b22",
    ).also { current = it }
}

private class FakeRouteScenario : RouteScenario by OfflineRouteScenario(
    TransportGraph(
        nodes = listOf(
            MapNode("N1", "Sylhet Hub", 24.8, 91.8),
            MapNode("N2", "Airport", 24.9, 91.8),
            MapNode("N3", "Sunamganj", 25.0, 91.4),
            MapNode("N4", "Companyganj", 25.0, 91.7),
        ),
        edges = listOf(
            MapEdge("E1", "N1", "N2", EdgeMode.ROAD, 20),
            MapEdge("E3", "N2", "N4", EdgeMode.ROAD, 45),
            MapEdge("E6", "N1", "N3", EdgeMode.WATERWAY, 150),
            MapEdge("E7", "N3", "N4", EdgeMode.WATERWAY, 50),
        ),
    ),
)
