package com.example.digitaldelta.domain.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlannerTest {
    private val graph = TransportGraph(
        nodes = listOf(
            MapNode("N1", "Sylhet Hub", 24.8949, 91.8687),
            MapNode("N2", "Airport", 24.9632, 91.8668),
            MapNode("N3", "Sunamganj", 25.0658, 91.4073),
            MapNode("N4", "Companyganj", 25.0715, 91.7554),
        ),
        edges = listOf(
            MapEdge("road-direct", "N1", "N3", EdgeMode.ROAD, 90),
            MapEdge("road-airport", "N1", "N2", EdgeMode.ROAD, 20),
            MapEdge("road-company", "N2", "N4", EdgeMode.ROAD, 45),
            MapEdge("river-sunamganj", "N1", "N3", EdgeMode.WATERWAY, 150),
            MapEdge("river-company", "N3", "N4", EdgeMode.WATERWAY, 50),
            MapEdge("air-company", "N1", "N4", EdgeMode.AIRWAY, 28),
        ),
    )

    @Test
    fun `truck route excludes waterway and airway edges`() {
        val route = RoutePlanner().findRoute(graph, "N1", "N4", VehicleType.TRUCK)

        assertEquals(listOf("road-airport", "road-company"), route.edgeIds)
        assertEquals(65, route.totalMinutes)
        assertTrue(route.explanation.contains("TRUCK"))
    }

    @Test
    fun `failed road triggers deterministic boat reroute`() {
        val flooded = graph.withEdgeState("road-direct", EdgeState.FAILED)

        val route = RoutePlanner().findRoute(flooded, "N1", "N4", VehicleType.BOAT)

        assertEquals(listOf("river-sunamganj", "river-company"), route.edgeIds)
        assertEquals(200, route.totalMinutes)
    }

    @Test
    fun `predicted risk adds cost without closing edge`() {
        val risky = graph.withRisk("road-airport", probability = 0.9)

        val route = RoutePlanner(riskPenaltyMinutes = 100)
            .findRoute(risky, "N1", "N4", VehicleType.TRUCK)

        assertEquals(listOf("road-airport", "road-company"), route.edgeIds)
        assertEquals(155, route.totalMinutes)
        assertTrue(route.riskAdjusted)
    }
}
