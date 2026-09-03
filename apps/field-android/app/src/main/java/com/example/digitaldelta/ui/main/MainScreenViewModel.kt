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
import com.example.digitaldelta.proto.v1.PriorityClass
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
@HiltViewModel
class MainScreenViewModel @Inject constructor(
    private val settingsRepository: UserSettingsRepository,
    private val requestSubmission: ReliefRequestSubmission,
    private val identityCoordinator: IdentityProvisioningCoordinator,
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

    init {
        viewModelScope.launch { loadIdentity() }
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
            }.onFailure {
                mutableRequestQueueState.value = RequestQueueUiState.Failed
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
    data object Failed : RequestQueueUiState
}

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
