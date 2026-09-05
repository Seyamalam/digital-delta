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
import kotlinx.coroutines.delay

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
    val canEdit: Boolean,
    val canResolve: Boolean,
    val delivered: Boolean,
    val custodyNeedsReconciliation: Boolean,
    val canRecordPlan: Boolean,
    val custodyPath: List<String>,
    val custodian: String,
    val canAssign: Boolean,
    val pendingCustodyChanges: List<Pair<MissionField, String>>,
    val pendingCustodyChangeIds: Set<String>,
)

@HiltViewModel
class MissionWorkspaceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    database: DeltaDatabase,
    profiles: DeviceProfileRepository,
    protector: MeshPayloadProtector,
    keys: AndroidDeviceIdentityKeyStore,
    trust: TrustAnchorRepository,
    private val selection: com.example.digitaldelta.domain.pod.MissionSelection,
) : ViewModel() {
    private val publisher = MissionEventPublisher(database, profiles, protector,
        AndroidEnvelopeSecurity(keys, database.recipientKeyDao(), trust)) { com.example.digitaldelta.service.ObserverPublication.schedule(context) }
    private val graph by lazy { SylhetMapParser().parse(context.assets.open("sylhet_map.json").bufferedReader().use { it.readText() }).graph }
    private val planRecorder by lazy { MissionPlanRecorder(database, profiles, keys, graph) { com.example.digitaldelta.service.ObserverPublication.schedule(context) } }
    val recordedPlan = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val failed = MutableStateFlow(false)
    val selectedMission = selection.missionId
    fun selectMission(id: String) = selection.select(id)
    private val ticks = flow { while (true) { emit(System.currentTimeMillis()); delay(1_000) } }
    private val authority = combine(profiles.profile, database.recipientKeyDao().observeAuthorities(), database.operationLogDao().observeMissionHistory()) { profile, authorities, history ->
        val credential = authorities.firstOrNull { it.nodeId == profile.nodeId }
        Triple(profile, credential, history)
    }
    val missions = combine(database.operationLogDao().observeRequests(), database.missionProjectionDao().observeAll(), database.conflictDao().observeOpen(), authority, ticks) { requests, projections, conflicts, authority, now ->
        val (profile, credential, history) = authority
        val public = keys.createOrGet(profile.nodeId)
        val active = credential != null && credential.identityId == profile.identityId && credential.revokedAtUnixMs == null && credential.issuedAtUnixMs <= now && credential.expiresAtUnixMs > now &&
            credential.signingKeyId == public.signingKeyId && credential.signingPublicKeyDer.contentEquals(public.signingPublicKeyDer)
        val coordinator = active && credential?.roleCode == com.example.digitaldelta.proto.v1.IdentityRole.IDENTITY_ROLE_COORDINATOR.name
        requests.map { operation ->
            val event = DomainEvent.parseFrom(operation.payloadBytes)
            val request = event.reliefRequestCreated
            val missionHistory = history.filter { it.missionId == operation.missionId }
            val receipts = com.example.digitaldelta.domain.pod.orderedCustodyEvents(missionHistory)
            val receipt = receipts.firstOrNull()?.custodyTransfer
            val pinnedIds = receipt?.let { runCatching { com.example.digitaldelta.proto.v1.MissionCustodySnapshot.parseFrom(it.missionSnapshot).eventIdsList.toSet() }.getOrDefault(emptySet()) }
            val fields = projections.filter { it.missionId == operation.missionId }.associateBy { it.fieldCode }
            val values = if (pinnedIds == null) fields.mapValues { it.value.value } else projectMissionVersion(missionHistory.filter { it.eventId in pinnedIds }.map { DomainEvent.parseFrom(it.payloadBytes) }).mapKeys { it.key.name }
            val destination = values["DESTINATION"] ?: request.destinationNodeId
            val path = values["CUSTODY_PATH"]?.split(">") ?: listOf(request.originNodeId, destination)
            val priority = CargoPriority.entries[((values["PRIORITY"]?.toIntOrNull() ?: request.cargoList.minOf { it.priorityValue }) - 1).coerceIn(0, 3)]
            val route = listOf(VehicleType.TRUCK, VehicleType.BOAT).mapNotNull { vehicle ->
                runCatching { RoutePlanner().findRoute(graph, request.originNodeId, destination, vehicle) }.getOrNull()
            }.minByOrNull { it.totalMinutes }
            FieldMission(operation.missionId, request.originNodeId, destination, priority,
                values["MEDICAL_QUANTITY"] ?: "—", fields.values.firstOrNull()?.convergenceHash.orEmpty(), event.simulated, route,
                route?.let { TriageEngine().evaluate(priority, ((now - request.createdAtUnixMs).coerceAtLeast(0) / 60_000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), it.totalMinutes) },
                conflicts.filter { it.missionId == operation.missionId },
                canEdit = receipt == null && active && (coordinator || event.actorIdentityId == profile.identityId || (profile.role == com.example.digitaldelta.proto.v1.IdentityRole.IDENTITY_ROLE_HOSPITAL && request.destinationNodeId == profile.nodeId)) && conflicts.none { it.missionId == operation.missionId },
                canResolve = coordinator,
                delivered = receipts.size >= path.lastIndex,
                custodyNeedsReconciliation = com.example.digitaldelta.domain.pod.custodyNeedsReconciliation(missionHistory),
                canRecordPlan = active && receipt == null && conflicts.none { it.missionId == operation.missionId } &&
                    (profile.nodeId in request.participantNodeIdsList || profile.nodeId in setOf(request.requesterNodeId, request.originNodeId, request.destinationNodeId)),
                custodyPath = path, custodian = path[receipts.size.coerceAtMost(path.lastIndex)],
                canAssign = coordinator && receipt == null && conflicts.none { it.missionId == operation.missionId },
                pendingCustodyChangeIds = com.example.digitaldelta.domain.pod.unreconciledCustodyChanges(missionHistory).map { it.eventId }.toSet(),
                pendingCustodyChanges = com.example.digitaldelta.domain.pod.unreconciledCustodyChanges(missionHistory).mapNotNull { change ->
                    when {
                        change.hasMissionFieldUpdated() -> MissionField.valueOf(change.missionFieldUpdated.fieldCode) to change.missionFieldUpdated.value.toStringUtf8()
                        change.hasConflictResolved() -> change.conflictResolved.let { resolution ->
                            val code = resolution.fieldCode.ifBlank { database.conflictDao().find(resolution.conflictId)?.fieldCode.orEmpty() }
                            MissionField.valueOf(code) to resolution.selectedValue.toStringUtf8()
                        }
                        else -> null
                    }
                })
        }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun edit(id: String, field: MissionField, value: String) = act { publisher.edit(id, field, value) }
    fun resolve(id: String, side: ConflictSide) = act { publisher.resolve(id, side) }
    fun recordPlan(id: String) = act { planRecorder.record(id); recordedPlan.value = id }
    fun reconcile(id: String, reason: String, expectedChanges: Set<String>) = act { publisher.reconcile(id, reason, expectedChanges) }
    private fun act(action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true; failed.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try { action() } catch (_: Exception) { failed.value = true } finally { busy.value = false }
        }
    }
}
