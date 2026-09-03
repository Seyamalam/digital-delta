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
)

@Singleton
class MeshRuntimeStateStore @Inject constructor() {
    private val mutableState = MutableStateFlow(MeshRuntimeState())
    val state: StateFlow<MeshRuntimeState> = mutableState.asStateFlow()

    fun publish(nearby: NearbyMeshState, batteryPercent: Int, broadcastIntervalMillis: Long) {
        mutableState.value = MeshRuntimeState(nearby, batteryPercent, broadcastIntervalMillis)
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
