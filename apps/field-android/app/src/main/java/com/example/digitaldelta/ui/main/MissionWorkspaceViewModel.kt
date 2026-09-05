package com.example.digitaldelta.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.digitaldelta.data.local.*
import com.example.digitaldelta.domain.identity.*
import com.example.digitaldelta.domain.mesh.*
import com.example.digitaldelta.domain.sync.*
import com.example.digitaldelta.domain.routing.*
import com.example.digitaldelta.domain.triage.*
import com.example.digitaldelta.proto.v1.DomainEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FieldMission(
    val id: String,
    val origin: String,
    val destination: String,
    val priority: CargoPriority,
    val medicalQuantity: String,
    val hash: String,
    val simulated: Boolean,
    val route: PlannedRoute?,
    val triage: TriageDecision?,
    val conflicts: List<ConflictEntity>,
)

@HiltViewModel
class MissionWorkspaceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    database: DeltaDatabase,
    profiles: DeviceProfileRepository,
    protector: MeshPayloadProtector,
    keys: AndroidDeviceIdentityKeyStore,
    trust: TrustAnchorRepository,
) : ViewModel() {
    private val publisher = MissionEventPublisher(database, profiles, protector,
        AndroidEnvelopeSecurity(keys, database.recipientKeyDao(), trust)) { com.example.digitaldelta.service.ObserverPublication.schedule(context) }
    private val graph by lazy { SylhetMapParser().parse(context.assets.open("sylhet_map.json").bufferedReader().use { it.readText() }).graph }
    val busy = MutableStateFlow(false)
    val failed = MutableStateFlow(false)
    val missions = combine(database.operationLogDao().observeRequests(), database.missionProjectionDao().observeAll(), database.conflictDao().observeOpen()) { requests, projections, conflicts ->
        requests.map { operation ->
            val event = DomainEvent.parseFrom(operation.payloadBytes)
            val request = event.reliefRequestCreated
            val fields = projections.filter { it.missionId == operation.missionId }.associateBy { it.fieldCode }
            val destination = fields["DESTINATION"]?.value ?: request.destinationNodeId
            val priority = CargoPriority.entries[((fields["PRIORITY"]?.value?.toIntOrNull() ?: request.cargoList.minOf { it.priorityValue }) - 1).coerceIn(0, 3)]
            val route = listOf(VehicleType.TRUCK, VehicleType.BOAT).mapNotNull { vehicle ->
                runCatching { RoutePlanner().findRoute(graph, request.originNodeId, destination, vehicle) }.getOrNull()
            }.minByOrNull { it.totalMinutes }
            FieldMission(operation.missionId, request.originNodeId, destination, priority,
                fields["MEDICAL_QUANTITY"]?.value ?: "—", fields.values.firstOrNull()?.convergenceHash.orEmpty(), event.simulated, route,
                route?.let { TriageEngine().evaluate(priority, ((System.currentTimeMillis() - request.createdAtUnixMs).coerceAtLeast(0) / 60_000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), it.totalMinutes) },
                conflicts.filter { it.missionId == operation.missionId })
        }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun edit(id: String, field: MissionField, value: String) = act { publisher.edit(id, field, value) }
    fun resolve(id: String, side: ConflictSide) = act { publisher.resolve(id, side) }
    private fun act(action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true; failed.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try { action() } catch (_: Exception) { failed.value = true } finally { busy.value = false }
        }
    }
}
