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
    val authenticatingNodeIds: Set<String> = emptySet(),
    val connectedNodeIds: Set<String> = emptySet(),
    val authenticatedPeerKeyIds: Map<String, String> = emptyMap(),
    val peerLastContactUnixMs: Map<String, Long> = emptyMap(),
    val peerAcknowledgementRoundTripMillis: Map<String, Long> = emptyMap(),
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
    private val identityAuthenticator: PeerIdentityAuthenticator,
    private val client: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : NearbyMeshController {
    private val mutableState = MutableStateFlow(NearbyMeshState())
    override val state: StateFlow<NearbyMeshState> = mutableState.asStateFlow()
    private val candidates = ConcurrentHashMap<String, NearbyPeerCandidate>()
    private val acceptedEndpoints = ConcurrentHashMap<String, String>()
    private val connectedEndpoints = ConcurrentHashMap<String, String>()
    private val endpointNodeIds = ConcurrentHashMap<String, String>()
    private val authenticatedPeerKeyIds = ConcurrentHashMap<String, String>()
    private val peerLastContactUnixMs = ConcurrentHashMap<String, Long>()
    private val peerAcknowledgementRoundTripMillis = ConcurrentHashMap<String, Long>()
    private val pendingChallenges = PendingPeerChallenges()
    private val pendingAcknowledgements = ConcurrentHashMap<String, CompletableDeferred<com.example.digitaldelta.proto.v1.Acknowledgement>>()

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            recordPeerContact(endpointId)
            scope.launch {
                try {
                    when (val body = PeerFrameCodec.decode(bytes)) {
                        is PeerFrameBody.IdentityChallengeMessage -> {
                            val expectedNodeId = endpointNodeIds[endpointId]
                            if (expectedNodeId == null || body.challenge.challengerNodeId != expectedNodeId) {
                                rejectAuthentication(endpointId, "PEER_CHALLENGE_NODE_MISMATCH")
                                return@launch
                            }
                            val proof = identityAuthenticator.createProof(body.challenge)
                            client.sendPayload(
                                endpointId,
                                Payload.fromBytes(PeerFrameCodec.encodeIdentityProof(proof)),
                            ).addOnFailureListener { recordError(it) }
                        }

                        is PeerFrameBody.IdentityProofMessage -> authenticateProof(endpointId, body)

                        is PeerFrameBody.EnvelopeBytes -> {
                            if (!isAuthenticated(endpointId)) {
                                rejectAuthentication(endpointId, "UNAUTHENTICATED_ENVELOPE")
                                return@launch
                            }
                            val acknowledgement = ingress.receive(body.wireBytes, endpointNodeIds[endpointId])
                            client.sendPayload(
                                endpointId,
                                Payload.fromBytes(PeerFrameCodec.encodeAcknowledgement(acknowledgement)),
                            ).addOnFailureListener { recordError(it) }
                        }

                        is PeerFrameBody.AcknowledgementMessage -> {
                            if (!isAuthenticated(endpointId)) {
                                rejectAuthentication(endpointId, "UNAUTHENTICATED_ACKNOWLEDGEMENT")
                                return@launch
                            }
                            pendingAcknowledgements.remove(body.acknowledgement.messageId)
                                ?.complete(body.acknowledgement)
                        }
                    }
                } catch (error: Throwable) {
                    rejectAuthentication(endpointId, error.message ?: error.javaClass.simpleName)
                }
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
                val existingEndpoint = acceptedEndpoints.putIfAbsent(nodeId, endpointId)
                if (existingEndpoint != null && existingEndpoint != endpointId) {
                    rejectAuthentication(endpointId, "DUPLICATE_NODE_CONNECTION")
                    return
                }
                candidates.remove(endpointId)
                publishPeers()
                scope.launch {
                    runCatching {
                        val challenge = identityAuthenticator.createChallenge()
                        pendingChallenges.put(endpointId, challenge)
                        client.sendPayload(
                            endpointId,
                            Payload.fromBytes(PeerFrameCodec.encodeIdentityChallenge(challenge)),
                        ).awaitSuccess()
                    }.onFailure { rejectAuthentication(endpointId, it.message ?: "PEER_CHALLENGE_FAILED") }
                }
            } else {
                candidates.remove(endpointId)
                endpointNodeIds.remove(endpointId)
                recordError(IllegalStateException("connection rejected: ${resolution.status.statusCode}"))
            }
        }

        override fun onDisconnected(endpointId: String) {
            val nodeId = endpointNodeIds.remove(endpointId)
            if (nodeId != null) {
                acceptedEndpoints.remove(nodeId, endpointId)
                if (connectedEndpoints.remove(nodeId, endpointId)) {
                    authenticatedPeerKeyIds.remove(nodeId)
                }
            }
            pendingChallenges.remove(endpointId)
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
            if (!connectedEndpoints.containsKey(nodeId) && !acceptedEndpoints.containsKey(nodeId)) {
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
        if (!isAuthenticated(endpointId)) {
            rejectAuthentication(endpointId, "PEER_AUTHORITY_EXPIRED_OR_REVOKED")
            throw SecurityException("Peer or local authority is no longer active")
        }
        val envelope = MeshWireCodec.decode(wireBytes)
        val pending = CompletableDeferred<com.example.digitaldelta.proto.v1.Acknowledgement>()
        check(pendingAcknowledgements.putIfAbsent(envelope.messageId, pending) == null) {
            "message ${envelope.messageId} is already awaiting acknowledgement"
        }
        try {
            val startedAt = System.currentTimeMillis()
            client.sendPayload(endpointId, Payload.fromBytes(PeerFrameCodec.encodeEnvelope(wireBytes))).awaitSuccess()
            val acknowledgement = withTimeout(ACK_TIMEOUT_MILLIS) { pending.await() }
            recordPeerContact(endpointId, System.currentTimeMillis() - startedAt)
            return acknowledgement
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
        acceptedEndpoints.clear()
        connectedEndpoints.clear()
        authenticatedPeerKeyIds.clear()
        peerLastContactUnixMs.clear()
        peerAcknowledgementRoundTripMillis.clear()
        pendingChallenges.clear()
        endpointNodeIds.clear()
        mutableState.value = NearbyMeshState()
        scope.cancel()
    }

    private fun publishPeers() {
        mutableState.update {
            it.copy(
                pendingCandidates = candidates.values.sortedBy(NearbyPeerCandidate::nodeId),
                authenticatingNodeIds = (acceptedEndpoints.keys - connectedEndpoints.keys).toSortedSet(),
                connectedNodeIds = connectedEndpoints.keys.toSortedSet(),
                authenticatedPeerKeyIds = authenticatedPeerKeyIds.toSortedMap(),
                peerLastContactUnixMs = peerLastContactUnixMs.toSortedMap(),
                peerAcknowledgementRoundTripMillis = peerAcknowledgementRoundTripMillis.toSortedMap(),
            )
        }
    }

    private fun recordPeerContact(endpointId: String, acknowledgementRoundTripMillis: Long? = null) {
        val nodeId = endpointNodeIds[endpointId] ?: return
        peerLastContactUnixMs[nodeId] = System.currentTimeMillis()
        acknowledgementRoundTripMillis?.let { peerAcknowledgementRoundTripMillis[nodeId] = it }
        publishPeers()
    }

    private suspend fun authenticateProof(endpointId: String, body: PeerFrameBody.IdentityProofMessage) {
        val expectedNodeId = endpointNodeIds[endpointId]
        val challenge = pendingChallenges.expected(endpointId)
        if (expectedNodeId == null || challenge == null ||
            !identityAuthenticator.verifyProof(body.proof, challenge, expectedNodeId) ||
            !pendingChallenges.consume(endpointId, challenge)
        ) {
            rejectAuthentication(endpointId, "PEER_IDENTITY_REJECTED")
            return
        }
        connectedEndpoints[expectedNodeId] = endpointId
        authenticatedPeerKeyIds[expectedNodeId] = body.proof.nodeSignature.keyId
        mutableState.update { it.copy(lastError = null) }
        publishPeers()
    }

    private suspend fun isAuthenticated(endpointId: String): Boolean {
        val nodeId = endpointNodeIds[endpointId] ?: return false
        return connectedEndpoints[nodeId] == endpointId && identityAuthenticator.isActive(nodeId) && identityAuthenticator.isActive(localNodeId)
    }

    private fun rejectAuthentication(endpointId: String, reason: String) {
        pendingChallenges.remove(endpointId)
        endpointNodeIds[endpointId]?.let { nodeId ->
            acceptedEndpoints.remove(nodeId, endpointId)
            if (connectedEndpoints.remove(nodeId, endpointId)) {
                authenticatedPeerKeyIds.remove(nodeId)
            }
        }
        candidates.remove(endpointId)
        client.disconnectFromEndpoint(endpointId)
        recordError(SecurityException(reason))
        publishPeers()
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
