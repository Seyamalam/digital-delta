package com.example.digitaldelta.ui.main

import com.example.digitaldelta.data.settings.LanguagePreference
import com.example.digitaldelta.data.settings.UserSettingsRepository
import com.example.digitaldelta.domain.request.QueueReceipt
import com.example.digitaldelta.domain.request.ReliefRequestDraft
import com.example.digitaldelta.domain.request.ReliefRequestSubmission
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
        val viewModel = MainScreenViewModel(repository, FakeRequestSubmission())

        assertEquals(LanguagePreference.BANGLA, viewModel.language.value)
        viewModel.setBangla(false)
        advanceUntilIdle()

        assertEquals(LanguagePreference.ENGLISH, repository.languageState.value)
        assertEquals(LanguagePreference.ENGLISH, viewModel.language.value)
    }

    @Test
    fun `queue request exposes durable receipt to the interface`() = runTest(dispatcher) {
        val submission = FakeRequestSubmission()
        val viewModel = MainScreenViewModel(FakeSettingsRepository(), submission)

        viewModel.queueRequest(medicine = 11, ors = 20, tarpaulin = 5, priorityCode = "P0")
        advanceUntilIdle()

        assertEquals(11, submission.received?.cargo?.first { it.itemCode == "medicine" }?.quantity)
        assertEquals(RequestQueueUiState.Queued("request-9", "message-9"), viewModel.requestQueueState.value)
    }
}

private class FakeSettingsRepository : UserSettingsRepository {
    val languageState = MutableStateFlow(LanguagePreference.BANGLA)
    override val language: Flow<LanguagePreference> = languageState

    override suspend fun setLanguage(language: LanguagePreference) {
        languageState.value = language
    }
}

private class FakeRequestSubmission : ReliefRequestSubmission {
    var received: ReliefRequestDraft? = null

    override suspend fun submit(draft: ReliefRequestDraft): QueueReceipt {
        received = draft
        return QueueReceipt("request-9", "message-9")
    }
}
