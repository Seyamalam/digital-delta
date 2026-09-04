package com.example.digitaldelta.ui.main

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.digitaldelta.R
import com.example.digitaldelta.domain.identity.DeviceProfiles
import com.example.digitaldelta.domain.mesh.MeshRuntimeState
import com.example.digitaldelta.domain.sync.ConflictSide
import com.example.digitaldelta.domain.sync.MissionConflictSnapshot
import com.example.digitaldelta.domain.routing.RouteScenarioSnapshot
import com.example.digitaldelta.domain.routing.RouteDecisionCause
import com.example.digitaldelta.domain.routing.VehicleType
import com.example.digitaldelta.domain.prediction.RouteRiskRuntime
import com.example.digitaldelta.domain.triage.TriageWorkflowSnapshot
import com.example.digitaldelta.domain.fleet.HybridFleetState
import com.example.digitaldelta.ui.scanner.QrScanPurpose
import com.example.digitaldelta.ui.scanner.QrScannerOverlay
import com.example.digitaldelta.theme.AlertCoral
import com.example.digitaldelta.theme.DeltaTeal
import com.example.digitaldelta.theme.DigitalDeltaTheme
import com.example.digitaldelta.theme.RiskAmber
import com.example.digitaldelta.theme.RiverBlue
import com.example.digitaldelta.theme.VerifiedGreen
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.util.Locale
import kotlinx.coroutines.delay

private enum class AppLanguage(val tag: String) {
    BANGLA("bn"),
    ENGLISH("en"),
}

private enum class DeltaDestination(@param:StringRes val label: Int, val icon: ImageVector) {
    OPERATIONS(R.string.nav_operations, Icons.Default.Map),
    REQUEST(R.string.nav_request, Icons.Default.AddCircle),
    ROUTE(R.string.nav_route, Icons.Default.Hub),
    HANDOFF(R.string.nav_handoff, Icons.Default.Handshake),
}

@Composable
private fun text(@StringRes id: Int, language: AppLanguage): String {
    val context = LocalContext.current
    val currentConfiguration = LocalConfiguration.current
    return remember(id, language, currentConfiguration) {
        val configuration = Configuration(currentConfiguration).apply {
            setLocale(Locale.forLanguageTag(language.tag))
        }
        context.createConfigurationContext(configuration).getString(id)
    }
}

@Composable
fun DigitalDeltaApp(
    modifier: Modifier = Modifier,
    showBootSequence: Boolean = true,
    useBangla: Boolean = true,
    onLanguageChange: ((Boolean) -> Unit)? = null,
    requestQueueState: RequestQueueUiState = RequestQueueUiState.Idle,
    onQueueRequest: ((Int, Int, Int, String) -> Unit)? = null,
    identityState: IdentityUiState = IdentityUiState.Loading,
    onPinAdministrator: ((String) -> Unit)? = null,
    onImportRecipientCredential: ((String) -> Unit)? = null,
    onSelectDeviceProfile: ((String) -> Unit)? = null,
    meshRuntimeState: MeshRuntimeState = MeshRuntimeState(),
    onStartRelay: (() -> Unit)? = null,
    onStopRelay: (() -> Unit)? = null,
    onAcceptPeer: ((String) -> Unit)? = null,
    onRejectPeer: ((String) -> Unit)? = null,
    conflictState: MissionConflictSnapshot = MissionConflictSnapshot.Idle,
    onSimulateConflict: (() -> Unit)? = null,
    onResolveConflict: ((String, ConflictSide) -> Unit)? = null,
    routeState: RouteScenarioSnapshot? = null,
    onToggleRouteFailure: (() -> Unit)? = null,
    routeRiskState: RouteRiskUiState = RouteRiskUiState.Idle,
    onToggleRouteRisk: (() -> Unit)? = null,
    triageState: TriageWorkflowSnapshot? = null,
    onConfirmPreemption: (() -> Unit)? = null,
    proofOfDeliveryState: ProofOfDeliveryUiState = ProofOfDeliveryUiState.Loading,
    onVerifyHandoff: ((Boolean) -> Unit)? = null,
    onScanHandoff: ((String) -> Unit)? = null,
    onPrepareNextHandoff: (() -> Unit)? = null,
    hybridFleetState: HybridFleetState = HybridFleetState.Unavailable,
    onReportBoatDelay: (() -> Unit)? = null,
    onAdvanceHybridFleet: (() -> Unit)? = null,
    onResetHybridFleet: (() -> Unit)? = null,
) {
    var booting by rememberSaveable { mutableStateOf(showBootSequence) }
    LaunchedEffect(showBootSequence) {
        if (showBootSequence) {
            delay(1_350)
            booting = false
        }
    }
    Crossfade(
        targetState = booting,
        animationSpec = tween(220),
        label = "boot-sequence",
        modifier = modifier.fillMaxSize(),
    ) { isBooting ->
        if (isBooting) {
            DeltaBootScreen()
        } else {
            DeltaShell(
                useBangla = useBangla,
                onLanguageChange = onLanguageChange,
                requestQueueState = requestQueueState,
                onQueueRequest = onQueueRequest,
                identityState = identityState,
                onPinAdministrator = onPinAdministrator,
                onImportRecipientCredential = onImportRecipientCredential,
                onSelectDeviceProfile = onSelectDeviceProfile,
                meshRuntimeState = meshRuntimeState,
                onStartRelay = onStartRelay,
                onStopRelay = onStopRelay,
                onAcceptPeer = onAcceptPeer,
                onRejectPeer = onRejectPeer,
                conflictState = conflictState,
                onSimulateConflict = onSimulateConflict,
                onResolveConflict = onResolveConflict,
                routeState = routeState,
                onToggleRouteFailure = onToggleRouteFailure,
                routeRiskState = routeRiskState,
                onToggleRouteRisk = onToggleRouteRisk,
                triageState = triageState,
                onConfirmPreemption = onConfirmPreemption,
                proofOfDeliveryState = proofOfDeliveryState,
                onVerifyHandoff = onVerifyHandoff,
                onScanHandoff = onScanHandoff,
                onPrepareNextHandoff = onPrepareNextHandoff,
                hybridFleetState = hybridFleetState,
                onReportBoatDelay = onReportBoatDelay,
                onAdvanceHybridFleet = onAdvanceHybridFleet,
                onResetHybridFleet = onResetHybridFleet,
            )
        }
    }
}

