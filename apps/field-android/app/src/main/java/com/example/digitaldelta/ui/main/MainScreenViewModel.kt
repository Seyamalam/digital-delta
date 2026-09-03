package com.example.digitaldelta.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.digitaldelta.data.settings.LanguagePreference
import com.example.digitaldelta.data.settings.UserSettingsRepository
import com.example.digitaldelta.domain.request.CargoDraft
import com.example.digitaldelta.domain.request.ReliefRequestDraft
import com.example.digitaldelta.domain.request.ReliefRequestSubmission
import com.example.digitaldelta.domain.identity.AcceptedRecipient
import com.example.digitaldelta.domain.identity.IdentityProvisioningCoordinator
import com.example.digitaldelta.domain.mesh.RecipientKeyUnavailableException
import com.example.digitaldelta.domain.sync.ConflictCoordinator
import com.example.digitaldelta.domain.sync.ConflictSide
import com.example.digitaldelta.domain.sync.MissionConflictSnapshot
import com.example.digitaldelta.domain.routing.RouteScenario
import com.example.digitaldelta.domain.routing.RouteScenarioSnapshot
import com.example.digitaldelta.domain.triage.TriageWorkflow
import com.example.digitaldelta.domain.triage.TriageWorkflowSnapshot
import com.example.digitaldelta.domain.triage.DefaultTriageWorkflow
import com.example.digitaldelta.domain.pod.CustodyReceiptRecord
import com.example.digitaldelta.domain.pod.DeliveryOfferReady
import com.example.digitaldelta.domain.pod.DeliveryOfferRejection
import com.example.digitaldelta.domain.pod.DeliveryReceiptResult
import com.example.digitaldelta.domain.pod.ProofOfDeliveryWorkflow
import com.example.digitaldelta.domain.prediction.RouteRiskClassifier
import com.example.digitaldelta.domain.prediction.RouteRiskFeatures
import com.example.digitaldelta.domain.prediction.RouteRiskPrediction
import com.example.digitaldelta.domain.prediction.RouteRiskPredictor
import com.example.digitaldelta.domain.routing.DynamicRouteDecision
import com.example.digitaldelta.domain.fleet.HybridFleetState
import com.example.digitaldelta.domain.fleet.HybridFleetWorkflow
import com.example.digitaldelta.proto.v1.PriorityClass
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
@HiltViewModel
class MainScreenViewModel @Inject constructor(
    private val settingsRepository: UserSettingsRepository,
    private val requestSubmission: ReliefRequestSubmission,
    private val identityCoordinator: IdentityProvisioningCoordinator,
    private val conflictCoordinator: ConflictCoordinator,
    private val routeScenario: RouteScenario,
    private val triageWorkflow: TriageWorkflow = DefaultTriageWorkflow(),
    private val proofOfDeliveryWorkflow: ProofOfDeliveryWorkflow = UnavailableProofOfDeliveryWorkflow,
    private val routeRiskPredictor: RouteRiskPredictor = RouteRiskClassifier(threshold = 0.65),
    private val hybridFleetWorkflow: HybridFleetWorkflow = UnavailableHybridFleetWorkflow,
) : ViewModel() {
    val language: StateFlow<LanguagePreference> = settingsRepository.language.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LanguagePreference.BANGLA,
    )

    private val mutableRequestQueueState = MutableStateFlow<RequestQueueUiState>(RequestQueueUiState.Idle)
    val requestQueueState: StateFlow<RequestQueueUiState> = mutableRequestQueueState.asStateFlow()

    private val mutableIdentityState = MutableStateFlow<IdentityUiState>(IdentityUiState.Loading)
    val identityState: StateFlow<IdentityUiState> = mutableIdentityState.asStateFlow()

    private val mutableConflictState = MutableStateFlow<MissionConflictSnapshot>(MissionConflictSnapshot.Idle)
    val conflictState: StateFlow<MissionConflictSnapshot> = mutableConflictState.asStateFlow()

    private val mutableRouteState = MutableStateFlow(routeScenario.snapshot())
    val routeState: StateFlow<RouteScenarioSnapshot> = mutableRouteState.asStateFlow()

    private val mutableTriageState = MutableStateFlow(
        triageWorkflow.evaluate(mutableRouteState.value.decision.route.totalMinutes),
    )
    val triageState: StateFlow<TriageWorkflowSnapshot> = mutableTriageState.asStateFlow()

    private val mutableProofOfDeliveryState = MutableStateFlow<ProofOfDeliveryUiState>(ProofOfDeliveryUiState.Loading)
    val proofOfDeliveryState: StateFlow<ProofOfDeliveryUiState> = mutableProofOfDeliveryState.asStateFlow()

    private val mutableRouteRiskState = MutableStateFlow<RouteRiskUiState>(RouteRiskUiState.Idle)
    val routeRiskState: StateFlow<RouteRiskUiState> = mutableRouteRiskState.asStateFlow()

    private val mutableHybridFleetState = MutableStateFlow(hybridFleetWorkflow.snapshot())
    val hybridFleetState: StateFlow<HybridFleetState> = mutableHybridFleetState.asStateFlow()

    init {
        viewModelScope.launch { loadIdentity() }
        viewModelScope.launch { mutableConflictState.value = conflictCoordinator.snapshot() }
        viewModelScope.launch { prepareHandoffInternal() }
    }

    fun setBangla(useBangla: Boolean) {
        viewModelScope.launch {
            settingsRepository.setLanguage(
                if (useBangla) LanguagePreference.BANGLA else LanguagePreference.ENGLISH,
            )
        }
    }

    fun queueRequest(medicine: Int, ors: Int, tarpaulin: Int, priorityCode: String) {
        if (mutableRequestQueueState.value == RequestQueueUiState.Submitting) return
        viewModelScope.launch {
            mutableRequestQueueState.value = RequestQueueUiState.Submitting
            runCatching {
                requestSubmission.submit(
                    ReliefRequestDraft(
                        requesterNodeId = "clinic-sylhet-01",
                        originNodeId = "N4",
                        destinationNodeId = "N6",
                        cargo = listOf(
                            CargoDraft("medicine", medicine, "pack"),
                            CargoDraft("ors", ors, "sachet"),
                            CargoDraft("tarpaulin", tarpaulin, "sheet"),
                        ).filter { it.quantity > 0 },
                        priority = priorityCode.toPriority(),
                        simulated = false,
                        scenarioSeed = "",
                    ),
                )
            }.onSuccess { receipt ->
                mutableRequestQueueState.value = RequestQueueUiState.Queued(receipt.requestId, receipt.messageId)
            }.onFailure { error ->
                mutableRequestQueueState.value = RequestQueueUiState.Failed(
                    if (error is RecipientKeyUnavailableException) {
                        RequestFailure.RECIPIENT_NOT_PROVISIONED
                    } else {
                        RequestFailure.STORAGE_OR_CRYPTO
                    },
                )
            }
        }
    }

    fun pinAdministrator(code: String) {
        val previous = mutableIdentityState.value.readySnapshot()
        viewModelScope.launch {
            mutableIdentityState.value = IdentityUiState.Working(previous)
            runCatching { identityCoordinator.pinTrustAnchor(code) }
                .onSuccess { snapshot -> mutableIdentityState.value = snapshot.toUiState(previous?.acceptedRecipient) }
                .onFailure { mutableIdentityState.value = IdentityUiState.Failed(previous, IdentityFailure.INVALID_TRUST) }
        }
    }

    fun importRecipientCredential(code: String) {
        val previous = mutableIdentityState.value.readySnapshot()
        viewModelScope.launch {
            mutableIdentityState.value = IdentityUiState.Working(previous)
            runCatching { identityCoordinator.acceptRecipientCredential(code) }
                .onSuccess { recipient ->
                    val snapshot = identityCoordinator.snapshot()
                    mutableIdentityState.value = snapshot.toUiState(recipient)
                }
                .onFailure { mutableIdentityState.value = IdentityUiState.Failed(previous, IdentityFailure.INVALID_CREDENTIAL) }
        }
    }

    fun simulateConflict() {
        viewModelScope.launch {
            runCatching { conflictCoordinator.simulateDestinationConflict() }
                .onSuccess { mutableConflictState.value = it }
        }
    }

    fun resolveConflict(conflictId: String, selectedSide: ConflictSide) {
        viewModelScope.launch {
            runCatching {
                conflictCoordinator.resolve(
                    conflictId = conflictId,
                    selectedSide = selectedSide,
                    resolverIdentityId = "coordinator-sylhet-01",
                )
            }.onSuccess { mutableConflictState.value = it }
        }
    }

    fun toggleRouteFailure() {
        if (mutableRouteRiskState.value !is RouteRiskUiState.Idle) {
            mutableRouteState.value = routeScenario.clearPredictedRisk()
            mutableRouteRiskState.value = RouteRiskUiState.Idle
        }
        mutableRouteState.value = if ("E3" in mutableRouteState.value.failedEdgeIds) {
            routeScenario.reset()
        } else {
            routeScenario.triggerEdgeFailure("E3")
        }
        mutableTriageState.value = triageWorkflow.evaluate(mutableRouteState.value.decision.route.totalMinutes)
    }

    fun toggleRouteRisk() {
        when (mutableRouteRiskState.value) {
            is RouteRiskUiState.Evaluating -> return
            is RouteRiskUiState.Active,
            is RouteRiskUiState.Failed,
            -> {
                mutableRouteState.value = routeScenario.clearPredictedRisk()
                mutableRouteRiskState.value = RouteRiskUiState.Idle
                mutableTriageState.value = triageWorkflow.evaluate(
                    mutableRouteState.value.decision.route.totalMinutes,
                )
            }
            RouteRiskUiState.Idle -> {
                val features = RouteRiskFeatures(
                    rainfallMmPerHour = 82.0,
                    elevationMeters = 3.0,
                    soilSaturation = 0.92,
                    simulated = true,
                )
                mutableRouteRiskState.value = RouteRiskUiState.Evaluating(features)
                viewModelScope.launch {
                    runCatching {
                        withContext(Dispatchers.Default) { routeRiskPredictor.predict(features) }
                    }.onSuccess { prediction ->
                        val before = mutableRouteState.value.decision
                        val updated = routeScenario.applyPredictedRisk("E3", prediction.probability)
                        mutableRouteState.value = updated
                        mutableRouteRiskState.value = RouteRiskUiState.Active(
                            edgeId = "E3",
                            features = features,
                            prediction = prediction,
                            previousDecision = before,
                            updatedDecision = updated.decision,
                        )
                        mutableTriageState.value = triageWorkflow.evaluate(
                            updated.decision.route.totalMinutes,
                        )
                    }.onFailure {
                        mutableRouteRiskState.value = RouteRiskUiState.Failed(features)
                    }
                }
            }
        }
    }

    fun confirmPreemption() {
        val proposal = mutableTriageState.value as? TriageWorkflowSnapshot.Proposed ?: return
        mutableTriageState.value = TriageWorkflowSnapshot.Confirming(
            decision = proposal.decision,
            proposal = proposal.proposal,
        )
        viewModelScope.launch {
            runCatching { triageWorkflow.confirm(proposal, "coordinator-sylhet-01") }
                .onSuccess { mutableTriageState.value = it }
                .onFailure { mutableTriageState.value = proposal }
        }
    }

    fun verifyHandoff(tamperForDemo: Boolean = false) {
        val offer = mutableProofOfDeliveryState.value.offerOrNull() ?: return
        if (mutableProofOfDeliveryState.value is ProofOfDeliveryUiState.Verifying) return
        mutableProofOfDeliveryState.value = ProofOfDeliveryUiState.Verifying(offer)
        viewModelScope.launch {
            val code = if (tamperForDemo) {
                proofOfDeliveryWorkflow.tamperForDemo(offer.qrCode)
            } else {
                offer.qrCode
            }
            runCatching { proofOfDeliveryWorkflow.verify(code) }
                .onSuccess { result ->
                    mutableProofOfDeliveryState.value = when (result) {
                        is DeliveryReceiptResult.Verified -> ProofOfDeliveryUiState.Verified(
                            offer = offer,
                            receipt = result.receipt,
                            chain = result.chain,
                        )
                        is DeliveryReceiptResult.Rejected -> ProofOfDeliveryUiState.Rejected(
                            offer = offer,
                            reason = result.reason,
                            preservedChain = result.preservedChain,
                        )
                    }
                }
                .onFailure { mutableProofOfDeliveryState.value = ProofOfDeliveryUiState.Failed }
        }
    }

    fun prepareNextHandoff() {
        if (mutableProofOfDeliveryState.value is ProofOfDeliveryUiState.Verifying) return
        mutableProofOfDeliveryState.value = ProofOfDeliveryUiState.Loading
        viewModelScope.launch { prepareHandoffInternal() }
    }

    fun advanceHybridFleet() {
        val current = mutableHybridFleetState.value
        if (current is HybridFleetState.PreparingDroneOffer || current is HybridFleetState.VerifyingTransfer) return
        val intermediate = when (current) {
            is HybridFleetState.BoatArrived -> HybridFleetState.PreparingDroneOffer(current.plan)
            is HybridFleetState.DroneArrived -> HybridFleetState.VerifyingTransfer(current.plan, current.offer)
            else -> null
        }
        if (intermediate != null) mutableHybridFleetState.value = intermediate
        viewModelScope.launch {
            if (intermediate != null) delay(650)
            runCatching { hybridFleetWorkflow.advance() }
                .onSuccess { mutableHybridFleetState.value = it }
                .onFailure { mutableHybridFleetState.value = current }
        }
    }

    fun resetHybridFleet() {
        mutableHybridFleetState.value = hybridFleetWorkflow.reset()
    }

    private suspend fun prepareHandoffInternal() {
        mutableProofOfDeliveryState.value = runCatching { proofOfDeliveryWorkflow.prepare() }
            .fold(
                onSuccess = ProofOfDeliveryUiState::Ready,
                onFailure = { ProofOfDeliveryUiState.Failed },
            )
    }

    private suspend fun loadIdentity() {
        mutableIdentityState.value = runCatching { identityCoordinator.snapshot().toUiState() }
            .getOrElse { IdentityUiState.Failed(null, IdentityFailure.KEYSTORE) }
    }

    private fun String.toPriority(): PriorityClass = when (this) {
        "P0" -> PriorityClass.PRIORITY_CLASS_P0
        "P1" -> PriorityClass.PRIORITY_CLASS_P1
        "P2" -> PriorityClass.PRIORITY_CLASS_P2
        "P3" -> PriorityClass.PRIORITY_CLASS_P3
        else -> error("unknown priority code")
    }
}

