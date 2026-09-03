package com.example.digitaldelta.domain.fleet

import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.data.local.OperationEntity
import com.example.digitaldelta.proto.v1.DomainEvent
import com.example.digitaldelta.proto.v1.RendezvousPlanned
import com.example.digitaldelta.proto.v1.TransportMode
import com.example.digitaldelta.proto.v1.VehicleStateChanged
import java.util.UUID
import kotlin.math.roundToInt

class RoomHybridFleetEventRecorder(
    private val database: DeltaDatabase,
    private val nowUnixMs: () -> Long = System::currentTimeMillis,
    private val eventId: () -> String = { UUID.randomUUID().toString() },
) : HybridFleetEventRecorder {
    override suspend fun recordRendezvous(plan: HybridFleetPlan) {
        val rendezvous = plan.rendezvous
        val body = RendezvousPlanned.newBuilder()
            .setMissionId(plan.mission.missionId)
            .setBoatVehicleId(plan.mission.boatVehicleId)
            .setDroneVehicleId(plan.mission.droneVehicleId)
            .setCandidateId(rendezvous.point.id)
            .setLatitudeDegrees(rendezvous.point.coordinate.latitude)
            .setLongitudeDegrees(rendezvous.point.coordinate.longitude)
            .setBoatEtaMinutes(rendezvous.boatArrivalMinutes.roundToProtoUInt())
            .setDroneEtaMinutes(rendezvous.droneArrivalMinutes.roundToProtoUInt())
            .setDeliveryEtaMinutes(rendezvous.deliveryArrivalMinutes.roundToProtoUInt())
            .setProjectedDroneBatteryPercent(rendezvous.projectedDroneBatteryPercent.roundToProtoUInt())
            .setReserveBatteryPercent(plan.mission.rendezvousInputs.reserveBatteryPercent)
            .setObjectiveCode(rendezvous.objective)
            .setSimulated(plan.mission.simulated)
            .build()
        append(plan, EVENT_RENDEZVOUS_PLANNED) { it.setRendezvousPlanned(body) }
    }

    override suspend fun recordBoatArrival(plan: HybridFleetPlan) {
        recordVehicle(
            plan = plan,
            vehicleId = plan.mission.boatVehicleId,
            mode = TransportMode.TRANSPORT_MODE_WATERWAY,
            stateCode = "ARRIVED_AT_RENDEZVOUS",
            batteryPercent = 0,
        )
    }

    override suspend fun recordDroneArrival(plan: HybridFleetPlan) {
        recordVehicle(
            plan = plan,
            vehicleId = plan.mission.droneVehicleId,
            mode = TransportMode.TRANSPORT_MODE_AIRWAY,
            stateCode = "ARRIVED_AT_RENDEZVOUS",
            batteryPercent = plan.rendezvous.projectedDroneBatteryPercent.roundToInt(),
        )
    }

    override suspend fun recordDroneCustodyAccepted(plan: HybridFleetPlan) {
        recordVehicle(
            plan = plan,
            vehicleId = plan.mission.droneVehicleId,
            mode = TransportMode.TRANSPORT_MODE_AIRWAY,
            stateCode = "CUSTODY_ACCEPTED",
            batteryPercent = plan.rendezvous.projectedDroneBatteryPercent.roundToInt(),
        )
    }

    private suspend fun recordVehicle(
        plan: HybridFleetPlan,
        vehicleId: String,
        mode: TransportMode,
        stateCode: String,
        batteryPercent: Int,
    ) {
        val coordinate = plan.rendezvous.point.coordinate
        val body = VehicleStateChanged.newBuilder()
            .setVehicleId(vehicleId)
            .setMode(mode)
            .setStateCode(stateCode)
            .setNodeId(plan.rendezvous.point.id)
            .setLatitudeDegrees(coordinate.latitude)
            .setLongitudeDegrees(coordinate.longitude)
            .setBatteryPercent(batteryPercent.coerceIn(0, 100))
            .setSimulated(plan.mission.simulated)
            .build()
        append(plan, EVENT_VEHICLE_STATE_CHANGED) { it.setVehicleStateChanged(body) }
    }

    private suspend fun append(
        plan: HybridFleetPlan,
        eventType: String,
        setBody: (DomainEvent.Builder) -> DomainEvent.Builder,
    ) {
        val occurredAt = nowUnixMs()
        val id = eventId()
        val event = setBody(
            DomainEvent.newBuilder()
                .setEventId(id)
                .setSchemaVersion(1)
                .setActorIdentityId(ACTOR_IDENTITY)
                .setOccurredAtUnixMs(occurredAt)
                .setSimulated(plan.mission.simulated)
                .setScenarioSeed(SCENARIO_SEED),
        ).build()
        database.operationLogDao().append(
            OperationEntity(
                eventId = id,
                missionId = plan.mission.missionId,
                eventType = eventType,
                payloadBytes = event.toByteArray(),
                createdAtUnixMs = occurredAt,
            ),
        )
    }

    private fun Double.roundToProtoUInt(): Int = roundToInt().coerceAtLeast(0)

    companion object {
        const val EVENT_RENDEZVOUS_PLANNED = "RENDEZVOUS_PLANNED"
        const val EVENT_VEHICLE_STATE_CHANGED = "VEHICLE_STATE_CHANGED"
        const val SCENARIO_SEED = "m8-hybrid-fleet-v1"
        private const val ACTOR_IDENTITY = "hybrid-fleet-orchestrator"
    }
}
