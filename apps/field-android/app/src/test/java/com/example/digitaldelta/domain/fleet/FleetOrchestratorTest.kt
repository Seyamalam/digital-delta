package com.example.digitaldelta.domain.fleet

import com.example.digitaldelta.domain.routing.EdgeMode
import com.example.digitaldelta.domain.routing.MapEdge
import com.example.digitaldelta.domain.routing.MapNode
import com.example.digitaldelta.domain.routing.TransportGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            candidates = listOf(
                NamedPoint("R1", GeoPoint(24.93, 91.76)),
                NamedPoint("R2", GeoPoint(24.95, 91.80)),
                NamedPoint("R3", GeoPoint(24.97, 91.86)),
            ),
            boatSpeedKph = 24.0,
            droneSpeedKph = 55.0,
        )

        assertEquals("R1", result.point.id)
        assertTrue(result.maxArrivalMinutes >= result.boatArrivalMinutes)
        assertTrue(result.maxArrivalMinutes >= result.droneArrivalMinutes)
        assertTrue(result.simulated)
    }
}
