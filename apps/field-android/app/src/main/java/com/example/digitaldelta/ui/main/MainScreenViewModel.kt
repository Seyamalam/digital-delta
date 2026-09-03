package com.example.digitaldelta.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.digitaldelta.data.settings.LanguagePreference
import com.example.digitaldelta.data.settings.UserSettingsRepository
import com.example.digitaldelta.domain.request.CargoDraft
import com.example.digitaldelta.domain.request.ReliefRequestDraft
import com.example.digitaldelta.domain.request.ReliefRequestSubmission
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
) : ViewModel() {
    val language: StateFlow<LanguagePreference> = settingsRepository.language.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LanguagePreference.BANGLA,
    )

    private val mutableRequestQueueState = MutableStateFlow<RequestQueueUiState>(RequestQueueUiState.Idle)
    val requestQueueState: StateFlow<RequestQueueUiState> = mutableRequestQueueState.asStateFlow()

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

    private fun String.toPriority(): PriorityClass = when (this) {
        "P0" -> PriorityClass.PRIORITY_CLASS_P0
        "P1" -> PriorityClass.PRIORITY_CLASS_P1
        "P2" -> PriorityClass.PRIORITY_CLASS_P2
        "P3" -> PriorityClass.PRIORITY_CLASS_P3
        else -> error("unknown priority code")
    }
}

sealed interface RequestQueueUiState {
    data object Idle : RequestQueueUiState
    data object Submitting : RequestQueueUiState
    data class Queued(val requestId: String, val messageId: String) : RequestQueueUiState
    data object Failed : RequestQueueUiState
}
