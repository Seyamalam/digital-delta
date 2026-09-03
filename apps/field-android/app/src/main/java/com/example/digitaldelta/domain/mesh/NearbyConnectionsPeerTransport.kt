package com.example.digitaldelta.domain.mesh

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class NearbyPeerCandidate(
    val endpointId: String,
    val nodeId: String,
    val authenticationDigits: String,
)

data class NearbyMeshState(
    val running: Boolean = false,
    val discoveredNodeIds: Set<String> = emptySet(),
    val pendingCandidates: List<NearbyPeerCandidate> = emptyList(),
    val connectedNodeIds: Set<String> = emptySet(),
    val lastError: String? = null,
)

interface NearbyMeshController : PeerTransport {
    val state: StateFlow<NearbyMeshState>
    fun start()
    fun acceptCandidate(endpointId: String)
    fun rejectCandidate(endpointId: String)
    fun stop()
}

/**
 * Google Nearby Connections adapter. Every radio payload is a Protobuf [PeerFrame]; envelope
 * durability and forwarding remain owned by [RoomMeshIngress]. Connection authentication digits
 * must be confirmed by a person before [acceptCandidate] is called.
 */
class NearbyConnectionsPeerTransport(
    context: Context,
    private val localNodeId: String,
    private val ingress: RoomMeshIngress,
    private val client: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : NearbyMeshController {
    private val mutableState = MutableStateFlow(NearbyMeshState())
    override val state: StateFlow<NearbyMeshState> = mutableState.asStateFlow()
    private val candidates = ConcurrentHashMap<String, NearbyPeerCandidate>()
    private val connectedEndpoints = ConcurrentHashMap<String, String>()
    private val endpointNodeIds = ConcurrentHashMap<String, String>()
    private val pendingAcknowledgements = ConcurrentHashMap<String, CompletableDeferred<com.example.digitaldelta.proto.v1.Acknowledgement>>()

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            scope.launch {
                runCatching { PeerFrameCodec.decode(bytes) }
                    .onSuccess { body ->
                        when (body) {
                            is PeerFrameBody.EnvelopeBytes -> {
                                val acknowledgement = ingress.receive(body.wireBytes)
                                client.sendPayload(
                                    endpointId,
                                    Payload.fromBytes(PeerFrameCodec.encodeAcknowledgement(acknowledgement)),
                                ).addOnFailureListener { recordError(it) }
                            }

                            is PeerFrameBody.AcknowledgementMessage -> {
                                pendingAcknowledgements.remove(body.acknowledgement.messageId)
                                    ?.complete(body.acknowledgement)
                            }
                        }
                    }
                    .onFailure { recordError(it) }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val candidate = NearbyPeerCandidate(
                endpointId = endpointId,
                nodeId = info.endpointName,
                authenticationDigits = info.authenticationDigits,
            )
            endpointNodeIds[endpointId] = info.endpointName
            candidates[endpointId] = candidate
            publishPeers()
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            val nodeId = endpointNodeIds[endpointId]
            if (resolution.status.statusCode == ConnectionsStatusCodes.STATUS_OK && nodeId != null) {
                connectedEndpoints[nodeId] = endpointId
                candidates.remove(endpointId)
                publishPeers()
            } else {
                candidates.remove(endpointId)
                endpointNodeIds.remove(endpointId)
                recordError(IllegalStateException("connection rejected: ${resolution.status.statusCode}"))
            }
        }

        override fun onDisconnected(endpointId: String) {
            val nodeId = endpointNodeIds.remove(endpointId)
            if (nodeId != null) connectedEndpoints.remove(nodeId)
            candidates.remove(endpointId)
            publishPeers()
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            endpointNodeIds[endpointId] = info.endpointName
            mutableState.update { it.copy(discoveredNodeIds = it.discoveredNodeIds + info.endpointName) }
            if (localNodeId < info.endpointName) {
                client.requestConnection(localNodeId, endpointId, connectionLifecycleCallback)
                    .addOnFailureListener { recordError(it) }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            val nodeId = endpointNodeIds[endpointId] ?: return
            if (!connectedEndpoints.containsKey(nodeId)) {
                endpointNodeIds.remove(endpointId)
                mutableState.update { it.copy(discoveredNodeIds = it.discoveredNodeIds - nodeId) }
            }
        }
    }

    override fun start() {
        if (mutableState.value.running) return
        mutableState.value = NearbyMeshState(running = true)
        val strategy = Strategy.P2P_CLUSTER
        client.startAdvertising(
            localNodeId,
            SERVICE_ID,
            connectionLifecycleCallback,
            AdvertisingOptions.Builder().setStrategy(strategy).build(),
        ).addOnFailureListener { recordError(it) }
        client.startDiscovery(
            SERVICE_ID,
            discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(strategy).build(),
        ).addOnFailureListener { recordError(it) }
    }

    override fun acceptCandidate(endpointId: String) {
        require(candidates.containsKey(endpointId)) { "unknown connection candidate" }
        client.acceptConnection(endpointId, payloadCallback).addOnFailureListener { recordError(it) }
    }

    override fun rejectCandidate(endpointId: String) {
        candidates.remove(endpointId)
        client.rejectConnection(endpointId).addOnFailureListener { recordError(it) }
        publishPeers()
    }

    override suspend fun send(peerId: String, wireBytes: ByteArray): com.example.digitaldelta.proto.v1.Acknowledgement {
        val endpointId = connectedEndpoints[peerId] ?: error("peer $peerId is not connected")
        val envelope = MeshWireCodec.decode(wireBytes)
        val pending = CompletableDeferred<com.example.digitaldelta.proto.v1.Acknowledgement>()
        check(pendingAcknowledgements.putIfAbsent(envelope.messageId, pending) == null) {
            "message ${envelope.messageId} is already awaiting acknowledgement"
        }
        try {
            client.sendPayload(endpointId, Payload.fromBytes(PeerFrameCodec.encodeEnvelope(wireBytes))).awaitSuccess()
            return withTimeout(ACK_TIMEOUT_MILLIS) { pending.await() }
        } finally {
            pendingAcknowledgements.remove(envelope.messageId, pending)
        }
    }

    override fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        pendingAcknowledgements.values.forEach { it.cancel() }
        pendingAcknowledgements.clear()
        candidates.clear()
        connectedEndpoints.clear()
        endpointNodeIds.clear()
        mutableState.value = NearbyMeshState()
        scope.cancel()
    }

    private fun publishPeers() {
        mutableState.update {
            it.copy(
                pendingCandidates = candidates.values.sortedBy(NearbyPeerCandidate::nodeId),
                connectedNodeIds = connectedEndpoints.keys.toSortedSet(),
            )
        }
    }

    private fun recordError(error: Throwable) {
        mutableState.update { it.copy(lastError = error.message ?: error.javaClass.simpleName) }
    }

    private suspend fun com.google.android.gms.tasks.Task<Void>.awaitSuccess(): Unit =
        suspendCoroutine { continuation ->
            addOnSuccessListener { continuation.resume(Unit) }
            addOnFailureListener { continuation.resumeWithException(it) }
        }

    companion object {
        const val SERVICE_ID = "com.example.digitaldelta.mesh.v1"
        private const val ACK_TIMEOUT_MILLIS = 12_000L
    }
}
