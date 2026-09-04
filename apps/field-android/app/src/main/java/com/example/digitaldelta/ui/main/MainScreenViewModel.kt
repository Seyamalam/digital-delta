package com.example.digitaldelta.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.digitaldelta.data.settings.LanguagePreference
import com.example.digitaldelta.data.settings.UserSettingsRepository
import com.example.digitaldelta.data.settings.OfflinePinRepository
import com.example.digitaldelta.data.settings.OfflinePinSnapshot
import com.example.digitaldelta.data.settings.PinVerification
import com.example.digitaldelta.domain.request.CargoDraft
import com.example.digitaldelta.domain.request.ReliefRequestDraft
import com.example.digitaldelta.domain.request.ReliefRequestSubmission
import com.example.digitaldelta.domain.identity.AcceptedRecipient
import com.example.digitaldelta.domain.identity.IdentityProvisioningCoordinator
import com.example.digitaldelta.domain.identity.DeviceProfiles
import com.example.digitaldelta.domain.identity.AuthorizationPolicy
import com.example.digitaldelta.domain.identity.AuthorizationAuditRecord
import com.example.digitaldelta.domain.identity.AuthorizationAuditTrail
import com.example.digitaldelta.domain.identity.DenialReason
import com.example.digitaldelta.domain.identity.Permission
import com.example.digitaldelta.domain.identity.Role
import com.example.digitaldelta.domain.identity.RevocationReceipt
import com.example.digitaldelta.domain.identity.toAuthorizationRole
import com.example.digitaldelta.domain.mesh.RecipientKeyUnavailableException
import com.example.digitaldelta.domain.sync.ConflictCoordinator
import com.example.digitaldelta.domain.sync.ConflictSide
import com.example.digitaldelta.domain.sync.MissionConflictSnapshot
import com.example.digitaldelta.domain.routing.RouteScenario
import com.example.digitaldelta.domain.routing.RouteScenarioSnapshot
import com.example.digitaldelta.domain.triage.TriageWorkflow
import com.example.digitaldelta.domain.triage.TriageWorkflowSnapshot
import com.example.digitaldelta.domain.triage.DefaultTriageWorkflow
import com.example.digitaldelta.domain.triage.StaleRouteEstimateException
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
import com.example.digitaldelta.domain.fleet.BoatDelayReport
import com.example.digitaldelta.domain.fleet.GeoPoint
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
    private val offlinePinRepository: OfflinePinRepository = UnavailableOfflinePinRepository,
    private val authorizationPolicy: AuthorizationPolicy = AuthorizationPolicy(),
    private val authorizationAuditTrail: AuthorizationAuditTrail = UnavailableAuthorizationAuditTrail,
) : ViewModel() {
    val language: StateFlow<LanguagePreference> = settingsRepository.language.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LanguagePreference.BANGLA,
    )

    val languageSelected: StateFlow<Boolean> = settingsRepository.languageSelected.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = true,
    )

    private val mutableRequestQueueState = MutableStateFlow<RequestQueueUiState>(RequestQueueUiState.Idle)
    val requestQueueState: StateFlow<RequestQueueUiState> = mutableRequestQueueState.asStateFlow()

    private val mutableIdentityState = MutableStateFlow<IdentityUiState>(IdentityUiState.Loading)
    val identityState: StateFlow<IdentityUiState> = mutableIdentityState.asStateFlow()

    private val mutableAuthorizationState = MutableStateFlow(FieldAuthorizationUiState())
    val authorizationState: StateFlow<FieldAuthorizationUiState> = mutableAuthorizationState.asStateFlow()

    private val mutableUnlockState = MutableStateFlow<OfflineUnlockUiState>(OfflineUnlockUiState.Loading)
    val unlockState: StateFlow<OfflineUnlockUiState> = mutableUnlockState.asStateFlow()

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
        viewModelScope.launch { loadUnlockState() }
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

    fun configurePin(pin: String) {
        if (mutableUnlockState.value is OfflineUnlockUiState.Working) return
        mutableUnlockState.value = OfflineUnlockUiState.Working
        viewModelScope.launch {
            runCatching { offlinePinRepository.configure(pin) }
                .onSuccess { mutableUnlockState.value = OfflineUnlockUiState.Unlocked }
                .onFailure { mutableUnlockState.value = OfflineUnlockUiState.SetupRequired(invalidPin = true) }
        }
    }

    fun unlock(pin: String) {
        if (mutableUnlockState.value is OfflineUnlockUiState.Working) return
        mutableUnlockState.value = OfflineUnlockUiState.Working
        viewModelScope.launch {
            mutableUnlockState.value = when (val result = offlinePinRepository.verify(pin, System.currentTimeMillis())) {
                PinVerification.Accepted -> OfflineUnlockUiState.Unlocked
                is PinVerification.Rejected -> OfflineUnlockUiState.Locked(
                    attemptsRemaining = result.attemptsRemaining,
                    rejected = true,
                )
                is PinVerification.LockedOut -> OfflineUnlockUiState.LockedOut(result.untilUnixMs)
            }
        }
    }

    fun queueRequest(medicine: Int, ors: Int, tarpaulin: Int, priorityCode: String) {
        viewModelScope.launch {
            if (mutableRequestQueueState.value == RequestQueueUiState.Submitting) return@launch
            val localIdentity = authorize(Permission.CREATE_REQUEST) ?: return@launch
            if (mutableRequestQueueState.value == RequestQueueUiState.Submitting) return@launch
            viewModelScope.launch {
                mutableRequestQueueState.value = RequestQueueUiState.Submitting
                runCatching {
                    requestSubmission.submit(
                        ReliefRequestDraft(
                            requesterNodeId = localIdentity.localNodeId,
                            requesterIdentityId = localIdentity.localIdentityId,
                            originNodeId = localIdentity.localNodeId,
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
    }

    fun selectDeviceProfile(profileCode: String) {
        DeviceProfiles.require(profileCode)
        val previous = mutableIdentityState.value.readySnapshot()
        viewModelScope.launch {
            mutableIdentityState.value = IdentityUiState.Working(previous)
            runCatching { identityCoordinator.selectProfile(profileCode) }
                .onSuccess { snapshot ->
                    val ready = snapshot.toUiState(lastRevocation = previous?.lastRevocation)
                    mutableIdentityState.value = ready
                    refreshAuthorization(ready)
                }
                .onFailure { mutableIdentityState.value = IdentityUiState.Failed(previous, IdentityFailure.KEYSTORE) }
        }
    }

    fun pinAdministrator(code: String) {
        val previous = mutableIdentityState.value.readySnapshot()
        viewModelScope.launch {
            mutableIdentityState.value = IdentityUiState.Working(previous)
            runCatching { identityCoordinator.pinTrustAnchor(code) }
                .onSuccess { snapshot ->
                    val ready = snapshot.toUiState(previous?.acceptedRecipient, previous?.lastRevocation)
                    mutableIdentityState.value = ready
                    refreshAuthorization(ready)
                }
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
                    val ready = snapshot.toUiState(recipient, previous?.lastRevocation)
                    mutableIdentityState.value = ready
                    refreshAuthorization(ready)
                }
                .onFailure { mutableIdentityState.value = IdentityUiState.Failed(previous, IdentityFailure.INVALID_CREDENTIAL) }
        }
    }

    fun importCredentialRevocation(code: String) {
        val previous = mutableIdentityState.value.readySnapshot()
        viewModelScope.launch {
            mutableIdentityState.value = IdentityUiState.Working(previous)
            runCatching { identityCoordinator.acceptCredentialRevocation(code) }
                .onSuccess { receipt ->
                    val snapshot = identityCoordinator.snapshot()
                    val ready = snapshot.toUiState(previous?.acceptedRecipient, receipt)
                    mutableIdentityState.value = ready
                    refreshAuthorization(ready)
                }
                .onFailure { mutableIdentityState.value = IdentityUiState.Failed(previous, IdentityFailure.INVALID_REVOCATION) }
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
            val localIdentity = authorize(Permission.RESOLVE_CONFLICT) ?: return@launch
            viewModelScope.launch {
                runCatching {
                    conflictCoordinator.resolve(
                        conflictId = conflictId,
                        selectedSide = selectedSide,
                        resolverIdentityId = localIdentity.localIdentityId,
                    )
                }.onSuccess { mutableConflictState.value = it }
            }
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

    fun startRelay(onAuthorized: () -> Unit) {
        viewModelScope.launch {
            if (authorize(Permission.RELAY_ENVELOPE) != null) onAuthorized()
        }
    }

    fun confirmPreemption() {
        viewModelScope.launch {
            val proposal = mutableTriageState.value as? TriageWorkflowSnapshot.Proposed ?: return@launch
            val localIdentity = authorize(Permission.CONFIRM_PREEMPTION) ?: return@launch
            if (mutableTriageState.value != proposal) return@launch
            mutableTriageState.value = TriageWorkflowSnapshot.Confirming(
                decision = proposal.decision,
                proposal = proposal.proposal,
            )
            viewModelScope.launch {
                runCatching { triageWorkflow.confirm(proposal, localIdentity.localIdentityId) }
                    .onSuccess { mutableTriageState.value = it }
                    .onFailure { error ->
                        mutableTriageState.value = if (error is StaleRouteEstimateException) {
                            TriageWorkflowSnapshot.RouteRefreshRequired(error.staleDecision)
                        } else {
                            proposal
                        }
                    }
            }
        }
    }

    fun verifyHandoff(tamperForDemo: Boolean = false) {
        viewModelScope.launch {
            if (authorize(Permission.ACCEPT_CUSTODY) == null) return@launch
            val offer = mutableProofOfDeliveryState.value.offerOrNull() ?: return@launch
            if (mutableProofOfDeliveryState.value is ProofOfDeliveryUiState.Verifying) return@launch
            val code = if (tamperForDemo) proofOfDeliveryWorkflow.tamperForDemo(offer.qrCode) else offer.qrCode
            verifyOfferCode(offer, code)
        }
    }

    fun verifyScannedHandoff(code: String) {
        viewModelScope.launch {
            if (authorize(Permission.ACCEPT_CUSTODY) == null) return@launch
            val offer = mutableProofOfDeliveryState.value.offerOrNull() ?: return@launch
            if (mutableProofOfDeliveryState.value is ProofOfDeliveryUiState.Verifying) return@launch
            verifyOfferCode(offer, code)
        }
    }

    private fun verifyOfferCode(offer: DeliveryOfferReady, code: String) {
        mutableProofOfDeliveryState.value = ProofOfDeliveryUiState.Verifying(offer)
        viewModelScope.launch {
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
        viewModelScope.launch {
            if (authorize(Permission.OFFER_CUSTODY) == null) return@launch
            if (mutableProofOfDeliveryState.value is ProofOfDeliveryUiState.Verifying) return@launch
            mutableProofOfDeliveryState.value = ProofOfDeliveryUiState.Loading
            viewModelScope.launch { prepareHandoffInternal() }
        }
    }

    fun advanceHybridFleet() {
        viewModelScope.launch {
            if (authorize(Permission.OFFER_CUSTODY) == null) return@launch
            val current = mutableHybridFleetState.value
            if (current is HybridFleetState.PreparingDroneOffer || current is HybridFleetState.VerifyingTransfer) return@launch
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
    }

    fun reportBoatDelay() {
        viewModelScope.launch {
            if (authorize(Permission.OFFER_CUSTODY) == null) return@launch
            val current = mutableHybridFleetState.value
            if (current !is HybridFleetState.Ready && current !is HybridFleetState.Replanned) return@launch
            viewModelScope.launch {
                runCatching {
                    hybridFleetWorkflow.reportBoatDelay(
                        BoatDelayReport(
                            delayMinutes = 18,
                            observedPosition = GeoPoint(25.0400, 91.8000),
                            simulated = true,
                        ),
                    )
                }.onSuccess { mutableHybridFleetState.value = it }
            }
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
        val loaded = runCatching { identityCoordinator.snapshot().toUiState() }
            .getOrElse { IdentityUiState.Failed(null, IdentityFailure.KEYSTORE) }
        mutableIdentityState.value = loaded
        refreshAuthorization(loaded.readySnapshot())
    }

    private suspend fun authorize(permission: Permission): IdentityUiState.Ready? {
        loadIdentity()
        val ready = mutableIdentityState.value.readySnapshot()
        val credential = ready?.localCredential
        if (ready == null || credential == null) {
            if (ready != null) recordAuthorization(
                ready = ready,
                permission = permission,
                allowed = false,
                reasonCode = AuthorizationFailure.CREDENTIAL_REQUIRED.name,
            )
            mutableAuthorizationState.value = mutableAuthorizationState.value.copy(
                denial = AuthorizationDenial(permission, AuthorizationFailure.CREDENTIAL_REQUIRED),
            )
            return null
        }
        val decision = authorizationPolicy.authorize(credential, permission, System.currentTimeMillis())
        if (!decision.allowed) {
            val failure = decision.denialReason.toAuthorizationFailure()
            recordAuthorization(ready, permission, false, failure.name)
            mutableAuthorizationState.value = mutableAuthorizationState.value.copy(
                denial = AuthorizationDenial(
                    permission,
                    failure,
                ),
            )
            return null
        }
        recordAuthorization(ready, permission, true, "AUTHORIZED")
        mutableAuthorizationState.value = mutableAuthorizationState.value.copy(denial = null)
        return ready
    }

    private fun recordAuthorization(
        ready: IdentityUiState.Ready,
        permission: Permission,
        allowed: Boolean,
        reasonCode: String,
    ) {
        viewModelScope.launch {
            runCatching {
                authorizationAuditTrail.record(
                    actorIdentityId = ready.localIdentityId,
                    actorNodeId = ready.localNodeId,
                    role = ready.localRole,
                    permission = permission,
                    allowed = allowed,
                    reasonCode = reasonCode,
                )
            }.onSuccess { audit ->
                mutableAuthorizationState.value = mutableAuthorizationState.value.copy(lastAudit = audit)
            }
        }
    }

    private fun refreshAuthorization(ready: IdentityUiState.Ready?) {
        val credential = ready?.localCredential
        val role = credential?.role
        val decision = credential?.let {
            authorizationPolicy.authorize(it, Permission.INSPECT_AUDIT, System.currentTimeMillis())
        }
        val permissions = if (credential != null && decision?.allowed == true) {
            authorizationPolicy.allowedPermissions(credential.role)
        } else {
            emptySet()
        }
        mutableAuthorizationState.value = FieldAuthorizationUiState(
            role = role,
            permissions = permissions,
            denial = decision?.denialReason?.let { reason ->
                AuthorizationDenial(Permission.INSPECT_AUDIT, reason.toAuthorizationFailure())
            },
        )
    }

    private suspend fun loadUnlockState() {
        mutableUnlockState.value = runCatching {
            val now = System.currentTimeMillis()
            val snapshot = offlinePinRepository.snapshot(now)
            when {
                !snapshot.configured -> OfflineUnlockUiState.SetupRequired()
                snapshot.lockedUntilUnixMs > now -> OfflineUnlockUiState.LockedOut(snapshot.lockedUntilUnixMs)
                else -> OfflineUnlockUiState.Locked(attemptsRemaining = 5 - snapshot.failedAttempts)
            }
        }.getOrElse { OfflineUnlockUiState.SetupRequired(invalidPin = true) }
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
    override suspend fun reportBoatDelay(report: BoatDelayReport): HybridFleetState = HybridFleetState.Unavailable
    override suspend fun advance(): HybridFleetState = HybridFleetState.Unavailable
    override fun reset(): HybridFleetState = HybridFleetState.Unavailable
}

private object UnavailableOfflinePinRepository : OfflinePinRepository {
    override suspend fun snapshot(nowUnixMs: Long) = OfflinePinSnapshot(true, 0, 0)
    override suspend fun configure(pin: String) = Unit
    override suspend fun verify(pin: String, nowUnixMs: Long): PinVerification = PinVerification.Accepted
}

private fun com.example.digitaldelta.domain.identity.IdentityProvisioningSnapshot.toUiState(
    acceptedRecipient: AcceptedRecipient? = this.acceptedRecipient,
    lastRevocation: RevocationReceipt? = null,
): IdentityUiState.Ready = IdentityUiState.Ready(
    profileCode = profileCode,
    localNodeId = localNodeId,
    localIdentityId = localIdentityId,
    localDisplayName = localDisplayName,
    localRole = localRole,
    localEncryptionKeyId = localEncryptionKeyId,
    enrollmentCode = enrollmentCode,
    trustedIssuerFingerprint = trustedIssuerFingerprint,
    acceptedRecipient = acceptedRecipient,
    localCredential = localCredential,
    lastRevocation = lastRevocation,
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

sealed interface OfflineUnlockUiState {
    data object Loading : OfflineUnlockUiState
    data class SetupRequired(val invalidPin: Boolean = false) : OfflineUnlockUiState
    data object Working : OfflineUnlockUiState
    data class Locked(val attemptsRemaining: Int = 5, val rejected: Boolean = false) : OfflineUnlockUiState
    data class LockedOut(val untilUnixMs: Long) : OfflineUnlockUiState
    data object Unlocked : OfflineUnlockUiState
}

enum class RequestFailure { RECIPIENT_NOT_PROVISIONED, STORAGE_OR_CRYPTO }

enum class IdentityFailure { KEYSTORE, INVALID_TRUST, INVALID_CREDENTIAL, INVALID_REVOCATION }

sealed interface IdentityUiState {
    data object Loading : IdentityUiState
    data class Working(val previous: Ready?) : IdentityUiState
    data class Ready(
        val profileCode: String,
        val localNodeId: String,
        val localIdentityId: String,
        val localDisplayName: String,
        val localRole: com.example.digitaldelta.proto.v1.IdentityRole,
        val localEncryptionKeyId: String,
        val enrollmentCode: String,
        val trustedIssuerFingerprint: String?,
        val acceptedRecipient: AcceptedRecipient?,
        val localCredential: com.example.digitaldelta.domain.identity.OfflineCredential?,
        val lastRevocation: RevocationReceipt? = null,
    ) : IdentityUiState

    data class Failed(val previous: Ready?, val reason: IdentityFailure) : IdentityUiState
}

enum class AuthorizationFailure { CREDENTIAL_REQUIRED, EXPIRED, REVOKED, ROLE_FORBIDDEN }

data class AuthorizationDenial(
    val permission: Permission,
    val reason: AuthorizationFailure,
)

data class FieldAuthorizationUiState(
    val role: Role? = null,
    val permissions: Set<Permission> = emptySet(),
    val denial: AuthorizationDenial? = null,
    val lastAudit: AuthorizationAuditRecord? = null,
) {
    fun allows(permission: Permission): Boolean = permission in permissions
}

private fun DenialReason?.toAuthorizationFailure(): AuthorizationFailure = when (this) {
    DenialReason.EXPIRED -> AuthorizationFailure.EXPIRED
    DenialReason.REVOKED -> AuthorizationFailure.REVOKED
    DenialReason.ROLE_FORBIDDEN,
    null,
    -> AuthorizationFailure.ROLE_FORBIDDEN
}

private object UnavailableAuthorizationAuditTrail : AuthorizationAuditTrail {
    override suspend fun record(
        actorIdentityId: String,
        actorNodeId: String,
        role: com.example.digitaldelta.proto.v1.IdentityRole,
        permission: Permission,
        allowed: Boolean,
        reasonCode: String,
    ) = AuthorizationAuditRecord("audit-unavailable", permission, allowed, reasonCode, 0)

    override suspend fun verifyChain(): Boolean = false
}
