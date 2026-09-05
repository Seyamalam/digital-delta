package com.example.digitaldelta.domain.routing

import androidx.room.withTransaction
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.data.local.OperationEntity
import com.example.digitaldelta.domain.identity.*
import com.example.digitaldelta.domain.triage.CargoPriority
import com.example.digitaldelta.domain.triage.TriageEngine
import com.example.digitaldelta.proto.v1.*
import kotlinx.coroutines.flow.first
import java.util.UUID

/** A local planning snapshot, not an assignment, road report or delivery authority. */
class MissionPlanRecorder(
    private val database: DeltaDatabase,
    private val profiles: DeviceProfileRepository,
    private val keys: AndroidDeviceIdentityKeyStore,
    private val graph: TransportGraph,
    private val afterCommit: () -> Unit = {},
) {
    suspend fun record(missionId: String): List<DomainEvent> = database.withTransaction {
        val now = System.currentTimeMillis()
        val profile = profiles.profile.first()
        val credential = requireNotNull(database.recipientKeyDao().findByNodeId(profile.nodeId))
        val public = keys.createOrGet(profile.nodeId)
        require(credential.identityId == profile.identityId && credential.signingKeyId == public.signingKeyId &&
            credential.signingPublicKeyDer.contentEquals(public.signingPublicKeyDer))
        require(credential.issuedAtUnixMs <= now && credential.expiresAtUnixMs > now && credential.revokedAtUnixMs == null)
        require(credential.roleCode in setOf(IdentityRole.IDENTITY_ROLE_COORDINATOR.name, IdentityRole.IDENTITY_ROLE_CLINIC.name, IdentityRole.IDENTITY_ROLE_HOSPITAL.name))
        val history = database.operationLogDao().forMission(missionId)
        require(history.none { it.eventType == "CUSTODY_TRANSFER" }) { "Delivery already accepted" }
        require(!database.conflictDao().hasOpen(missionId)) { "Resolve conflicts before recording a plan" }
        val creation = history.single { it.eventType == "RELIEF_REQUEST_CREATED" }.let { DomainEvent.parseFrom(it.payloadBytes) }
        val request = creation.reliefRequestCreated
        require(profile.nodeId in request.participantNodeIdsList || profile.nodeId in setOf(request.requesterNodeId, request.originNodeId, request.destinationNodeId))
        val fields = database.missionProjectionDao().forMission(missionId).associate { it.fieldCode to it.value }
        val destination = fields["DESTINATION"] ?: request.destinationNodeId
        val priorityValue = fields["PRIORITY"]?.toInt() ?: request.cargoList.minOf { it.priorityValue }
        val priority = CargoPriority.entries[priorityValue - 1]
        val plan = listOf(VehicleType.TRUCK, VehicleType.BOAT).mapNotNull { vehicle ->
            try { vehicle to RoutePlanner().findRoute(graph, request.originNodeId, destination, vehicle) }
            catch (_: RouteNotFoundException) { null }
        }.minByOrNull { it.second.totalMinutes }
        fun base() = DomainEvent.newBuilder().setEventId(UUID.randomUUID().toString()).setSchemaVersion(1)
            .setActorIdentityId(profile.identityId).setOccurredAtUnixMs(now)
            // Real request IDs do not make packaged edge travel times real measurements.
            .setSimulated(true).setScenarioSeed("packaged-network-v1")
        val route = base().setRoutePlanned(RoutePlanned.newBuilder().setMissionId(missionId)
            .setVehicleId(if (plan?.first == VehicleType.BOAT) "planning-boat" else "planning-truck")
            .setMode(if (plan?.first == VehicleType.BOAT) TransportMode.TRANSPORT_MODE_WATERWAY else TransportMode.TRANSPORT_MODE_ROAD)
            .addAllEdgeIds(plan?.second?.edgeIds.orEmpty()).setEtaMinutes(plan?.second?.totalMinutes ?: 0)
            .setRiskAdjusted(plan?.second?.riskAdjusted ?: false)
            .setExplanationCode(if (plan == null) "NO_FEASIBLE_GROUND_ROUTE" else "PACKAGED_NETWORK_ESTIMATE")).build()
        val events = mutableListOf(route)
        if (plan != null) {
            val elapsed = ((now - request.createdAtUnixMs).coerceAtLeast(0) / 60_000).coerceAtMost(500_000).toInt()
            val decision = TriageEngine().evaluate(priority, elapsed, plan.second.totalMinutes)
            if (decision.willBreachSla) events += base().setSlaBreachPredicted(SlaBreachPredicted.newBuilder().setMissionId(missionId)
                .setPriority(PriorityClass.forNumber(priorityValue)).setBaselineEtaMinutes(decision.baselineArrivalMinutes)
                .setSlowedEtaMinutes(decision.slowedArrivalMinutes).setSlaMinutes(priority.slaMinutes)
                .setPolicyVersion("triage-v1-packaged-estimate")).build()
        }
        for (event in events) database.operationLogDao().append(OperationEntity(event.eventId, missionId,
            event.bodyCase.name, event.toByteArray(), now))
        events
    }.also { runCatching { afterCommit() } }
}
