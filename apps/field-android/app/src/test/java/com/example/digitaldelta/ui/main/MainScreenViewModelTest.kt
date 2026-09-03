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
        val viewModel = MainScreenViewModel(repository, FakeRequestSubmission(), FakeIdentityCoordinator())

        assertEquals(LanguagePreference.BANGLA, viewModel.language.value)
        viewModel.setBangla(false)
        advanceUntilIdle()

        assertEquals(LanguagePreference.ENGLISH, repository.languageState.value)
        assertEquals(LanguagePreference.ENGLISH, viewModel.language.value)
    }

    @Test
    fun `queue request exposes durable receipt to the interface`() = runTest(dispatcher) {
        val submission = FakeRequestSubmission()
        val viewModel = MainScreenViewModel(FakeSettingsRepository(), submission, FakeIdentityCoordinator())

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
        val viewModel = MainScreenViewModel(FakeSettingsRepository(), FakeRequestSubmission(), coordinator)
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
        val viewModel = MainScreenViewModel(FakeSettingsRepository(), FakeRequestSubmission(), coordinator)
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
