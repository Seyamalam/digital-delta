package com.example.digitaldelta.domain.fleet

import androidx.room.withTransaction
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.data.local.OperationEntity
import com.example.digitaldelta.domain.pod.orderedCustodyEvents
import com.example.digitaldelta.domain.routing.*
import com.example.digitaldelta.domain.sync.*
import com.example.digitaldelta.domain.triage.*
import com.example.digitaldelta.proto.v1.DomainEvent
import com.example.digitaldelta.proto.v1.IdentityRole
import com.example.digitaldelta.proto.v1.MissionCustodySnapshot

/** Human-declared reservation, never vehicle telemetry or a custody receipt. */
data class DispatchReservation(val state: String, val operatorNodeId: String, val vehicle: VehicleType, val preemptedByMissionId: String? = null) {
    init {
        require(state in setOf(READY, HOLD))
        require(operatorNodeId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        require(vehicle in setOf(VehicleType.TRUCK, VehicleType.BOAT))
        require(preemptedByMissionId == null || (state == HOLD && preemptedByMissionId.matches(Regex("[A-Za-z0-9_-]{1,128}"))))
    }
    fun encode() = "$state|$operatorNodeId|${vehicle.name}" + (preemptedByMissionId?.let { "|$it" } ?: "")
    companion object {
        const val READY = "READY"
        const val HOLD = "HOLD_AT_ORIGIN"
        fun decode(value: String): DispatchReservation {
            val parts = value.split('|')
            require(parts.size in 3..4)
            return DispatchReservation(parts[0], parts[1], VehicleType.valueOf(parts[2]), parts.getOrNull(3))
        }
    }
}

private val missionVersionTypes = setOf("RELIEF_REQUEST_CREATED", "MISSION_FIELD_UPDATED", "CONFLICT_RESOLVED")

fun dispatchReviewIds(history: List<OperationEntity>): Set<String> = history
    .filter { it.eventType in missionVersionTypes || it.eventType in setOf("CUSTODY_TRANSFER", "CUSTODY_RECONCILED") }
    .map { it.eventId }.toSet()

/** After pickup the first receipt remains authoritative, including a crossing hold. */
fun dispatchVersion(history: List<OperationEntity>): Map<MissionField, String> {
    val first = orderedCustodyEvents(history).firstOrNull()
    val ids = first?.let { MissionCustodySnapshot.parseFrom(it.custodyTransfer.missionSnapshot).eventIdsList.toSet() }
    return projectMissionVersion(history.filter { it.eventType in missionVersionTypes && (ids == null || it.eventId in ids) }
        .map { DomainEvent.parseFrom(it.payloadBytes) })
}

/** One DISPATCH revision defines the complete reservation and itinerary atomically. */
fun dispatchCustodyPath(origin: String, destination: String, path: String?, dispatch: String?): List<String> =
    dispatch?.let { listOf(origin, DispatchReservation.decode(it).operatorNodeId, destination) }
        ?: path?.split('>') ?: listOf(origin, destination)

/** Conflict branches reserve conservatively until a human resolves them. */
fun reservedDispatchOperators(history: List<OperationEntity>): Set<String> {
    val request = history.firstOrNull { it.eventType == "RELIEF_REQUEST_CREATED" }
        ?.let { DomainEvent.parseFrom(it.payloadBytes).reliefRequestCreated } ?: return emptySet()
    val resolved = runCatching {
        val values = dispatchVersion(history)
        val path = dispatchCustodyPath(request.originNodeId, values.getValue(MissionField.DESTINATION), values[MissionField.CUSTODY_PATH], values[MissionField.DISPATCH])
        if (orderedCustodyEvents(history).size >= path.lastIndex) emptySet()
        else values[MissionField.DISPATCH]?.let(DispatchReservation::decode)?.let {
            if (it.state == DispatchReservation.READY) setOf(it.operatorNodeId) else emptySet()
        } ?: path.drop(1).dropLast(1).toSet()
    }
    return resolved.getOrElse {
        // An unresolved field must not make an already mentioned operator look free.
        history.filter { it.eventType == "MISSION_FIELD_UPDATED" }.flatMap { row ->
            val update = DomainEvent.parseFrom(row.payloadBytes).missionFieldUpdated
            when (update.fieldCode) {
                MissionField.DISPATCH.name -> runCatching { DispatchReservation.decode(update.value.toStringUtf8()) }
                    .getOrNull()?.let { if (it.state == DispatchReservation.READY) listOf(it.operatorNodeId) else emptyList() }.orEmpty()
                MissionField.CUSTODY_PATH.name -> update.value.toStringUtf8().split('>').drop(1).dropLast(1)
                else -> emptyList()
            }
        }.toSet()
    }
}

data class DispatchCommand(
    val missionId: String,
    val operatorNodeId: String,
    val vehicle: VehicleType,
    val reviewedEventIds: Set<String>,
    val heldMissionId: String? = null,
    val reviewedHeldEventIds: Set<String> = emptySet(),
)

class OperationalDispatchPlanner(
    private val database: DeltaDatabase,
    private val publisher: MissionEventPublisher,
    private val graph: TransportGraph,
    private val nowUnixMs: () -> Long = System::currentTimeMillis,
) {
    /** All local revisions and encrypted fan-out commit together or none do. */
    suspend fun confirm(command: DispatchCommand) = database.withTransaction {
        val now = nowUnixMs()
        val history = pending(command.missionId, command.reviewedEventIds)
        val creation = DomainEvent.parseFrom(history.single { it.eventType == "RELIEF_REQUEST_CREATED" }.payloadBytes)
        val request = creation.reliefRequestCreated
        val values = dispatchVersion(history)
        val destination = values.getValue(MissionField.DESTINATION)
        val driver = requireNotNull(database.recipientKeyDao().findByNodeId(command.operatorNodeId)) { "Operator is not provisioned" }
        require(driver.roleCode == IdentityRole.IDENTITY_ROLE_DRIVER.name && driver.revokedAtUnixMs == null &&
            driver.issuedAtUnixMs <= now && driver.expiresAtUnixMs > now) { "Operator authority is not active" }
        require(command.operatorNodeId in request.participantNodeIdsList && command.operatorNodeId !in setOf(request.originNodeId, destination))
        val reservation = DispatchReservation(DispatchReservation.READY, command.operatorNodeId, command.vehicle)
        require(request.createdAtUnixMs <= now) { "Request clock is in the future" }
        val route = RoutePlanner().findRoute(graph, request.originNodeId, destination, command.vehicle)
        require(route.edgeIds.isNotEmpty()) { "No transport leg to dispatch" }
        val priority = CargoPriority.entries[values.getValue(MissionField.PRIORITY).toInt() - 1]
        if (command.heldMissionId != null) {
            require(command.heldMissionId != command.missionId)
            val lowerHistory = pending(command.heldMissionId, command.reviewedHeldEventIds)
            val lowerCreation = DomainEvent.parseFrom(lowerHistory.single { it.eventType == "RELIEF_REQUEST_CREATED" }.payloadBytes)
            require(lowerCreation.reliefRequestCreated.participantNodeIdsList.toSet() == request.participantNodeIdsList.toSet()) {
                "Linked preemption needs the same frozen readers"
            }
            val lowerValues = dispatchVersion(lowerHistory)
            val lower = DispatchReservation.decode(requireNotNull(lowerValues[MissionField.DISPATCH]))
            require(lowerCreation.simulated == creation.simulated && lowerCreation.reliefRequestCreated.originNodeId == request.originNodeId)
            require(priority in setOf(CargoPriority.P0, CargoPriority.P1) && lowerValues.getValue(MissionField.PRIORITY).toInt() in 3..4)
            require(lower.state == DispatchReservation.READY && lower.operatorNodeId == command.operatorNodeId && lower.vehicle == command.vehicle)
            val elapsed = ((now - request.createdAtUnixMs) / 60_000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            require(TriageEngine().evaluate(priority, elapsed, RouteEtaEstimate(route.totalMinutes, now), now).willBreachSla)
            publisher.edit(command.heldMissionId, MissionField.DISPATCH, lower.copy(state = DispatchReservation.HOLD, preemptedByMissionId = command.missionId).encode())
        }
        for (other in database.operationLogDao().requests().filter { it.missionId != command.missionId }) {
            require(command.operatorNodeId !in reservedDispatchOperators(database.operationLogDao().forMission(other.missionId))) {
                "Operator has another active or conflicting reservation"
            }
        }
        publisher.edit(command.missionId, MissionField.DISPATCH, reservation.encode())
    }

    suspend fun hold(missionId: String, reviewedEventIds: Set<String>) = database.withTransaction {
        val values = dispatchVersion(pending(missionId, reviewedEventIds))
        val previous = DispatchReservation.decode(requireNotNull(values[MissionField.DISPATCH]))
        require(previous.state == DispatchReservation.READY)
        publisher.edit(missionId, MissionField.DISPATCH, previous.copy(state = DispatchReservation.HOLD).encode())
    }

    private suspend fun pending(missionId: String, expected: Set<String>): List<OperationEntity> {
        val history = database.operationLogDao().forMission(missionId)
        require(history.any { it.eventType == "RELIEF_REQUEST_CREATED" })
        require(dispatchReviewIds(history) == expected) { "Mission changed; review again" }
        require(history.none { it.eventType == "CUSTODY_TRANSFER" }) { "Custody already began; a plan cannot move cargo" }
        require(!database.conflictDao().hasOpen(missionId)) { "Resolve mission conflicts first" }
        return history
    }
}

/** First handoff is blocked by a hold, incomplete plan, or known double booking. */
suspend fun requireDispatchReady(database: DeltaDatabase, missionId: String) {
    val history = database.operationLogDao().forMission(missionId)
    if (orderedCustodyEvents(history).isNotEmpty()) return
    val values = dispatchVersion(history)
    val reservation = values[MissionField.DISPATCH]?.let(DispatchReservation::decode)
    if (reservation != null) {
        require(reservation.state == DispatchReservation.READY) { "Cargo is held at its pickup node" }
    }
    val operators = reservation?.let { setOf(it.operatorNodeId) }
        ?: values[MissionField.CUSTODY_PATH]?.split('>')?.drop(1)?.dropLast(1)?.toSet().orEmpty()
    for (other in database.operationLogDao().requests().filter { it.missionId != missionId }) {
        require(operators.intersect(reservedDispatchOperators(database.operationLogDao().forMission(other.missionId))).isEmpty()) { "Operator has competing reservations" }
    }
}