@Composable
private fun DeltaBootScreen() {
    val transition = rememberInfiniteTransition(label = "offline-initialization")
    val routeProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_700), RepeatMode.Restart),
        label = "route-progress",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(720), RepeatMode.Reverse),
        label = "relay-pulse",
    )
    val language = AppLanguage.BANGLA

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(190.dp)) {
                val route = Path().apply {
                    moveTo(size.width * .12f, size.height * .70f)
                    cubicTo(
                        size.width * .33f,
                        size.height * .14f,
                        size.width * .60f,
                        size.height * .90f,
                        size.width * .88f,
                        size.height * .30f,
                    )
                }
                drawPath(route, Color(0xFFD7E6E7), style = Stroke(12f, cap = StrokeCap.Round))
                val segment = Path()
                val measure = PathMeasure().apply { setPath(route, false) }
                measure.getSegment(0f, measure.length * routeProgress, segment, true)
                drawPath(segment, RiverBlue, style = Stroke(12f, cap = StrokeCap.Round))
                listOf(.12f to .70f, .50f to .55f, .88f to .30f).forEachIndexed { index, (x, y) ->
                    drawCircle(
                        color = if (index == 1) DeltaTeal.copy(alpha = .28f) else RiverBlue.copy(alpha = .20f),
                        radius = 24f * pulse,
                        center = Offset(size.width * x, size.height * y),
                    )
                    drawCircle(
                        color = if (index == 1) DeltaTeal else RiverBlue,
                        radius = 13f,
                        center = Offset(size.width * x, size.height * y),
                    )
                }
                val boat = measure.getPosition(measure.length * routeProgress)
                drawCircle(Color.White, 17f, boat)
                drawCircle(RiverBlue, 10f, boat)
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = text(R.string.loading_title, language),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = text(R.string.loading_subtitle, language),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DeltaShell(
    useBangla: Boolean,
    onLanguageChange: ((Boolean) -> Unit)?,
    requestQueueState: RequestQueueUiState,
    onQueueRequest: ((Int, Int, Int, String) -> Unit)?,
    identityState: IdentityUiState,
    onPinAdministrator: ((String) -> Unit)?,
    onImportRecipientCredential: ((String) -> Unit)?,
    onSelectDeviceProfile: ((String) -> Unit)?,
    meshRuntimeState: MeshRuntimeState,
    onStartRelay: (() -> Unit)?,
    onStopRelay: (() -> Unit)?,
    onAcceptPeer: ((String) -> Unit)?,
    onRejectPeer: ((String) -> Unit)?,
    conflictState: MissionConflictSnapshot,
    onSimulateConflict: (() -> Unit)?,
    onResolveConflict: ((String, ConflictSide) -> Unit)?,
    routeState: RouteScenarioSnapshot?,
    onToggleRouteFailure: (() -> Unit)?,
    routeRiskState: RouteRiskUiState,
    onToggleRouteRisk: (() -> Unit)?,
    triageState: TriageWorkflowSnapshot?,
    onConfirmPreemption: (() -> Unit)?,
    proofOfDeliveryState: ProofOfDeliveryUiState,
    onVerifyHandoff: ((Boolean) -> Unit)?,
    onScanHandoff: ((String) -> Unit)?,
    onPrepareNextHandoff: (() -> Unit)?,
    hybridFleetState: HybridFleetState,
    onReportBoatDelay: (() -> Unit)?,
    onAdvanceHybridFleet: (() -> Unit)?,
    onResetHybridFleet: (() -> Unit)?,
) {
    var destination by rememberSaveable { mutableStateOf(DeltaDestination.OPERATIONS) }
    var identityOpen by rememberSaveable { mutableStateOf(false) }
    var localUseBangla by rememberSaveable { mutableStateOf(useBangla) }
    LaunchedEffect(useBangla) {
        if (onLanguageChange != null) localUseBangla = useBangla
    }
    val language = if (localUseBangla) AppLanguage.BANGLA else AppLanguage.ENGLISH

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ConnectivityBar(
                language = language,
                onLanguageChange = {
                    localUseBangla = !localUseBangla
                    onLanguageChange?.invoke(localUseBangla)
                },
                onIdentity = { identityOpen = true },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                DeltaDestination.entries.forEach { item ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("nav-${item.name.lowercase()}"),
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = {
                            Text(
                                text(item.label, language),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 11.sp,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DeltaTeal,
                            selectedTextColor = DeltaTeal,
                            indicatorColor = DeltaTeal.copy(alpha = .12f),
                        ),
                    )
                }
            }
        },
    ) { contentPadding ->
        AnimatedContent(
            targetState = if (identityOpen) null else destination,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "top-level-destination",
            modifier = Modifier.padding(contentPadding),
        ) { selected ->
            if (selected == null) {
                IdentityScreen(
                    language = language,
                    state = identityState,
                    onBack = { identityOpen = false },
                    onPinAdministrator = onPinAdministrator,
                    onImportRecipientCredential = onImportRecipientCredential,
                    onSelectDeviceProfile = onSelectDeviceProfile,
                )
                return@AnimatedContent
            }
            when (selected) {
                DeltaDestination.OPERATIONS -> OperationsScreen(
                    language = language,
                    conflictState = conflictState,
                    onSimulateConflict = onSimulateConflict,
                    onResolveConflict = onResolveConflict,
                )
                DeltaDestination.REQUEST -> RequestScreen(
                    language = language,
                    requestQueueState = requestQueueState,
                    onQueueRequest = onQueueRequest,
                )
                DeltaDestination.ROUTE -> RouteAndMeshScreen(
                    language = language,
                    meshRuntimeState = meshRuntimeState,
                    onStartRelay = onStartRelay,
                    onStopRelay = onStopRelay,
                    onAcceptPeer = onAcceptPeer,
                    onRejectPeer = onRejectPeer,
                    routeState = routeState,
                    onToggleRouteFailure = onToggleRouteFailure,
                    routeRiskState = routeRiskState,
                    onToggleRouteRisk = onToggleRouteRisk,
                    triageState = triageState,
                    onConfirmPreemption = onConfirmPreemption,
                )
                DeltaDestination.HANDOFF -> HandoffScreen(
                    language = language,
                    hybridFleetState = hybridFleetState,
                    onReportBoatDelay = onReportBoatDelay,
                    onAdvanceHybridFleet = onAdvanceHybridFleet,
                    onResetHybridFleet = onResetHybridFleet,
                    state = proofOfDeliveryState,
                    onVerify = onVerifyHandoff,
                    onScan = onScanHandoff,
                    onPrepareNext = onPrepareNextHandoff,
                )
            }
        }
    }
}

@Composable
private fun ConnectivityBar(
    language: AppLanguage,
    onLanguageChange: () -> Unit,
    onIdentity: () -> Unit,
) {
    Surface(color = DeltaTeal) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(54.dp)
                .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.WifiOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text(R.string.connectivity_offline, language),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(text(R.string.demo_identity, language), color = Color.White.copy(alpha = .78f), fontSize = 11.sp)
            }
            TextButton(onClick = onLanguageChange) {
                Icon(Icons.Default.Language, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(text(R.string.language_toggle, language), color = Color.White)
            }
            IconButton(onClick = onIdentity, modifier = Modifier.testTag("identity-open")) {
                Icon(
                    Icons.Default.AdminPanelSettings,
                    contentDescription = text(R.string.identity_and_keys, language),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun IdentityScreen(
    language: AppLanguage,
    state: IdentityUiState,
    onBack: () -> Unit,
    onPinAdministrator: ((String) -> Unit)?,
    onImportRecipientCredential: ((String) -> Unit)?,
    onSelectDeviceProfile: ((String) -> Unit)?,
) {
    var trustCode by rememberSaveable { mutableStateOf("") }
    var credentialCode by rememberSaveable { mutableStateOf("") }
    var scanPurpose by rememberSaveable { mutableStateOf<QrScanPurpose?>(null) }
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val ready = when (state) {
        is IdentityUiState.Ready -> state
        is IdentityUiState.Working -> state.previous
        is IdentityUiState.Failed -> state.previous
        IdentityUiState.Loading -> null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("identity-screen"),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = text(R.string.back, language))
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        text(R.string.identity_and_keys, language),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text(R.string.identity_offline_subtitle, language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (ready == null) {
            item { IdentityLoadingCard(language) }
        } else {
            item {
                Surface(
                    color = VerifiedGreen.copy(alpha = .10f),
                    contentColor = VerifiedGreen,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VpnKey, null, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(text(R.string.device_key_ready, language), fontWeight = FontWeight.Bold)
                            Text("${ready.localNodeId} • ${ready.localEncryptionKeyId.takeLast(12)}")
                        }
                    }
                }
            }
            item {
                SectionLabel(text(R.string.device_profile, language))
                Spacer(Modifier.height(8.dp))
                Text(
                    text(R.string.device_profile_help, language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                DeviceProfiles.all.forEach { profile ->
                    val selected = ready.profileCode == profile.code
                    OutlinedButton(
                        onClick = { onSelectDeviceProfile?.invoke(profile.code) },
                        enabled = !selected && state !is IdentityUiState.Working,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("profile-${profile.code}"),
                    ) {
                        Icon(if (selected) Icons.Default.CheckCircle else Icons.Default.Hub, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (profile.code) {
                                DeviceProfiles.HOSPITAL -> text(R.string.profile_hospital, language)
                                DeviceProfiles.RELAY -> text(R.string.profile_relay, language)
                                else -> text(R.string.profile_clinic, language)
                            } + " • ${profile.nodeId}",
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            item {
                SectionLabel(text(R.string.enrollment_qr, language))
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(18.dp),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth().testTag("enrollment-card"),
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        QrCode(
                            value = ready.enrollmentCode,
                            size = 258.dp,
                            description = text(R.string.enrollment_qr_description, language),
                        )
                        Text(
                            text(R.string.enrollment_qr_help, language),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(ready.enrollmentCode)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.ContentCopy, null)
                            Spacer(Modifier.width(8.dp))
                            Text(text(R.string.copy_enrollment_code, language))
                        }
                    }
                }
            }
            item {
                SectionLabel(text(R.string.administrator_trust, language))
                Spacer(Modifier.height(8.dp))
                if (ready.trustedIssuerFingerprint == null) {
                    OutlinedTextField(
                        value = trustCode,
                        onValueChange = { trustCode = it },
                        label = { Text(text(R.string.trust_code, language)) },
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (trustCode.isNotBlank()) onPinAdministrator?.invoke(trustCode)
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { scanPurpose = QrScanPurpose.ADMIN_TRUST },
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("scan-admin-trust"),
                    ) {
                        Icon(Icons.Default.QrCodeScanner, null)
                        Spacer(Modifier.width(8.dp))
                        Text(text(R.string.scan_administrator_qr, language))
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onPinAdministrator?.invoke(trustCode) },
                        enabled = trustCode.isNotBlank() && state !is IdentityUiState.Working,
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("pin-admin"),
                    ) {
                        Icon(Icons.Default.Shield, null)
                        Spacer(Modifier.width(8.dp))
                        Text(text(R.string.pin_administrator, language))
                    }
                } else {
                    InfoRow(Icons.Default.CheckCircle, "${text(R.string.trusted, language)} • ${ready.trustedIssuerFingerprint}", VerifiedGreen)
                }
            }
            item {
                SectionLabel(text(R.string.recipient_credential, language))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = credentialCode,
                    onValueChange = { credentialCode = it },
                    label = { Text(text(R.string.credential_code, language)) },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (ready.trustedIssuerFingerprint != null && credentialCode.isNotBlank()) {
                                onImportRecipientCredential?.invoke(credentialCode)
                            }
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { scanPurpose = QrScanPurpose.RECIPIENT_CREDENTIAL },
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("scan-recipient-credential"),
                ) {
                    Icon(Icons.Default.QrCodeScanner, null)
                    Spacer(Modifier.width(8.dp))
                    Text(text(R.string.scan_credential_qr, language))
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onImportRecipientCredential?.invoke(credentialCode) },
                    enabled = ready.trustedIssuerFingerprint != null && credentialCode.isNotBlank() && state !is IdentityUiState.Working,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("import-credential"),
                ) {
                    Icon(Icons.Default.AdminPanelSettings, null)
                    Spacer(Modifier.width(8.dp))
                    Text(text(R.string.verify_add_recipient, language))
                }
                ready.acceptedRecipient?.let { recipient ->
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = VerifiedGreen.copy(alpha = .10f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("recipient-accepted"),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = VerifiedGreen)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(recipient.displayName, fontWeight = FontWeight.Bold)
                                Text("${recipient.nodeId} • ${recipient.encryptionKeyId.takeLast(12)}")
                            }
                        }
                    }
                }
            }
        }
        if (state is IdentityUiState.Failed) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("identity-error"),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text(
                                when (state.reason) {
                                    IdentityFailure.KEYSTORE -> R.string.identity_keystore_failed
                                    IdentityFailure.INVALID_TRUST -> R.string.invalid_trust_code
                                    IdentityFailure.INVALID_CREDENTIAL -> R.string.invalid_credential
                                },
                                language,
                            ),
                        )
                    }
                }
            }
        }
    }
    scanPurpose?.let { purpose ->
        QrScannerOverlay(
            purpose = purpose,
            title = text(
                if (purpose == QrScanPurpose.ADMIN_TRUST) R.string.scan_administrator_qr else R.string.scan_credential_qr,
                language,
            ),
            guidance = text(R.string.scan_qr_guidance, language),
            permissionRequired = text(R.string.camera_permission_required, language),
            wrongCode = text(R.string.wrong_qr_purpose, language),
            closeLabel = text(R.string.close_scanner, language),
            retryLabel = text(R.string.grant_camera, language),
            onAccepted = { code ->
                scanPurpose = null
                if (purpose == QrScanPurpose.ADMIN_TRUST) {
                    trustCode = code
                    onPinAdministrator?.invoke(code)
                } else {
                    credentialCode = code
                    onImportRecipientCredential?.invoke(code)
                }
            },
            onDismiss = { scanPurpose = null },
        )
    }
}

