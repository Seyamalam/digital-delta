package com.example.digitaldelta.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.digitaldelta.data.settings.LanguagePreference
import com.example.digitaldelta.data.settings.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
@HiltViewModel
class MainScreenViewModel @Inject constructor(
    private val settingsRepository: UserSettingsRepository,
) : ViewModel() {
    val language: StateFlow<LanguagePreference> = settingsRepository.language.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LanguagePreference.BANGLA,
    )

    fun setBangla(useBangla: Boolean) {
        viewModelScope.launch {
            settingsRepository.setLanguage(
                if (useBangla) LanguagePreference.BANGLA else LanguagePreference.ENGLISH,
            )
        }
    }
}
