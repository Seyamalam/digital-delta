package com.example.digitaldelta.ui.main

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
    val uiMode = LocalConfiguration.current.uiMode
    return remember(id, language, uiMode) {
        val configuration = Configuration(context.resources.configuration).apply {
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
                )
                return@AnimatedContent
            }
            when (selected) {
                DeltaDestination.OPERATIONS -> OperationsScreen(language)
                DeltaDestination.REQUEST -> RequestScreen(
                    language = language,
                    requestQueueState = requestQueueState,
                    onQueueRequest = onQueueRequest,
                )
                DeltaDestination.ROUTE -> RouteAndMeshScreen(language)
                DeltaDestination.HANDOFF -> HandoffScreen(language)
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
) {
    var trustCode by rememberSaveable { mutableStateOf("") }
    var credentialCode by rememberSaveable { mutableStateOf("") }
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
private fun OperationsScreen(language: AppLanguage) {
    var missionExpanded by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(bottom = 18.dp),
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(if (missionExpanded) 360.dp else 430.dp)) {
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
        item {
            MissionSheet(language, missionExpanded) { missionExpanded = !missionExpanded }
        }
    }
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
private fun RouteAndMeshScreen(language: AppLanguage) {
    var flooded by rememberSaveable { mutableStateOf(true) }
    val progress by animateFloatAsState(
        targetValue = if (flooded) 1f else .42f,
        animationSpec = tween(900),
        label = "reroute-progress",
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(470.dp)) {
                FloodMap(routeProgress = progress, showFailure = flooded, showRisk = true, detailed = true)
                MapLegend(Modifier.align(Alignment.TopEnd).padding(12.dp))
            }
        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                modifier = Modifier.fillMaxWidth().offset(y = (-20).dp),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text(R.string.road_flooded, language),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ElevatedAssistChip(
                            onClick = {},
                            label = { Text(text(R.string.predicted, language)) },
                            leadingIcon = { Icon(Icons.Default.Warning, null, tint = RiskAmber) },
                        )
                        AssistChip(onClick = {}, label = { Text(text(R.string.simulated, language)) })
                    }
                    InfoRow(Icons.Default.DirectionsBoat, text(R.string.route_selected, language))
                    InfoRow(Icons.Default.Warning, text(R.string.risk_notice, language), RiskAmber)
                    InfoRow(Icons.Default.Info, text(R.string.weather_simulated, language))
                    Button(onClick = { flooded = !flooded }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(if (flooded) Icons.Default.Replay else Icons.Default.Warning, null)
                        Spacer(Modifier.width(8.dp))
                        Text(text(R.string.trigger_flood, language))
                    }
                }
            }
        }
    }
}

private enum class HandoffUiState { VERIFIED, REPLAY_REJECTED }

@Composable
private fun HandoffScreen(language: AppLanguage) {
    var state by rememberSaveable { mutableStateOf(HandoffUiState.VERIFIED) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SectionLabel(text(R.string.qr_scan_step, language)) }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    QrCode(
                        value = "DELTA-2026-0001|Boat-02|Hospital-01|nonce-0001",
                        description = text(R.string.signed_delivery_qr_description, language),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(text(R.string.scan_recipient_qr, language), textAlign = TextAlign.Center)
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
                    Surface(
                        color = VerifiedGreen.copy(alpha = .10f),
                        contentColor = VerifiedGreen,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, null, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(text(R.string.handoff_verified, language), style = MaterialTheme.typography.titleLarge)
                                Text(text(R.string.custody_verified, language))
                            }
                        }
                    }
                    DetailRow(text(R.string.sender, language), "Boat-02", Icons.Default.DirectionsBoat)
                    DetailRow(text(R.string.recipient, language), "Drone-01 • SIMULATED", Icons.Default.AirplanemodeActive)
                    DetailRow(text(R.string.nonce_check, language), text(R.string.nonce_ok, language), Icons.Default.Shield)
                    DetailRow(text(R.string.chain_progress, language), text(R.string.chain_steps, language), Icons.Default.Link)
                    OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        Icon(Icons.Default.Shield, null)
                        Spacer(Modifier.width(8.dp))
                        Text(text(R.string.view_audit, language))
                    }
                    AnimatedVisibility(state == HandoffUiState.REPLAY_REJECTED) {
                        Surface(
                            color = AlertCoral.copy(alpha = .10f),
                            contentColor = AlertCoral,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(text(R.string.replay_rejected, language), fontWeight = FontWeight.Bold)
                                    Text(text(R.string.replay_explanation, language), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Button(
                onClick = {
                    state = if (state == HandoffUiState.VERIFIED) HandoffUiState.REPLAY_REJECTED else HandoffUiState.VERIFIED
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(if (state == HandoffUiState.VERIFIED) Icons.Default.Replay else Icons.Default.CheckCircle, null)
                Spacer(Modifier.width(8.dp))
                Text(text(R.string.verify_handoff, language))
            }
        }
    }
}

@Composable
private fun FloodMap(routeProgress: Float, showFailure: Boolean, showRisk: Boolean, detailed: Boolean = false) {
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
                cubicTo(size.width * .24f, size.height * .42f, size.width * .44f, size.height * .33f, size.width * .52f, size.height * .55f)
                cubicTo(size.width * .64f, size.height * .77f, size.width * .72f, size.height * .58f, size.width * .84f, size.height * .80f)
            }
            drawPath(activeRoute, RiverBlue.copy(alpha = .20f), style = Stroke(18f, cap = StrokeCap.Round))
            val measure = PathMeasure().apply { setPath(activeRoute, false) }
            val visible = Path()
            measure.getSegment(0f, measure.length * routeProgress.coerceIn(0f, 1f), visible, true)
            drawPath(visible, RiverBlue, style = Stroke(10f, cap = StrokeCap.Round))
            val vehicle = measure.getPosition(measure.length * routeProgress.coerceIn(0f, 1f))
            drawCircle(Color.White, 19f, vehicle)
            drawCircle(RiverBlue, 12f, vehicle)
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
private fun MapLegend(modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .94f), shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LegendLine(RiverBlue, "Boat")
            LegendLine(AlertCoral, "Failed", dashed = true)
            LegendLine(RiskAmber, "Predicted", dashed = true)
            LegendLine(DeltaTeal, "Mesh", dashed = true)
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
