package com.example.digitaldelta.domain.fleet

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.domain.routing.EdgeMode
import com.example.digitaldelta.domain.routing.MapEdge
import com.example.digitaldelta.domain.routing.MapNode
import com.example.digitaldelta.domain.routing.TransportGraph
import com.example.digitaldelta.proto.v1.DomainEvent
import com.example.digitaldelta.proto.v1.TransportMode
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomHybridFleetEventRecorderTest {
    private lateinit var database: DeltaDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DeltaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun close() = database.close()

    @Test
    fun rendezvousAndVehiclePhasesAreStoredAsParseableSimulatedProtobufEvents() = runTest {
        var now = 1_800_000_000_000L
        var sequence = 0
        val recorder = RoomHybridFleetEventRecorder(
            database = database,
            nowUnixMs = { now++ },
            eventId = { "fleet-event-${++sequence}" },
        )
        val plan = plan()

        recorder.recordRendezvous(plan)
        recorder.recordBoatArrival(plan)
        recorder.recordDroneArrival(plan)
        recorder.recordDroneCustodyAccepted(plan)

        val operations = database.operationLogDao().forMission(plan.mission.missionId)
        assertEquals(
            listOf("RENDEZVOUS_PLANNED", "VEHICLE_STATE_CHANGED", "VEHICLE_STATE_CHANGED", "VEHICLE_STATE_CHANGED"),
            operations.map { it.eventType },
        )
        val events = operations.map { DomainEvent.parseFrom(it.payloadBytes) }
        val rendezvous = events.first().rendezvousPlanned
        assertEquals(plan.rendezvous.point.id, rendezvous.candidateId)
        assertEquals(20, rendezvous.reserveBatteryPercent)
        assertTrue(rendezvous.projectedDroneBatteryPercent >= 20)
        assertTrue(events.all { it.simulated && it.scenarioSeed == "m8-hybrid-fleet-v1" })
        assertEquals("boat-02", events[1].vehicleStateChanged.vehicleId)
        assertEquals(TransportMode.TRANSPORT_MODE_WATERWAY, events[1].vehicleStateChanged.mode)
        assertEquals("drone-07", events[2].vehicleStateChanged.vehicleId)
        assertEquals(TransportMode.TRANSPORT_MODE_AIRWAY, events[2].vehicleStateChanged.mode)
        assertEquals("CUSTODY_ACCEPTED", events[3].vehicleStateChanged.stateCode)
    }

    @Test
    fun delayedBoatPositionAndReplanAreStoredWithoutInventingSensorData() = runTest {
        val recorder = RoomHybridFleetEventRecorder(
            database = database,
            nowUnixMs = { 1_800_000_000_000L },
            eventId = { "fleet-delay-event" },
        )
        val previous = plan()
        val revised = previous.copy(
            rendezvous = previous.rendezvous.copy(
                point = NamedPoint("R3", GeoPoint(25.0200, 91.7000)),
                boatStartDelayMinutes = 18.0,
            ),
        )
        val report = BoatDelayReport(18, GeoPoint(25.0400, 91.8000), simulated = true)

        recorder.recordBoatDelay(previous, revised, report)

        val operation = database.operationLogDao().forMission(previous.mission.missionId).single()
        val event = DomainEvent.parseFrom(operation.payloadBytes)
        assertEquals("VEHICLE_STATE_CHANGED", operation.eventType)
        assertEquals("DELAYED_18_MIN_R2_TO_R3", event.vehicleStateChanged.stateCode)
        assertEquals(report.observedPosition.latitude, event.vehicleStateChanged.latitudeDegrees, 0.000001)
        assertEquals(report.observedPosition.longitude, event.vehicleStateChanged.longitudeDegrees, 0.000001)
        assertTrue(event.simulated)
    }

    private fun plan(): HybridFleetPlan {
        val mission = HybridFleetMission(
            missionId = "mission-drone-demo-01",
            originNodeId = "N1",
            destinationNodeId = "N7",
            boatVehicleId = "boat-02",
            droneVehicleId = "drone-07",
            graph = TransportGraph(
                nodes = listOf(
                    MapNode("N1", "Sylhet Hub", 24.8949, 91.8687),
                    MapNode("N7", "Tanguar Haor Clinic", 25.12, 91.68),
                ),
                edges = listOf(MapEdge("A2", "N1", "N7", EdgeMode.AIRWAY, 28, simulated = true)),
            ),
            rendezvousInputs = HybridFleetInputs(
                boatPosition = GeoPoint(25.04, 91.57),
                droneBase = GeoPoint(24.9632, 91.8668),
                droneDestination = GeoPoint(25.12, 91.68),
                candidates = listOf(NamedPoint("R2", GeoPoint(25.0715, 91.7554))),
                boatSpeedKph = 24.0,
                droneSpeedKph = 55.0,
                droneBatteryPercent = 74,
                droneRangeAtFullChargeKm = 60.0,
                reserveBatteryPercent = 20,
            ),
            simulated = true,
        )
        return HybridFleetPlan(
            mission = mission,
            reachability = Reachability.DRONE_REQUIRED,
            rendezvous = FleetOrchestrator().computeRendezvous(mission.rendezvousInputs),
        )
    }
}