@Composable
private fun IdentityLoadingCard(language: AppLanguage) {
    val transition = rememberInfiniteTransition(label = "identity-key-loading")
    val pulse by transition.animateFloat(
        initialValue = .55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "identity-key-pulse",
    )
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(DeltaTeal.copy(alpha = .10f + pulse * .12f), radius = size.minDimension * .48f * pulse)
                    drawCircle(DeltaTeal.copy(alpha = .24f), radius = size.minDimension * .28f)
                }
                Icon(Icons.Default.VpnKey, null, tint = DeltaTeal, modifier = Modifier.size(34.dp))
            }
            Text(text(R.string.preparing_device_keys, language), fontWeight = FontWeight.SemiBold)
            Text(
                text(R.string.private_keys_never_leave, language),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OperationsScreen(
    language: AppLanguage,
    conflictState: MissionConflictSnapshot,
    onSimulateConflict: (() -> Unit)?,
    onResolveConflict: ((String, ConflictSide) -> Unit)?,
) {
    var missionExpanded by rememberSaveable { mutableStateOf(false) }
    val conflictFocused = conflictState !is MissionConflictSnapshot.Idle
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(bottom = 18.dp),
    ) {
        item {
            val mapHeight = when {
                conflictFocused -> 240.dp
                missionExpanded -> 330.dp
                else -> 370.dp
            }
            Box(Modifier.fillMaxWidth().height(mapHeight)) {
                FloodMap(routeProgress = 1f, showFailure = false, showRisk = false)
                SimulationPill(language, Modifier.align(Alignment.TopStart).padding(14.dp))
                FilledTonalIconButton(
                    onClick = {},
                    modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Recenter map")
                }
            }
        }
        if (!conflictFocused) {
            item {
                MissionSheet(language, missionExpanded) { missionExpanded = !missionExpanded }
            }
        }
        item {
            ConflictDemoCard(
                language = language,
                state = conflictState,
                onSimulate = onSimulateConflict,
                onResolve = onResolveConflict,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }
        if (conflictFocused) {
            item {
                MissionSheet(language, missionExpanded) { missionExpanded = !missionExpanded }
            }
        }
    }
}

@Composable
private fun ConflictDemoCard(
    language: AppLanguage,
    state: MissionConflictSnapshot,
    onSimulate: (() -> Unit)?,
    onResolve: ((String, ConflictSide) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "conflict-sync-pulse")
    val pulse by transition.animateFloat(
        initialValue = .15f,
        targetValue = .95f,
        animationSpec = infiniteRepeatable(tween(1_100), RepeatMode.Reverse),
        label = "conflict-pulse-alpha",
    )
    val open = state as? MissionConflictSnapshot.Open
    val resolved = state as? MissionConflictSnapshot.Resolved
    val accent = when {
        open != null -> AlertCoral
        resolved != null -> VerifiedGreen
        else -> RiverBlue
    }
    Surface(
        color = accent.copy(alpha = .08f),
        shape = RoundedCornerShape(20.dp),
        border = CardDefaults.outlinedCardBorder(),
        modifier = modifier.fillMaxWidth().testTag("conflict-card"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(accent.copy(alpha = .12f + pulse * .18f))
                    }
                    Icon(
                        if (resolved != null) Icons.Default.CheckCircle else Icons.Default.Hub,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(text(R.string.conflict_demo_title, language), fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            open != null -> text(R.string.human_decision_required, language)
                            resolved != null -> text(R.string.conflict_resolved, language)
                            else -> text(R.string.conflict_demo_subtitle, language)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (open != null) AlertCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SimulationPill(language)
            }

            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "conflict-state",
            ) { current ->
                when (current) {
                    MissionConflictSnapshot.Idle -> Button(
                        onClick = { onSimulate?.invoke() },
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("simulate-conflict"),
                    ) {
                        Icon(Icons.Default.Warning, null)
                        Spacer(Modifier.width(8.dp))
                        Text(text(R.string.run_concurrent_edits, language))
                    }

                    is MissionConflictSnapshot.Open -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text(R.string.conflict_explanation, language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ConflictChoice(
                            label = text(R.string.phone_a_version, language),
                            location = locationName(current.leftValue, language),
                            clock = current.leftClock,
                            action = "${text(R.string.use_destination, language)} ${locationName(current.leftValue, language)}",
                            onClick = { onResolve?.invoke(current.conflictId, ConflictSide.LEFT) },
                        )
                        ConflictChoice(
                            label = text(R.string.phone_b_version, language),
                            location = locationName(current.rightValue, language),
                            clock = current.rightClock,
                            action = "${text(R.string.use_destination, language)} ${locationName(current.rightValue, language)}",
                            onClick = { onResolve?.invoke(current.conflictId, ConflictSide.RIGHT) },
                        )
                    }

                    is MissionConflictSnapshot.Resolved -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoRow(Icons.Default.CheckCircle, locationName(current.selectedValue, language), VerifiedGreen)
                        Text(
                            "${text(R.string.projection_hash, language)} • ${current.convergenceHash.take(12)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text(R.string.convergence_explanation, language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { onSimulate?.invoke() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Replay, null)
                            Spacer(Modifier.width(8.dp))
                            Text(text(R.string.run_conflict_again, language))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictChoice(
    label: String,
    location: String,
    clock: com.example.digitaldelta.domain.sync.VectorClock,
    action: String,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    clock.counters.toSortedMap().entries.joinToString(" • ") { (node, count) ->
                        "${node.removePrefix("phone-").uppercase()}:$count"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = DeltaTeal,
                )
            }
            Text(location, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(action) }
        }
    }
}

@Composable
private fun locationName(nodeId: String, language: AppLanguage): String = when (nodeId) {
    "N3" -> text(R.string.sunamganj_camp, language)
    "N6" -> text(R.string.habiganj_medical, language)
    else -> nodeId
}

@Composable
private fun MissionSheet(language: AppLanguage, expanded: Boolean, onExpand: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        modifier = Modifier.fillMaxWidth().offset(y = (-18).dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = AlertCoral, shape = RoundedCornerShape(9.dp)) {
                    Text("P0", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text(R.string.p0_medical, language),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }
            InfoRow(Icons.Default.LocationOn, text(R.string.mission_route, language))
            InfoRow(Icons.Default.AccessTime, text(R.string.eta_value, language))
            InfoRow(Icons.Default.DirectionsBoat, text(R.string.vehicle_boat, language))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Hub, null, tint = DeltaTeal, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("${text(R.string.mesh_status, language)} • A ↔ B → C", modifier = Modifier.weight(1f))
                Text("● ${text(R.string.mesh_stable, language)}", color = VerifiedGreen, fontWeight = FontWeight.SemiBold)
            }
            AnimatedVisibility(expanded, enter = fadeIn(), exit = fadeOut()) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text(R.string.relay_count, language))
                        Text("PREDICTED 0.34", color = RiskAmber, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Button(onClick = onExpand, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Default.Map, null)
                Spacer(Modifier.width(8.dp))
                Text(text(R.string.view_mission, language))
            }
        }
    }
}