sealed interface ProofOfDeliveryUiState {
    data object Loading : ProofOfDeliveryUiState
    data class Ready(val offer: DeliveryOfferReady) : ProofOfDeliveryUiState
    data class Verifying(val offer: DeliveryOfferReady) : ProofOfDeliveryUiState
    data class Verified(
        val offer: DeliveryOfferReady,
        val receipt: CustodyReceiptRecord,
        val chain: List<CustodyReceiptRecord>,
    ) : ProofOfDeliveryUiState
    data class Rejected(
        val offer: DeliveryOfferReady,
        val reason: DeliveryOfferRejection,
        val preservedChain: List<CustodyReceiptRecord>,
    ) : ProofOfDeliveryUiState
    data object Failed : ProofOfDeliveryUiState
}

sealed interface RouteRiskUiState {
    data object Idle : RouteRiskUiState
    data class Evaluating(val features: RouteRiskFeatures) : RouteRiskUiState
    data class Active(
        val edgeId: String,
        val features: RouteRiskFeatures,
        val prediction: RouteRiskPrediction,
        val previousDecision: DynamicRouteDecision,
        val updatedDecision: DynamicRouteDecision,
    ) : RouteRiskUiState
    data class Failed(val features: RouteRiskFeatures) : RouteRiskUiState
}

