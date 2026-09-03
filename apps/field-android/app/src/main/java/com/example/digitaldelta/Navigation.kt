package com.example.digitaldelta

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.digitaldelta.data.settings.LanguagePreference
import com.example.digitaldelta.ui.main.DigitalDeltaApp
import com.example.digitaldelta.ui.main.MainScreenViewModel

@Composable
fun MainNavigation() {
  val viewModel: MainScreenViewModel = viewModel()
  val language by viewModel.language.collectAsStateWithLifecycle()
  val requestQueueState by viewModel.requestQueueState.collectAsStateWithLifecycle()
  val identityState by viewModel.identityState.collectAsStateWithLifecycle()

  DigitalDeltaApp(
    useBangla = language == LanguagePreference.BANGLA,
    onLanguageChange = viewModel::setBangla,
    requestQueueState = requestQueueState,
    onQueueRequest = viewModel::queueRequest,
    identityState = identityState,
    onPinAdministrator = viewModel::pinAdministrator,
    onImportRecipientCredential = viewModel::importRecipientCredential,
  )
}
