package com.example.digitaldelta.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.EntryPointAccessors
import com.example.digitaldelta.MainActivity
import com.example.digitaldelta.R
import com.example.digitaldelta.di.DigitalDeltaGraphEntryPoint
import com.example.digitaldelta.domain.mesh.MeshOutboxDispatcher
import com.example.digitaldelta.domain.mesh.MeshPolicy
import com.example.digitaldelta.domain.mesh.MeshRuntimeStateStore
import com.example.digitaldelta.domain.mesh.AndroidMeshAcknowledgementSigner
import com.example.digitaldelta.domain.mesh.AndroidPeerIdentityAuthenticator
import com.example.digitaldelta.domain.mesh.DirectoryMeshAcknowledgementVerifier
import com.example.digitaldelta.domain.mesh.NearbyConnectionsPeerTransport
import com.example.digitaldelta.domain.mesh.NearbyMeshController
import com.example.digitaldelta.domain.mesh.NearbyMeshState
import com.example.digitaldelta.domain.mesh.RoomMeshIngress
import com.example.digitaldelta.domain.mesh.RoomPeerSigningIdentityDirectory
import com.example.digitaldelta.domain.mesh.RelayRoleInput
import com.example.digitaldelta.domain.mesh.RelayRoleSelection
import com.example.digitaldelta.domain.mesh.RelayRole
import com.example.digitaldelta.domain.mesh.RelayLinkQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MeshRelayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var controller: NearbyMeshController? = null
    private lateinit var runtimeState: MeshRuntimeStateStore
    private lateinit var graph: DigitalDeltaGraphEntryPoint
    private val controllerMutex = Mutex()
    private var dispatchJob: Job? = null
    private var batteryPercent = 100
    private var intervalMillis = 10_000L
    private var localNodeId = ""
    private var pendingQueueDepth = 0
    private var relaySelection = RelayRoleSelection(RelayRole.CLIENT_ONLY, RelayLinkQuality.UNKNOWN, false)

    override fun onCreate() {
        super.onCreate()
        graph = EntryPointAccessors.fromApplication(
            applicationContext,
            DigitalDeltaGraphEntryPoint::class.java,
        )
        runtimeState = graph.meshRuntimeStateStore()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> {
                startForegroundImmediately()
                scope.launch {
                    runCatching { activateRelay() }.onFailure { error ->
                        runtimeState.publish(
                            NearbyMeshState(lastError = error.message ?: error.javaClass.simpleName),
                            batteryPercent,
                            intervalMillis,
                            localNodeId,
                            pendingQueueDepth,
                            relaySelection,
                        )
                    }
                }
            }
            ACTION_ACCEPT -> intent?.getStringExtra(EXTRA_ENDPOINT_ID)?.let { endpointId ->
                controller?.acceptCandidate(endpointId)
            }
            ACTION_REJECT -> intent?.getStringExtra(EXTRA_ENDPOINT_ID)?.let { endpointId ->
                controller?.rejectCandidate(endpointId)
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        dispatchJob?.cancel()
        controller?.stop()
        runtimeState.reset()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundImmediately() {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.relay_notification_title))
            .setContentText(getString(R.string.relay_notification_text))
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun activateRelay() {
        val activeController = ensureController()
        activeController.start()
        if (dispatchJob?.isActive != true) {
            dispatchJob = scope.launch { dispatchLoop(activeController) }
        }
    }

    private suspend fun dispatchLoop(activeController: NearbyMeshController) {
        val dispatcher = MeshOutboxDispatcher(
            database = graph.database(),
            transport = activeController,
            acknowledgementVerifier = DirectoryMeshAcknowledgementVerifier(
                RoomPeerSigningIdentityDirectory(graph.database().recipientKeyDao()),
            ),
        )
        val policy = MeshPolicy()
        while (scope.isActive) {
            val now = System.currentTimeMillis()
            batteryPercent = readBatteryPercent()
            val urgent = graph.database().outboxDao().hasUrgentPending(now)
            pendingQueueDepth = graph.database().outboxDao().activeQueueDepth(now)
            intervalMillis = policy.broadcastIntervalMillis(batteryPercent, urgent)
            val nearby = activeController.state.value
            val lastContact = nearby.peerLastContactUnixMs.values.maxOrNull()
            val lastRoundTrip = nearby.peerAcknowledgementRoundTripMillis.values.minOrNull()
            relaySelection = policy.selectRelayRole(
                RelayRoleInput(
                    batteryPercent = batteryPercent,
                    pendingQueueDepth = pendingQueueDepth,
                    connectedPeerCount = nearby.connectedNodeIds.size,
                    lastContactAgeMillis = lastContact?.let { (now - it).coerceAtLeast(0) },
                    lastAcknowledgementRoundTripMillis = lastRoundTrip,
                    urgentPending = urgent,
                ),
            )
            runtimeState.publish(
                nearby,
                batteryPercent,
                intervalMillis,
                localNodeId,
                pendingQueueDepth,
                relaySelection,
            )
            activeController.state.value.connectedNodeIds.forEach { peerId ->
                dispatcher.dispatch(peerId)
            }
            delay(intervalMillis)
        }
    }

    private suspend fun ensureController(): NearbyMeshController = controllerMutex.withLock {
        controller?.let { return@withLock it }
        val profile = graph.deviceProfileRepository().profile.first()
        localNodeId = profile.nodeId
        val acknowledgementSigner = AndroidMeshAcknowledgementSigner(
            nodeId = profile.nodeId,
            deviceKeys = graph.deviceIdentityKeyStore(),
        )
        NearbyConnectionsPeerTransport(
            context = applicationContext,
            localNodeId = profile.nodeId,
            ingress = RoomMeshIngress(
                database = graph.database(),
                localNodeId = profile.nodeId,
                acknowledgementSigner = acknowledgementSigner,
                localApplicationScheduler = { MeshMaintenance.scheduleNow(applicationContext) },
                envelopeVerifier = com.example.digitaldelta.domain.mesh.AndroidEnvelopeSecurity(graph.deviceIdentityKeyStore(), graph.database().recipientKeyDao(), graph.trustAnchorRepository()),
            ),
            identityAuthenticator = AndroidPeerIdentityAuthenticator(
                localNodeId = profile.nodeId,
                deviceKeys = graph.deviceIdentityKeyStore(),
                recipientKeys = graph.database().recipientKeyDao(),
                trustAnchors = graph.trustAnchorRepository(),
            ),
        ).also { created ->
            controller = created
            scope.launch {
                created.state.collectLatest { state ->
                    runtimeState.publish(
                        state,
                        batteryPercent,
                        intervalMillis,
                        localNodeId,
                        pendingQueueDepth,
                        relaySelection,
                    )
                }
            }
        }
    }

    private fun readBatteryPercent(): Int {
        val battery = getSystemService(BATTERY_SERVICE) as BatteryManager
        return battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.relay_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        private const val CHANNEL_ID = "digital_delta_mesh_relay"
        private const val NOTIFICATION_ID = 4104
        private const val EXTRA_ENDPOINT_ID = "endpoint_id"
        const val ACTION_START = "com.example.digitaldelta.mesh.START"
        const val ACTION_STOP = "com.example.digitaldelta.mesh.STOP"
        const val ACTION_ACCEPT = "com.example.digitaldelta.mesh.ACCEPT"
        const val ACTION_REJECT = "com.example.digitaldelta.mesh.REJECT"

        fun intent(context: Context, action: String, endpointId: String? = null): Intent =
            Intent(context, MeshRelayService::class.java).setAction(action).apply {
                if (endpointId != null) putExtra(EXTRA_ENDPOINT_ID, endpointId)
            }
    }
}