private fun ProofOfDeliveryUiState.offerOrNull(): DeliveryOfferReady? = when (this) {
    is ProofOfDeliveryUiState.Ready -> offer
    is ProofOfDeliveryUiState.Verifying -> offer
    is ProofOfDeliveryUiState.Verified -> offer
    is ProofOfDeliveryUiState.Rejected -> offer
    ProofOfDeliveryUiState.Loading,
    ProofOfDeliveryUiState.Failed,
    -> null
}

private object UnavailableProofOfDeliveryWorkflow : ProofOfDeliveryWorkflow {
    override suspend fun prepare(): DeliveryOfferReady = error("proof-of-delivery workflow is unavailable")
    override suspend fun verify(code: String): DeliveryReceiptResult = error("proof-of-delivery workflow is unavailable")
    override suspend fun reconstructChain() = error("proof-of-delivery workflow is unavailable")
    override fun tamperForDemo(code: String): String = error("proof-of-delivery workflow is unavailable")
}

private object UnavailableHybridFleetWorkflow : HybridFleetWorkflow {
    override fun snapshot(): HybridFleetState = HybridFleetState.Unavailable
    override suspend fun advance(): HybridFleetState = HybridFleetState.Unavailable
    override fun reset(): HybridFleetState = HybridFleetState.Unavailable
}

