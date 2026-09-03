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
import com.example.digitaldelta.domain.mesh.NearbyConnectionsPeerTransport
import com.example.digitaldelta.domain.mesh.NearbyMeshController
import com.example.digitaldelta.domain.mesh.RoomMeshIngress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MeshRelayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var controller: NearbyMeshController
    private lateinit var runtimeState: MeshRuntimeStateStore
    private lateinit var graph: DigitalDeltaGraphEntryPoint
    private var dispatchJob: Job? = null
    private var batteryPercent = 100
    private var intervalMillis = 10_000L

    override fun onCreate() {
        super.onCreate()
        graph = EntryPointAccessors.fromApplication(
            applicationContext,
            DigitalDeltaGraphEntryPoint::class.java,
        )
        runtimeState = graph.meshRuntimeStateStore()
        controller = NearbyConnectionsPeerTransport(
            context = applicationContext,
            localNodeId = LOCAL_NODE_ID,
            ingress = RoomMeshIngress(graph.database(), LOCAL_NODE_ID),
        )
        scope.launch {
            controller.state.collectLatest { state ->
                runtimeState.publish(state, batteryPercent, intervalMillis)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> startRelay()
            ACTION_ACCEPT -> intent?.getStringExtra(EXTRA_ENDPOINT_ID)?.let(controller::acceptCandidate)
            ACTION_REJECT -> intent?.getStringExtra(EXTRA_ENDPOINT_ID)?.let(controller::rejectCandidate)
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        dispatchJob?.cancel()
        controller.stop()
        runtimeState.reset()
        scope.cancel()
        super.onDestroy()
    }

    private fun startRelay() {
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
        controller.start()
        if (dispatchJob?.isActive != true) {
            dispatchJob = scope.launch { dispatchLoop() }
        }
    }

    private suspend fun dispatchLoop() {
        val dispatcher = MeshOutboxDispatcher(graph.database(), controller)
        val policy = MeshPolicy()
        while (scope.isActive) {
            val now = System.currentTimeMillis()
            batteryPercent = readBatteryPercent()
            val urgent = graph.database().outboxDao().hasUrgentPending(now)
            intervalMillis = policy.broadcastIntervalMillis(batteryPercent, urgent)
            runtimeState.publish(controller.state.value, batteryPercent, intervalMillis)
            controller.state.value.connectedNodeIds.forEach { peerId ->
                dispatcher.dispatch(peerId)
            }
            delay(intervalMillis)
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
        private const val LOCAL_NODE_ID = "N4"
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
