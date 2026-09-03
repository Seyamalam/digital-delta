package com.example.digitaldelta.domain.fleet

import com.example.digitaldelta.domain.routing.EdgeMode
import com.example.digitaldelta.domain.routing.MapEdge
import com.example.digitaldelta.domain.routing.MapNode
import com.example.digitaldelta.domain.routing.TransportGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class FleetOrchestratorTest {
    private val orchestrator = FleetOrchestrator()

    @Test
    fun `air only destination is classified drone required`() {
        val graph = TransportGraph(
            nodes = listOf(
                MapNode("hub", "Hub", 24.89, 91.86),
                MapNode("island", "Island", 25.02, 91.62),
            ),
            edges = listOf(MapEdge("air-1", "hub", "island", EdgeMode.AIRWAY, 24)),
        )

        assertEquals(
            Reachability.DRONE_REQUIRED,
            orchestrator.classifyReachability(graph, "hub", "island"),
        )
    }

    @Test
    fun `rendezvous minimizes the later agent arrival`() {
        val result = orchestrator.computeRendezvous(
            boatPosition = GeoPoint(24.90, 91.70),
            droneBase = GeoPoint(24.98, 91.88),
            droneDestination = GeoPoint(25.03, 91.73),
            candidates = listOf(
                NamedPoint("R1", GeoPoint(24.93, 91.76)),
                NamedPoint("R2", GeoPoint(24.95, 91.80)),
                NamedPoint("R3", GeoPoint(24.97, 91.86)),
            ),
            boatSpeedKph = 24.0,
            droneSpeedKph = 55.0,
            droneBatteryPercent = 75,
            droneRangeAtFullChargeKm = 60.0,
            reserveBatteryPercent = 20,
        )

        assertEquals("R1", result.point.id)
        assertTrue(result.maxArrivalMinutes >= result.boatArrivalMinutes)
        assertTrue(result.maxArrivalMinutes >= result.droneArrivalMinutes)
        assertTrue(result.projectedDroneBatteryPercent >= 20)
        assertTrue(result.simulated)
    }

    @Test
    fun `low battery rejects every rendezvous that would violate reserve`() {
        assertThrows(NoFeasibleRendezvousException::class.java) {
            orchestrator.computeRendezvous(
                boatPosition = GeoPoint(24.90, 91.70),
                droneBase = GeoPoint(24.98, 91.88),
                droneDestination = GeoPoint(25.10, 91.55),
                candidates = listOf(
                    NamedPoint("R1", GeoPoint(24.93, 91.76)),
                    NamedPoint("R2", GeoPoint(24.95, 91.80)),
                ),
                boatSpeedKph = 24.0,
                droneSpeedKph = 55.0,
                droneBatteryPercent = 24,
                droneRangeAtFullChargeKm = 40.0,
                reserveBatteryPercent = 20,
            )
        }
    }

    @Test
    fun `changed destination can select a different feasible rendezvous`() {
        val candidates = listOf(
            NamedPoint("WEST", GeoPoint(24.93, 91.72)),
            NamedPoint("EAST", GeoPoint(24.96, 91.86)),
        )
        val common = HybridFleetInputs(
            boatPosition = GeoPoint(24.94, 91.76),
            droneBase = GeoPoint(24.98, 91.88),
            droneDestination = GeoPoint(25.02, 91.60),
            candidates = candidates,
            boatSpeedKph = 40.0,
            droneSpeedKph = 55.0,
            droneBatteryPercent = 80,
            droneRangeAtFullChargeKm = 70.0,
            reserveBatteryPercent = 20,
        )

        val west = orchestrator.computeRendezvous(
            common.copy(droneDestination = GeoPoint(25.02, 91.60)),
        )
        val east = orchestrator.computeRendezvous(
            common.copy(droneDestination = GeoPoint(25.02, 92.00)),
        )

        assertEquals("WEST", west.point.id)
        assertEquals("EAST", east.point.id)
    }

    @Test
    fun `destination with no supported edge is unreachable`() {
        val graph = TransportGraph(
            nodes = listOf(
                MapNode("hub", "Hub", 24.89, 91.86),
                MapNode("cut-off", "Cut off", 25.02, 91.62),
            ),
            edges = emptyList(),
        )

        assertEquals(
            Reachability.UNREACHABLE,
            orchestrator.classifyReachability(graph, "hub", "cut-off"),
        )
    }
}
