package com.example.digitaldelta

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.digitaldelta.data.settings.LanguagePreference
import com.example.digitaldelta.domain.mesh.MeshRuntimeStateStore
import com.example.digitaldelta.domain.mesh.NearbyPermissionPolicy
import com.example.digitaldelta.service.MeshRelayService
import com.example.digitaldelta.ui.main.DigitalDeltaApp
import com.example.digitaldelta.ui.main.MainScreenViewModel

@Composable
fun MainNavigation(meshRuntimeStateStore: MeshRuntimeStateStore) {
  val viewModel: MainScreenViewModel = viewModel()
  val context = LocalContext.current
  val language by viewModel.language.collectAsStateWithLifecycle()
  val requestQueueState by viewModel.requestQueueState.collectAsStateWithLifecycle()
  val identityState by viewModel.identityState.collectAsStateWithLifecycle()
  val conflictState by viewModel.conflictState.collectAsStateWithLifecycle()
  val routeState by viewModel.routeState.collectAsStateWithLifecycle()
  val triageState by viewModel.triageState.collectAsStateWithLifecycle()
  val proofOfDeliveryState by viewModel.proofOfDeliveryState.collectAsStateWithLifecycle()
  val meshRuntimeState by meshRuntimeStateStore.state.collectAsStateWithLifecycle()
  var startAfterPermissions by remember { mutableStateOf(false) }
  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
  ) { results ->
    val essential = NearbyPermissionPolicy.requiredRuntimePermissions(Build.VERSION.SDK_INT) -
      NearbyPermissionPolicy.POST_NOTIFICATIONS
    val granted = essential.all { permission ->
      results[permission] == true ||
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    if (startAfterPermissions && granted) {
      ContextCompat.startForegroundService(
        context,
        MeshRelayService.intent(context, MeshRelayService.ACTION_START),
      )
    } else if (!granted) {
      meshRuntimeStateStore.reportPermissionDenied()
    }
    startAfterPermissions = false
  }

  fun startRelay() {
    val missing = NearbyPermissionPolicy.requiredRuntimePermissions(Build.VERSION.SDK_INT)
      .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
    if (missing.isEmpty()) {
      ContextCompat.startForegroundService(
        context,
        MeshRelayService.intent(context, MeshRelayService.ACTION_START),
      )
    } else {
      startAfterPermissions = true
      permissionLauncher.launch(missing.toTypedArray())
    }
  }

  DigitalDeltaApp(
    useBangla = language == LanguagePreference.BANGLA,
    onLanguageChange = viewModel::setBangla,
    requestQueueState = requestQueueState,
    onQueueRequest = viewModel::queueRequest,
    identityState = identityState,
    onPinAdministrator = viewModel::pinAdministrator,
    onImportRecipientCredential = viewModel::importRecipientCredential,
    meshRuntimeState = meshRuntimeState,
    onStartRelay = ::startRelay,
    onStopRelay = { context.startService(MeshRelayService.intent(context, MeshRelayService.ACTION_STOP)) },
    onAcceptPeer = { endpointId ->
      context.startService(MeshRelayService.intent(context, MeshRelayService.ACTION_ACCEPT, endpointId))
    },
    onRejectPeer = { endpointId ->
      context.startService(MeshRelayService.intent(context, MeshRelayService.ACTION_REJECT, endpointId))
    },
    conflictState = conflictState,
    onSimulateConflict = viewModel::simulateConflict,
    onResolveConflict = viewModel::resolveConflict,
    routeState = routeState,
    onToggleRouteFailure = viewModel::toggleRouteFailure,
    triageState = triageState,
    onConfirmPreemption = viewModel::confirmPreemption,
    proofOfDeliveryState = proofOfDeliveryState,
    onVerifyHandoff = viewModel::verifyHandoff,
    onPrepareNextHandoff = viewModel::prepareNextHandoff,
  )
}