@Composable
private fun RequestScreen(
    language: AppLanguage,
    requestQueueState: RequestQueueUiState,
    onQueueRequest: ((Int, Int, Int, String) -> Unit)?,
) {
    var medicine by rememberSaveable { mutableIntStateOf(10) }
    var ors by rememberSaveable { mutableIntStateOf(20) }
    var tarpaulin by rememberSaveable { mutableIntStateOf(5) }
    var priority by rememberSaveable { mutableStateOf("P0") }
    var note by rememberSaveable { mutableStateOf("") }
    var queued by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text(
                text(R.string.new_request, language),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        item {
            SectionLabel(text(R.string.location, language))
            Spacer(Modifier.height(8.dp))
            Surface(
                onClick = {},
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder(),
            ) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = DeltaTeal)
                        Spacer(Modifier.width(10.dp))
                        Text(text(R.string.select_location, language), modifier = Modifier.weight(1f))
                        Text("›", fontSize = 26.sp)
                    }
                    MiniLocationMap()
                }
            }
        }
        item {
            SectionLabel(text(R.string.cargo, language))
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder(),
            ) {
                Column {
                    QuantityRow(Icons.Default.Medication, text(R.string.medicine, language), medicine) { medicine = it }
                    HorizontalDivider(Modifier.padding(horizontal = 14.dp))
                    QuantityRow(Icons.Default.WaterDrop, text(R.string.ors, language), ors) { ors = it }
                    HorizontalDivider(Modifier.padding(horizontal = 14.dp))
                    QuantityRow(Icons.Default.Layers, text(R.string.tarpaulin, language), tarpaulin) { tarpaulin = it }
                }
            }
        }
        item {
            SectionLabel(text(R.string.priority, language))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("P0", "P1", "P2", "P3").forEach { value ->
                    val selected = priority == value
                    Surface(
                        onClick = { priority = value },
                        color = if (selected) AlertCoral else MaterialTheme.colorScheme.surface,
                        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = if (selected) null else CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text(value, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        item {
            SectionLabel(text(R.string.note_optional, language))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text(text(R.string.note_hint, language)) },
                minLines = 3,
                shape = RoundedCornerShape(14.dp),
                keyboardActions = KeyboardActions.Default,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                onClick = {
                    if (onQueueRequest == null) {
                        queued = true
                    } else {
                        onQueueRequest(medicine, ors, tarpaulin, priority)
                    }
                },
                enabled = requestQueueState != RequestQueueUiState.Submitting,
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("send-request"),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text(
                        if (requestQueueState == RequestQueueUiState.Submitting) {
                            R.string.queueing_request
                        } else {
                            R.string.send_request
                        },
                        language,
                    ),
                )
            }
            AnimatedVisibility(queued || requestQueueState is RequestQueueUiState.Queued) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 12.dp).testTag("request-queued"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = VerifiedGreen)
                        Spacer(Modifier.width(8.dp))
                        Text(text(R.string.request_queued, language), color = VerifiedGreen)
                    }
                    (requestQueueState as? RequestQueueUiState.Queued)?.let { receipt ->
                        Text(
                            text = "ID ${receipt.requestId.take(8)} • ${receipt.messageId.take(8)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            AnimatedVisibility(requestQueueState is RequestQueueUiState.Failed) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text(
                            if ((requestQueueState as? RequestQueueUiState.Failed)?.reason == RequestFailure.RECIPIENT_NOT_PROVISIONED) {
                                R.string.request_recipient_not_provisioned
                            } else {
                                R.string.request_queue_failed
                            },
                            language,
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteAndMeshScreen(
    language: AppLanguage,
    meshRuntimeState: MeshRuntimeState,
    onStartRelay: (() -> Unit)?,
    onStopRelay: (() -> Unit)?,
    onAcceptPeer: ((String) -> Unit)?,
    onRejectPeer: ((String) -> Unit)?,
    routeState: RouteScenarioSnapshot?,
    onToggleRouteFailure: (() -> Unit)?,
    routeRiskState: RouteRiskUiState,
    onToggleRouteRisk: (() -> Unit)?,
    triageState: TriageWorkflowSnapshot?,
    onConfirmPreemption: (() -> Unit)?,
) {
    val flooded = routeState?.failedEdgeIds?.contains("E3") == true
    val riskActive = routeRiskState is RouteRiskUiState.Active
    val decision = routeState?.decision
    val progress = remember { Animatable(0f) }
    LaunchedEffect(decision?.routeVehicle, flooded) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(900))
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(470.dp)) {
                FloodMap(
                    routeProgress = progress.value,
                    showFailure = flooded,
                    showRisk = riskActive,
                    detailed = true,
                    routeVehicle = decision?.routeVehicle ?: VehicleType.TRUCK,
                )
                MapLegend(
                    language = language,
                    routeVehicle = decision?.routeVehicle ?: VehicleType.TRUCK,
                    showFailure = flooded,
                    showRisk = riskActive,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                )
            }
        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                modifier = Modifier.fillMaxWidth().offset(y = (-20).dp),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RouteRiskCard(
                        language = language,
                        state = routeRiskState,
                        onToggle = onToggleRouteRisk,
                    )
                    MeshRelayCard(
                        language = language,
                        state = meshRuntimeState,
                        onStart = onStartRelay,
                        onStop = onStopRelay,
                        onAcceptPeer = onAcceptPeer,
                        onRejectPeer = onRejectPeer,
                    )
                    Text(
                        text(
                            when {
                                flooded -> R.string.road_flooded
                                riskActive -> R.string.predicted_risk_rerouted
                                else -> R.string.truck_route_ready
                            },
                            language,
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (flooded) {
                            ElevatedAssistChip(
                                onClick = {},
                                label = { Text(text(R.string.confirmed_failure, language)) },
                                leadingIcon = { Icon(Icons.Default.Warning, null, tint = AlertCoral) },
                            )
                        }
                        AssistChip(onClick = {}, label = { Text(text(R.string.simulated, language)) })
                    }
                    Button(
                        onClick = { onToggleRouteFailure?.invoke() },
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("toggle-route-failure"),
                    ) {
                        Icon(if (flooded) Icons.Default.Replay else Icons.Default.Warning, null)
                        Spacer(Modifier.width(8.dp))
                        Text(text(if (flooded) R.string.reset_route else R.string.trigger_flood, language))
                    }
                    InfoRow(
                        if (decision?.routeVehicle == VehicleType.BOAT) Icons.Default.DirectionsBoat else Icons.Default.Map,
                        routeSummary(decision, language),
                    )
                    InfoRow(Icons.Default.AccessTime, etaSummary(decision, language))
                    InfoRow(Icons.Default.Info, routeReason(flooded, riskActive, language))
                    decision?.let {
                        Text(
                            "${text(R.string.recompute_time, language)} • ${"%.3f".format(Locale.US, it.computationNanos / 1_000_000.0)} ms",
                            style = MaterialTheme.typography.labelMedium,
                            color = DeltaTeal,
                            modifier = Modifier.testTag("route-latency"),
                        )
                    }
                    triageState?.let { state ->
                        TriageCard(
                            language = language,
                            state = state,
                            onConfirm = onConfirmPreemption,
                        )
                    }
                    InfoRow(Icons.Default.Info, text(R.string.weather_simulated, language))
                }
            }
        }
    }
}