private fun com.example.digitaldelta.domain.identity.IdentityProvisioningSnapshot.toUiState(
    acceptedRecipient: AcceptedRecipient? = this.acceptedRecipient,
): IdentityUiState.Ready = IdentityUiState.Ready(
    localNodeId = localNodeId,
    localEncryptionKeyId = localEncryptionKeyId,
    enrollmentCode = enrollmentCode,
    trustedIssuerFingerprint = trustedIssuerFingerprint,
    acceptedRecipient = acceptedRecipient,
)

private fun IdentityUiState.readySnapshot(): IdentityUiState.Ready? = when (this) {
    is IdentityUiState.Ready -> this
    is IdentityUiState.Working -> previous
    is IdentityUiState.Failed -> previous
    IdentityUiState.Loading -> null
}

sealed interface RequestQueueUiState {
    data object Idle : RequestQueueUiState
    data object Submitting : RequestQueueUiState
    data class Queued(val requestId: String, val messageId: String) : RequestQueueUiState
    data class Failed(val reason: RequestFailure) : RequestQueueUiState
}

enum class RequestFailure { RECIPIENT_NOT_PROVISIONED, STORAGE_OR_CRYPTO }

enum class IdentityFailure { KEYSTORE, INVALID_TRUST, INVALID_CREDENTIAL }

sealed interface IdentityUiState {
    data object Loading : IdentityUiState
    data class Working(val previous: Ready?) : IdentityUiState
    data class Ready(
        val localNodeId: String,
        val localEncryptionKeyId: String,
        val enrollmentCode: String,
        val trustedIssuerFingerprint: String?,
        val acceptedRecipient: AcceptedRecipient?,
    ) : IdentityUiState

    data class Failed(val previous: Ready?, val reason: IdentityFailure) : IdentityUiState
}
