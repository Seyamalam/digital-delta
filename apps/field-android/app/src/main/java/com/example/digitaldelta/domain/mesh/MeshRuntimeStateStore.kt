package com.example.digitaldelta.domain.mesh

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MeshRuntimeState(
    val nearby: NearbyMeshState = NearbyMeshState(),
    val batteryPercent: Int = 100,
    val broadcastIntervalMillis: Long = 10_000,
    val localNodeId: String = "",
    val pendingQueueDepth: Int = 0,
    val relaySelection: RelayRoleSelection = RelayRoleSelection(
        RelayRole.CLIENT_ONLY,
        RelayLinkQuality.UNKNOWN,
        proximityRecent = false,
    ),
)

@Singleton
class MeshRuntimeStateStore @Inject constructor() {
    private val mutableState = MutableStateFlow(MeshRuntimeState())
    val state: StateFlow<MeshRuntimeState> = mutableState.asStateFlow()

    fun publish(
        nearby: NearbyMeshState,
        batteryPercent: Int,
        broadcastIntervalMillis: Long,
        localNodeId: String = mutableState.value.localNodeId,
        pendingQueueDepth: Int = mutableState.value.pendingQueueDepth,
        relaySelection: RelayRoleSelection = mutableState.value.relaySelection,
    ) {
        mutableState.value = MeshRuntimeState(
            nearby,
            batteryPercent,
            broadcastIntervalMillis,
            localNodeId,
            pendingQueueDepth,
            relaySelection,
        )
    }

    fun reportPermissionDenied() {
        val current = mutableState.value
        mutableState.value = current.copy(
            nearby = current.nearby.copy(lastError = "PERMISSION_DENIED"),
        )
    }

    fun reset() {
        mutableState.value = MeshRuntimeState()
    }
}