@Composable
private fun TriageCard(
    language: AppLanguage,
    state: TriageWorkflowSnapshot,
    onConfirm: (() -> Unit)?,
) {
    val urgent = state is TriageWorkflowSnapshot.Proposed || state is TriageWorkflowSnapshot.Confirming
    val confirmed = state is TriageWorkflowSnapshot.Confirmed
    val color = when {
        urgent -> AlertCoral
        confirmed -> VerifiedGreen
        else -> DeltaTeal
    }
    Surface(
        color = color.copy(alpha = .09f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("triage-card"),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (urgent) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = color,
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text(
                            when {
                                urgent -> R.string.sla_breach_predicted
                                confirmed -> R.string.preemption_confirmed
                                else -> R.string.sla_protected
                            },
                            language,
                        ),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "30% • ${state.decision.slowedArrivalMinutes} / ${state.decision.priority.slaMinutes} ${text(R.string.minutes_short, language)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                SimulationPill(language)
            }
            Text(
                "${text(R.string.baseline_arrival, language)} • ${state.decision.baselineArrivalMinutes} ${text(R.string.minutes_short, language)}",
                style = MaterialTheme.typography.bodySmall,
            )
            when (state) {
                is TriageWorkflowSnapshot.Protected -> Text(
                    text(R.string.sla_protected_reason, language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is TriageWorkflowSnapshot.Warning -> Text(
                    text(R.string.sla_warning_reason, language),
                    style = MaterialTheme.typography.bodySmall,
                    color = RiskAmber,
                )
                is TriageWorkflowSnapshot.Proposed -> {
                    Text(
                        "${text(R.string.deposit_p2_at, language)} ${locationName(state.proposal.waypointId, language)}",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${text(R.string.estimated_time_gained, language)} • ${state.proposal.estimatedMinutesGained} ${text(R.string.minutes_short, language)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text(R.string.coordinator_confirmation_required, language),
                        style = MaterialTheme.typography.bodySmall,
                        color = AlertCoral,
                    )
                    Button(
                        onClick = { onConfirm?.invoke() },
                        modifier = Modifier.fillMaxWidth().testTag("confirm-preemption"),
                    ) {
                        Icon(Icons.Default.CheckCircle, null)
                        Spacer(Modifier.width(8.dp))
                        Text(text(R.string.confirm_preemption, language))
                    }
                }
                is TriageWorkflowSnapshot.Confirming -> {
                    Text(
                        "${text(R.string.deposit_p2_at, language)} ${locationName(state.proposal.waypointId, language)}",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text(R.string.recording_preemption_locally, language),
                        style = MaterialTheme.typography.bodySmall,
                        color = AlertCoral,
                    )
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is TriageWorkflowSnapshot.Confirmed -> {
                    Text(
                        "P2 • ${locationName(state.proposal.waypointId, language)} • P0 ${text(R.string.continues, language)}",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${text(R.string.local_event, language)} • ${state.eventId.takeLast(10)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun routeSummary(
    decision: com.example.digitaldelta.domain.routing.DynamicRouteDecision?,
    language: AppLanguage,
): String {
    if (decision == null) return text(R.string.route_loading, language)
    val vehicle = text(
        when (decision.routeVehicle) {
            VehicleType.TRUCK -> R.string.vehicle_truck_name
            VehicleType.BOAT -> R.string.vehicle_boat_name
            VehicleType.DRONE -> R.string.vehicle_drone_name
        },
        language,
    )
    return "$vehicle • ${decision.route.nodeIds.joinToString(" → ")} • ${decision.route.edgeIds.joinToString(" + ")}"
}

@Composable
private fun etaSummary(
    decision: com.example.digitaldelta.domain.routing.DynamicRouteDecision?,
    language: AppLanguage,
): String = if (decision == null) {
    text(R.string.route_loading, language)
} else {
    "${text(R.string.route_eta, language)} • ${decision.route.totalMinutes} ${text(R.string.minutes_short, language)}"
}

@Composable
private fun routeReason(flooded: Boolean, riskActive: Boolean, language: AppLanguage): String =
    text(
        when {
            flooded -> R.string.boat_fallback_reason
            riskActive -> R.string.predicted_risk_route_reason
            else -> R.string.truck_route_reason
        },
        language,
    )

@Composable
private fun RouteRiskCard(
    language: AppLanguage,
    state: RouteRiskUiState,
    onToggle: (() -> Unit)?,
) {
    val active = state as? RouteRiskUiState.Active
    val evaluating = state is RouteRiskUiState.Evaluating
    val accent = when {
        active != null -> RiskAmber
        state is RouteRiskUiState.Failed -> AlertCoral
        else -> DeltaTeal
    }
    Surface(
        color = accent.copy(alpha = .09f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("route-risk-card"),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WaterDrop, null, tint = accent)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(text(R.string.route_risk_title, language), fontWeight = FontWeight.Bold)
                    Text(
                        text(
                            when {
                                active != null -> R.string.route_risk_active
                                evaluating -> R.string.route_risk_evaluating
                                state is RouteRiskUiState.Failed -> R.string.route_risk_failed
                                else -> R.string.route_risk_idle
                            },
                            language,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                SimulationPill(language)
            }
            val features = when (state) {
                is RouteRiskUiState.Active -> state.features
                is RouteRiskUiState.Evaluating -> state.features
                is RouteRiskUiState.Failed -> state.features
                RouteRiskUiState.Idle -> null
            }
            if (features != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RiskMetric(
                        label = text(R.string.rainfall, language),
                        value = "${features.rainfallMmPerHour.toInt()} mm/h",
                        modifier = Modifier.weight(1f),
                    )
                    RiskMetric(
                        label = text(R.string.elevation, language),
                        value = "${features.elevationMeters.toInt()} m",
                        modifier = Modifier.weight(1f),
                    )
                    RiskMetric(
                        label = text(R.string.soil_saturation, language),
                        value = "${(features.soilSaturation * 100).toInt()}%",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (active != null) {
                val prediction = active.prediction
                Text(
                    "${text(R.string.edge_risk, language)} ${active.edgeId} • " +
                        "${"%.1f".format(Locale.US, prediction.probability * 100)}% / " +
                        "${"%.1f".format(Locale.US, prediction.threshold * 100)}%",
                    color = RiskAmber,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("route-risk-probability"),
                )
                Text(
                    "${if (prediction.runtime == RouteRiskRuntime.ONNX) "ONNX" else "BASELINE"} • " +
                        prediction.modelVersion,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (active.updatedDecision.cause == RouteDecisionCause.PREDICTED_RISK) {
                    Text(
                        text(R.string.proactive_reroute, language),
                        color = RiskAmber,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text(R.string.prediction_not_closure, language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (evaluating) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Button(
                onClick = { onToggle?.invoke() },
                enabled = !evaluating,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("toggle-route-risk"),
            ) {
                Icon(if (active != null) Icons.Default.Replay else Icons.Default.WaterDrop, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text(
                        if (active != null || state is RouteRiskUiState.Failed) {
                            R.string.reset_predicted_risk
                        } else {
                            R.string.run_risk_prediction
                        },
                        language,
                    ),
                )
            }
        }
    }
}

@Composable
private fun RiskMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun MeshRelayCard(
    language: AppLanguage,
    state: MeshRuntimeState,
    onStart: (() -> Unit)?,
    onStop: (() -> Unit)?,
    onAcceptPeer: ((String) -> Unit)?,
    onRejectPeer: ((String) -> Unit)?,
) {
    val nearby = state.nearby
    Surface(
        color = if (nearby.running) DeltaTeal.copy(alpha = .10f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("mesh-relay-card"),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Hub, null, tint = if (nearby.running) DeltaTeal else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(text(R.string.nearby_relay, language), fontWeight = FontWeight.Bold)
                    Text(
                        text(if (nearby.running) R.string.relay_active else R.string.relay_stopped, language),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    if (nearby.running) "●" else "○",
                    color = if (nearby.running) VerifiedGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 22.sp,
                )
            }
            if (nearby.running) {
                Text(
                    "${text(R.string.connected_peers, language)} ${nearby.connectedNodeIds.size} • " +
                        "${text(R.string.battery, language)} ${state.batteryPercent}% • " +
                        "${text(R.string.broadcast_every, language)} ${state.broadcastIntervalMillis / 1_000}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (nearby.connectedNodeIds.isEmpty() &&
                    nearby.pendingCandidates.isEmpty() &&
                    nearby.authenticatingNodeIds.isEmpty()
                ) {
                    Text(text(R.string.scanning_for_peers, language), style = MaterialTheme.typography.bodySmall)
                }
                nearby.authenticatingNodeIds.forEach { nodeId ->
                    Surface(
                        color = RiskAmber.copy(alpha = .12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("authenticating-peer-$nodeId"),
                    ) {
                        Text(
                            "${text(R.string.authenticating_peer, language)} • $nodeId",
                            Modifier.padding(12.dp),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                nearby.authenticatedPeerKeyIds.forEach { (nodeId, keyId) ->
                    Surface(
                        color = VerifiedGreen.copy(alpha = .12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("verified-peer-$nodeId"),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${text(R.string.verified_peer, language)} • $nodeId", fontWeight = FontWeight.Bold)
                            Text(
                                "${text(R.string.signing_key, language)} • $keyId",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            nearby.pendingCandidates.forEach { candidate ->
                Surface(color = RiskAmber.copy(alpha = .12f), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${text(R.string.connection_request, language)} • ${candidate.nodeId}", fontWeight = FontWeight.Bold)
                        Text(
                            "${text(R.string.compare_code, language)} ${candidate.authenticationDigits}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onAcceptPeer?.invoke(candidate.endpointId) },
                                modifier = Modifier.weight(1f).testTag("accept-peer"),
                            ) { Text(text(R.string.accept, language)) }
                            OutlinedButton(
                                onClick = { onRejectPeer?.invoke(candidate.endpointId) },
                                modifier = Modifier.weight(1f),
                            ) { Text(text(R.string.reject, language)) }
                        }
                    }
                }
            }
            nearby.lastError?.let { error ->
                Text(
                    if (error == "PERMISSION_DENIED") {
                        text(R.string.mesh_permission_denied, language)
                    } else {
                        "${text(R.string.mesh_error, language)} $error"
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = { if (nearby.running) onStop?.invoke() else onStart?.invoke() },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("mesh-relay-toggle"),
            ) {
                Icon(if (nearby.running) Icons.Default.WifiOff else Icons.Default.Hub, null)
                Spacer(Modifier.width(8.dp))
                Text(text(if (nearby.running) R.string.stop_relay else R.string.start_relay, language))
            }
        }
    }
}

@Composable
private fun HybridFleetCard(
    language: AppLanguage,
    state: HybridFleetState,
    onDelay: (() -> Unit)?,
    onAdvance: (() -> Unit)?,
    onReset: (() -> Unit)?,
) {
    val plan = when (state) {
        is HybridFleetState.Ready -> state.plan
        is HybridFleetState.Replanned -> state.plan
        is HybridFleetState.BoatArrived -> state.plan
        is HybridFleetState.PreparingDroneOffer -> state.plan
        is HybridFleetState.DroneArrived -> state.plan
        is HybridFleetState.VerifyingTransfer -> state.plan
        is HybridFleetState.Transferred -> state.plan
        is HybridFleetState.Blocked,
        HybridFleetState.Unavailable,
        -> null
    }
    val phase = when (state) {
        is HybridFleetState.Ready -> 0
        is HybridFleetState.Replanned -> 0
        is HybridFleetState.BoatArrived,
        is HybridFleetState.PreparingDroneOffer,
        -> 1
        is HybridFleetState.DroneArrived,
        is HybridFleetState.VerifyingTransfer,
        -> 2
        is HybridFleetState.Transferred -> 3
        is HybridFleetState.Blocked,
        HybridFleetState.Unavailable,
        -> -1
    }
    val statusText = when (state) {
        is HybridFleetState.Ready -> text(R.string.hybrid_phase_ready, language)
        is HybridFleetState.Replanned -> text(R.string.hybrid_phase_replanned, language)
        is HybridFleetState.BoatArrived -> text(R.string.hybrid_phase_boat_arrived, language)
        is HybridFleetState.PreparingDroneOffer -> text(R.string.hybrid_phase_preparing_offer, language)
        is HybridFleetState.DroneArrived -> text(R.string.hybrid_phase_drone_arrived, language)
        is HybridFleetState.VerifyingTransfer -> text(R.string.hybrid_phase_verifying, language)
        is HybridFleetState.Transferred -> text(R.string.hybrid_phase_transferred, language)
        is HybridFleetState.Blocked -> text(R.string.hybrid_phase_blocked, language)
        HybridFleetState.Unavailable -> text(R.string.hybrid_phase_loading, language)
    }
    val actionText = when (state) {
        is HybridFleetState.Ready -> text(R.string.hybrid_start_boat, language)
        is HybridFleetState.Replanned -> text(R.string.hybrid_start_replanned_boat, language)
        is HybridFleetState.BoatArrived -> text(R.string.hybrid_generate_qr, language)
        is HybridFleetState.DroneArrived -> text(R.string.hybrid_accept_custody, language)
        is HybridFleetState.Transferred,
        is HybridFleetState.Blocked,
        -> text(R.string.hybrid_reset, language)
        is HybridFleetState.PreparingDroneOffer,
        is HybridFleetState.VerifyingTransfer,
        HybridFleetState.Unavailable,
        -> statusText
    }
    val loading = state is HybridFleetState.PreparingDroneOffer ||
        state is HybridFleetState.VerifyingTransfer ||
        state is HybridFleetState.Unavailable
    val transition = rememberInfiniteTransition(label = "hybrid-fleet-motion")
    val motion by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_600), RepeatMode.Restart),
        label = "rendezvous-motion",
    )

    Surface(
        modifier = Modifier.fillMaxWidth().testTag("hybrid-fleet-card"),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = AlertCoral.copy(alpha = .12f),
                    contentColor = AlertCoral,
                    shape = RoundedCornerShape(9.dp),
                ) {
                    Text(
                        text(R.string.hybrid_drone_required, language),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = RiskAmber.copy(alpha = .16f),
                    contentColor = Color(0xFF7B4B00),
                    shape = RoundedCornerShape(9.dp),
                ) {
                    Text(
                        text(R.string.simulated, language),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text(R.string.hybrid_fleet_title, language),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text(R.string.hybrid_fleet_subtitle, language),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HybridRendezvousAnimation(progress = motion, phase = phase)
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("hybrid-fleet-status"),
                color = when (state) {
                    is HybridFleetState.Transferred -> VerifiedGreen.copy(alpha = .11f)
                    is HybridFleetState.Blocked -> AlertCoral.copy(alpha = .10f)
                    else -> DeltaTeal.copy(alpha = .09f)
                },
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (state) {
                            is HybridFleetState.Transferred -> Icons.Default.CheckCircle
                            is HybridFleetState.Blocked -> Icons.Default.Warning
                            else -> Icons.Default.Hub
                        },
                        contentDescription = null,
                        tint = when (state) {
                            is HybridFleetState.Transferred -> VerifiedGreen
                            is HybridFleetState.Blocked -> AlertCoral
                            else -> DeltaTeal
                        },
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        AnimatedContent(
                            targetState = statusText,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(100)) },
                            label = "hybrid-phase-status",
                        ) { label ->
                            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text(R.string.hybrid_offline_ledger, language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (plan != null) {
                if (state is HybridFleetState.Replanned) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().testTag("hybrid-fleet-replanned"),
                        color = RiskAmber.copy(alpha = .14f),
                        contentColor = Color(0xFF6C4300),
                        shape = RoundedCornerShape(13.dp),
                    ) {
                        Text(
                            "${text(R.string.hybrid_replan_label, language)} " +
                                "${state.previousPlan.rendezvous.point.id} → ${state.plan.rendezvous.point.id} • " +
                                "${state.report.delayMinutes} ${text(R.string.minutes_short, language)}",
                            modifier = Modifier.padding(13.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                DetailRow(
                    text(R.string.hybrid_destination, language),
                    "N7 • ${text(R.string.hybrid_destination_name, language)}",
                    Icons.Default.LocalHospital,
                )
                DetailRow(
                    text(R.string.hybrid_rendezvous, language),
                    "${plan.rendezvous.point.id} • " + String.format(
                        Locale.US,
                        "%.4f, %.4f",
                        plan.rendezvous.point.coordinate.latitude,
                        plan.rendezvous.point.coordinate.longitude,
                    ),
                    Icons.Default.LocationOn,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HybridMetric(
                        Modifier.weight(1f),
                        text(R.string.hybrid_boat_eta, language),
                        "${plan.rendezvous.boatArrivalMinutes.toInt()} ${text(R.string.minutes_short, language)}",
                    )
                    HybridMetric(
                        Modifier.weight(1f),
                        text(R.string.hybrid_drone_eta, language),
                        "${plan.rendezvous.droneArrivalMinutes.toInt()} ${text(R.string.minutes_short, language)}",
                    )
                    HybridMetric(
                        Modifier.weight(1f),
                        text(R.string.hybrid_delivery_eta, language),
                        "${plan.rendezvous.deliveryArrivalMinutes.toInt()} ${text(R.string.minutes_short, language)}",
                    )
                }
                val reserve = plan.mission.rendezvousInputs.reserveBatteryPercent
                val projected = plan.rendezvous.projectedDroneBatteryPercent.toInt()
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text(R.string.hybrid_projected_battery, language), style = MaterialTheme.typography.labelMedium)
                        Text("$projected% • ${text(R.string.hybrid_reserve, language)} $reserve%", style = MaterialTheme.typography.labelMedium)
                    }
                    LinearProgressIndicator(
                        progress = { projected / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = VerifiedGreen,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
                HybridPhaseRow(Icons.Default.DirectionsBoat, text(R.string.hybrid_boat_step, language), phase >= 1, phase == 0)
                HybridPhaseRow(Icons.Default.Shield, text(R.string.hybrid_signed_qr_step, language), phase >= 2, phase == 1)
                HybridPhaseRow(Icons.Default.AirplanemodeActive, text(R.string.hybrid_drone_step, language), phase >= 3, phase == 2)
            }
            if (state is HybridFleetState.Transferred) {
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("hybrid-fleet-receipt"),
                    color = VerifiedGreen.copy(alpha = .10f),
                    contentColor = VerifiedGreen,
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text(R.string.hybrid_receipt_verified, language), fontWeight = FontWeight.Bold)
                        Text("${state.receipt.recipientIdentityId} • ${state.receipt.receiptHash.toShortHex()}", style = MaterialTheme.typography.labelSmall)
                        Text("${state.chain.size} • ${text(R.string.chain_valid, language)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("hybrid-fleet-loading"))
            if (state is HybridFleetState.Ready) {
                OutlinedButton(
                    onClick = { onDelay?.invoke() },
                    enabled = onDelay != null,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("hybrid-fleet-delay"),
                ) {
                    Icon(Icons.Default.AccessTime, null)
                    Spacer(Modifier.width(8.dp))
                    Text(text(R.string.hybrid_simulate_delay, language))
                }
            }
            Button(
                onClick = {
                    if (state is HybridFleetState.Transferred || state is HybridFleetState.Blocked) onReset?.invoke()
                    else onAdvance?.invoke()
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("hybrid-fleet-action"),
            ) {
                Icon(if (state is HybridFleetState.Transferred) Icons.Default.Replay else Icons.Default.Handshake, null)
                Spacer(Modifier.width(8.dp))
                Text(actionText)
            }
        }
    }
}

@Composable
private fun HybridRendezvousAnimation(progress: Float, phase: Int) {
    Canvas(Modifier.fillMaxWidth().height(82.dp)) {
        val center = Offset(size.width * .52f, size.height * .57f)
        val boatStart = Offset(size.width * .06f, size.height * .72f)
        val droneStart = Offset(size.width * .94f, size.height * .18f)
        drawLine(RiverBlue.copy(alpha = .28f), boatStart, center, 8f, cap = StrokeCap.Round)
        drawLine(
            DeltaTeal.copy(alpha = .30f),
            droneStart,
            center,
            5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(13f, 10f)),
        )
        val boatProgress = if (phase >= 1) 1f else progress
        val droneProgress = if (phase >= 2) 1f else if (phase == 1) progress else 0f
        val boat = Offset(boatStart.x + (center.x - boatStart.x) * boatProgress, boatStart.y + (center.y - boatStart.y) * boatProgress)
        val drone = Offset(droneStart.x + (center.x - droneStart.x) * droneProgress, droneStart.y + (center.y - droneStart.y) * droneProgress)
        drawCircle(RiverBlue.copy(alpha = .20f), 20f, boat)
        drawCircle(RiverBlue, 10f, boat)
        drawCircle(DeltaTeal.copy(alpha = .20f), 20f, drone)
        drawCircle(DeltaTeal, 9f, drone)
        drawCircle(if (phase >= 3) VerifiedGreen else RiskAmber, 12f, center)
        drawCircle(Color.White, 5f, center)
    }
}

@Composable
private fun HybridMetric(modifier: Modifier, label: String, value: String) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .62f), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun HybridPhaseRow(icon: ImageVector, label: String, complete: Boolean, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = when {
                complete -> VerifiedGreen.copy(alpha = .14f)
                active -> DeltaTeal.copy(alpha = .12f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = when {
                complete -> VerifiedGreen
                active -> DeltaTeal
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        ) {
            Icon(if (complete) Icons.Default.CheckCircle else icon, null, Modifier.padding(7.dp).size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun HandoffScreen(
    language: AppLanguage,
    hybridFleetState: HybridFleetState,
    onReportBoatDelay: (() -> Unit)?,
    onAdvanceHybridFleet: (() -> Unit)?,
    onResetHybridFleet: (() -> Unit)?,
    state: ProofOfDeliveryUiState,
    onVerify: ((Boolean) -> Unit)?,
    onScan: ((String) -> Unit)?,
    onPrepareNext: (() -> Unit)?,
) {
    var scannerOpen by rememberSaveable { mutableStateOf(false) }
    val offer = when (state) {
        is ProofOfDeliveryUiState.Ready -> state.offer
        is ProofOfDeliveryUiState.Verifying -> state.offer
        is ProofOfDeliveryUiState.Verified -> state.offer
        is ProofOfDeliveryUiState.Rejected -> state.offer
        ProofOfDeliveryUiState.Loading,
        ProofOfDeliveryUiState.Failed,
        -> null
    }
    val preservedChain = when (state) {
        is ProofOfDeliveryUiState.Verified -> state.chain
        is ProofOfDeliveryUiState.Rejected -> state.preservedChain
        else -> emptyList()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            HybridFleetCard(
                language = language,
                state = hybridFleetState,
                onDelay = onReportBoatDelay,
                onAdvance = onAdvanceHybridFleet,
                onReset = onResetHybridFleet,
            )
        }
        item { SectionLabel(text(R.string.signed_offer_step, language)) }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        offer != null -> {
                            QrCode(
                                value = offer.qrCode,
                                description = text(R.string.signed_delivery_qr_description, language),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(text(R.string.scan_recipient_qr, language), textAlign = TextAlign.Center)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Protobuf • RSA-PSS • ${offer.senderSigningKeyId.takeLast(10)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        state is ProofOfDeliveryUiState.Failed -> {
                            Icon(Icons.Default.Warning, null, tint = AlertCoral, modifier = Modifier.size(42.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(text(R.string.handoff_prepare_failed, language), textAlign = TextAlign.Center)
                        }
                        else -> {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Spacer(Modifier.height(12.dp))
                            Text(text(R.string.preparing_signed_offer, language), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        item { SectionLabel(text(R.string.handoff_check_step, language)) }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                border = CardDefaults.outlinedCardBorder(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val verified = state is ProofOfDeliveryUiState.Verified
                    val rejected = state is ProofOfDeliveryUiState.Rejected
                    val statusColor = when {
                        verified -> VerifiedGreen
                        rejected -> AlertCoral
                        else -> DeltaTeal
                    }
                    Surface(
                        color = statusColor.copy(alpha = .10f),
                        contentColor = statusColor,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (rejected) Icons.Default.Warning else Icons.Default.Shield,
                                null,
                                modifier = Modifier.size(34.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    when (state) {
                                        is ProofOfDeliveryUiState.Ready -> text(R.string.awaiting_verification, language)
                                        is ProofOfDeliveryUiState.Verifying -> text(R.string.verifying_locally, language)
                                        is ProofOfDeliveryUiState.Verified -> text(R.string.handoff_verified, language)
                                        is ProofOfDeliveryUiState.Rejected -> text(R.string.handoff_rejected, language)
                                        ProofOfDeliveryUiState.Loading -> text(R.string.preparing_signed_offer, language)
                                        ProofOfDeliveryUiState.Failed -> text(R.string.handoff_prepare_failed, language)
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    if (verified) text(R.string.custody_verified, language)
                                    else text(R.string.offline_local_verification, language),
                                )
                            }
                        }
                    }
                    AnimatedVisibility(state is ProofOfDeliveryUiState.Rejected) {
                        Surface(
                            color = AlertCoral.copy(alpha = .10f),
                            contentColor = AlertCoral,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    val rejection = (state as? ProofOfDeliveryUiState.Rejected)?.reason
                                    Text(
                                        rejectionTitle(rejection, language),
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        rejectionExplanation(rejection, language),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                    if (offer != null && state !is ProofOfDeliveryUiState.Verifying) {
                        OutlinedButton(
                            onClick = { scannerOpen = true },
                            enabled = onScan != null,
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("scan-handoff"),
                        ) {
                            Icon(Icons.Default.QrCodeScanner, null)
                            Spacer(Modifier.width(8.dp))
                            Text(text(R.string.scan_delivery_qr, language))
                        }
                        Button(
                            onClick = { onVerify?.invoke(false) },
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("verify-handoff"),
                        ) {
                            Icon(
                                if (state is ProofOfDeliveryUiState.Verified) Icons.Default.Replay
                                else Icons.Default.CheckCircle,
                                null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text(
                                    if (state is ProofOfDeliveryUiState.Verified) R.string.verify_same_qr
                                    else R.string.verify_handoff,
                                    language,
                                ),
                            )
                        }
                        OutlinedButton(
                            onClick = { onVerify?.invoke(true) },
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("tamper-handoff"),
                        ) {
                            Icon(Icons.Default.Warning, null)
                            Spacer(Modifier.width(8.dp))
                            Text(text(R.string.test_tampered_qr, language))
                        }
                    }
                    if (state is ProofOfDeliveryUiState.Rejected || state is ProofOfDeliveryUiState.Failed) {
                        TextButton(
                            onClick = { onPrepareNext?.invoke() },
                            modifier = Modifier.fillMaxWidth().testTag("prepare-next-handoff"),
                        ) {
                            Text(text(R.string.prepare_next_handoff, language))
                        }
                    }
                    if (offer != null) {
                        DetailRow(text(R.string.delivery_id, language), offer.deliveryId, Icons.Default.Inventory2)
                        DetailRow(text(R.string.sender, language), "Boat-02", Icons.Default.DirectionsBoat)
                        DetailRow(text(R.string.recipient, language), "Hospital-01", Icons.Default.LocalHospital)
                        DetailRow(
                            text(R.string.payload_hash, language),
                            offer.payloadSha256.toShortHex(),
                            Icons.Default.Shield,
                        )
                        DetailRow(
                            text(R.string.previous_receipt, language),
                            offer.previousReceiptSha256.toShortHex(),
                            Icons.Default.Link,
                        )
                    }
                    if (state is ProofOfDeliveryUiState.Verifying) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    if (preservedChain.isNotEmpty()) {
                        DetailRow(
                            text(R.string.chain_progress, language),
                            "${preservedChain.size} • ${text(R.string.chain_valid, language)}",
                            Icons.Default.Link,
                        )
                        Text(
                            "${text(R.string.latest_receipt, language)} • ${preservedChain.last().receiptHash.toShortHex()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    if (scannerOpen) {
        QrScannerOverlay(
            purpose = QrScanPurpose.DELIVERY_HANDOFF,
            title = text(R.string.scan_delivery_qr, language),
            guidance = text(R.string.scan_qr_guidance, language),
            permissionRequired = text(R.string.camera_permission_required, language),
            wrongCode = text(R.string.wrong_qr_purpose, language),
            closeLabel = text(R.string.close_scanner, language),
            retryLabel = text(R.string.grant_camera, language),
            onAccepted = { code ->
                scannerOpen = false
                onScan?.invoke(code)
            },
            onDismiss = { scannerOpen = false },
        )
    }
}

@Composable
private fun rejectionTitle(reason: com.example.digitaldelta.domain.pod.DeliveryOfferRejection?, language: AppLanguage): String =
    text(
        when (reason) {
            com.example.digitaldelta.domain.pod.DeliveryOfferRejection.REPLAY_REJECTED -> R.string.replay_rejected
            com.example.digitaldelta.domain.pod.DeliveryOfferRejection.INVALID_SIGNATURE -> R.string.signature_rejected
            else -> R.string.handoff_rejected
        },
        language,
    )

@Composable
private fun rejectionExplanation(
    reason: com.example.digitaldelta.domain.pod.DeliveryOfferRejection?,
    language: AppLanguage,
): String = text(
    when (reason) {
        com.example.digitaldelta.domain.pod.DeliveryOfferRejection.REPLAY_REJECTED -> R.string.replay_explanation
        com.example.digitaldelta.domain.pod.DeliveryOfferRejection.INVALID_SIGNATURE -> R.string.signature_rejection_explanation
        com.example.digitaldelta.domain.pod.DeliveryOfferRejection.CLOCK_SKEW -> R.string.clock_skew_explanation
        com.example.digitaldelta.domain.pod.DeliveryOfferRejection.KEY_MISMATCH -> R.string.key_mismatch_explanation
        else -> R.string.handoff_rejection_explanation
    },
    language,
)

private fun ByteArray.toShortHex(): String =
    joinToString("") { "%02x".format(it) }.let { "${it.take(8)}…${it.takeLast(8)}" }

@Composable
private fun FloodMap(
    routeProgress: Float,
    showFailure: Boolean,
    showRisk: Boolean,
    detailed: Boolean = false,
    routeVehicle: VehicleType = VehicleType.BOAT,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFFEFF5F3))) {
        Canvas(Modifier.fillMaxSize()) {
            val river = Path().apply {
                moveTo(size.width * .04f, size.height * .18f)
                cubicTo(size.width * .32f, size.height * .22f, size.width * .22f, size.height * .58f, size.width * .52f, size.height * .54f)
                cubicTo(size.width * .72f, size.height * .52f, size.width * .68f, size.height * .83f, size.width * .96f, size.height * .92f)
            }
            drawPath(river, Color(0xFFB8DDF3), style = Stroke(34f, cap = StrokeCap.Round))
            drawPath(river, Color(0xFFD7ECF7), style = Stroke(18f, cap = StrokeCap.Round))
            val roadEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))
            repeat(4) { index ->
                val y = size.height * (.18f + index * .20f)
                drawLine(Color(0xFFB9C8C4), Offset(0f, y), Offset(size.width, y * .90f), 3f, pathEffect = roadEffect)
            }
            val activeRoute = Path().apply {
                moveTo(size.width * .16f, size.height * .25f)
                when (routeVehicle) {
                    VehicleType.TRUCK -> cubicTo(
                        size.width * .34f,
                        size.height * .17f,
                        size.width * .56f,
                        size.height * .38f,
                        size.width * .84f,
                        size.height * .80f,
                    )
                    VehicleType.BOAT -> {
                        cubicTo(size.width * .24f, size.height * .42f, size.width * .44f, size.height * .33f, size.width * .52f, size.height * .55f)
                        cubicTo(size.width * .64f, size.height * .77f, size.width * .72f, size.height * .58f, size.width * .84f, size.height * .80f)
                    }
                    VehicleType.DRONE -> lineTo(size.width * .84f, size.height * .80f)
                }
            }
            val routeColor = when (routeVehicle) {
                VehicleType.TRUCK -> DeltaTeal
                VehicleType.BOAT -> RiverBlue
                VehicleType.DRONE -> RiskAmber
            }
            drawPath(activeRoute, routeColor.copy(alpha = .20f), style = Stroke(18f, cap = StrokeCap.Round))
            val measure = PathMeasure().apply { setPath(activeRoute, false) }
            val visible = Path()
            measure.getSegment(0f, measure.length * routeProgress.coerceIn(0f, 1f), visible, true)
            drawPath(visible, routeColor, style = Stroke(10f, cap = StrokeCap.Round))
            val vehicle = measure.getPosition(measure.length * routeProgress.coerceIn(0f, 1f))
            drawCircle(Color.White, 19f, vehicle)
            drawCircle(routeColor, 12f, vehicle)
            if (showFailure) {
                drawLine(
                    AlertCoral,
                    Offset(size.width * .26f, size.height * .12f),
                    Offset(size.width * .52f, size.height * .55f),
                    strokeWidth = 8f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 12f)),
                )
                drawCircle(AlertCoral, 18f, Offset(size.width * .39f, size.height * .335f))
            }
            if (showRisk) {
                drawLine(
                    RiskAmber,
                    Offset(size.width * .78f, size.height * .14f),
                    Offset(size.width * .84f, size.height * .80f),
                    7f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 13f)),
                )
            }
            if (detailed) {
                val meshEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                val a = Offset(size.width * .28f, size.height * .18f)
                val b = Offset(size.width * .40f, size.height * .54f)
                val c = Offset(size.width * .70f, size.height * .60f)
                drawLine(DeltaTeal, a, b, 6f, pathEffect = meshEffect)
                drawLine(DeltaTeal, a, c, 6f, pathEffect = meshEffect)
                drawLine(DeltaTeal, b, c, 6f, pathEffect = meshEffect)
                listOf(a, b, c).forEach { node ->
                    drawCircle(Color.White, 25f, node)
                    drawCircle(DeltaTeal, 19f, node)
                }
            }
        }
        if (detailed) {
            MapNodeLabel("A", .28f, .18f, maxWidth, maxHeight)
            MapNodeLabel("B", .40f, .54f, maxWidth, maxHeight)
            MapNodeLabel("C", .70f, .60f, maxWidth, maxHeight)
        } else {
            MapMarker(Icons.Default.LocalHospital, .16f, .25f, maxWidth, maxHeight, DeltaTeal)
            MapMarker(Icons.Default.DirectionsBoat, .52f, .55f, maxWidth, maxHeight, RiverBlue)
            MapMarker(Icons.Default.LocalHospital, .84f, .80f, maxWidth, maxHeight, DeltaTeal)
        }
    }
}

@Composable
private fun MiniLocationMap() {
    Box(
        Modifier.fillMaxWidth().height(150.dp).background(Color(0xFFEFF5F3)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            repeat(5) { index ->
                drawLine(
                    Color(0xFFC9D7D3),
                    Offset(0f, size.height * index / 4),
                    Offset(size.width, size.height * (index + 1) / 5),
                    2f,
                )
            }
        }
        Icon(Icons.Default.LocationOn, contentDescription = "Selected location", tint = DeltaTeal, modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun MapLegend(
    language: AppLanguage,
    routeVehicle: VehicleType,
    showFailure: Boolean,
    showRisk: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .94f), shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val routeColor = when (routeVehicle) {
                VehicleType.TRUCK -> DeltaTeal
                VehicleType.BOAT -> RiverBlue
                VehicleType.DRONE -> RiskAmber
            }
            val vehicleLabel = text(
                when (routeVehicle) {
                    VehicleType.TRUCK -> R.string.vehicle_truck_name
                    VehicleType.BOAT -> R.string.vehicle_boat_name
                    VehicleType.DRONE -> R.string.vehicle_drone_name
                },
                language,
            )
            LegendLine(routeColor, vehicleLabel)
            if (showFailure) LegendLine(AlertCoral, text(R.string.failed_edge, language), dashed = true)
            if (showRisk) LegendLine(RiskAmber, text(R.string.predicted_risk_edge, language), dashed = true)
            LegendLine(DeltaTeal, text(R.string.mesh_legend, language), dashed = true)
        }
    }
}

@Composable
private fun LegendLine(color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(28.dp).height(8.dp)) {
            drawLine(
                color,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                5f,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp)
    }
}

@Composable
private fun MapNodeLabel(label: String, x: Float, y: Float, width: Dp, height: Dp) {
    Box(
        Modifier.offset(x = width * x - 17.dp, y = height * y - 17.dp).size(34.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MapMarker(icon: ImageVector, x: Float, y: Float, width: Dp, height: Dp, color: Color) {
    Surface(
        color = color,
        contentColor = Color.White,
        shape = CircleShape,
        modifier = Modifier.offset(x = width * x - 18.dp, y = height * y - 18.dp).size(36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun SimulationPill(language: AppLanguage, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
        shape = RoundedCornerShape(10.dp),
        border = CardDefaults.outlinedCardBorder(),
        modifier = modifier,
    ) {
        Text(
            text(R.string.simulated, language),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SectionLabel(value: String) {
    Text(value, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

@Composable
private fun InfoRow(icon: ImageVector, value: String, tint: Color = DeltaTeal) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetailRow(label: String, value: String, icon: ImageVector) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(88.dp))
        Icon(icon, null, tint = DeltaTeal, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(9.dp))
        Text(value, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun QuantityRow(icon: ImageVector, label: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = DeltaTeal)
        Spacer(Modifier.width(10.dp))
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = { onValueChange((value - 1).coerceAtLeast(0)) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease $label")
        }
        Text(value.toString(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp))
        IconButton(onClick = { onValueChange(value + 1) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Increase $label")
        }
    }
}

@Composable
private fun QrCode(
    value: String,
    size: Dp = 220.dp,
    description: String,
) {
    val image = remember(value) {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 520, 520)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            val x = index % matrix.width
            val y = index / matrix.width
            if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
        }.asImageBitmap()
    }
    androidx.compose.foundation.Image(
        bitmap = image,
        contentDescription = description,
        modifier = Modifier
            .size(size)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(8.dp),
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DeltaAppPreview() {
    DigitalDeltaTheme(darkTheme = false) { DigitalDeltaApp(showBootSequence = false) }
}
