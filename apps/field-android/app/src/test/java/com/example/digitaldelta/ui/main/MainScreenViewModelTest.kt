package com.example.digitaldelta.ui.main

import com.example.digitaldelta.data.settings.LanguagePreference
import com.example.digitaldelta.data.settings.UserSettingsRepository
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
        val viewModel = MainScreenViewModel(repository)

        assertEquals(LanguagePreference.BANGLA, viewModel.language.value)
        viewModel.setBangla(false)
        advanceUntilIdle()

        assertEquals(LanguagePreference.ENGLISH, repository.languageState.value)
        assertEquals(LanguagePreference.ENGLISH, viewModel.language.value)
    }
}

private class FakeSettingsRepository : UserSettingsRepository {
    val languageState = MutableStateFlow(LanguagePreference.BANGLA)
    override val language: Flow<LanguagePreference> = languageState

    override suspend fun setLanguage(language: LanguagePreference) {
        languageState.value = language
    }
}
