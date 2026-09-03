package com.example.digitaldelta.domain.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteScenarioTest {
    private val fixture = """
        {
          "metadata": {"region":"Sylhet Division","scenario":"Flash Flood Delta","last_updated":"2026-04-12T08:00:00Z"},
          "nodes": [
            {"id":"N1","name":"Sylhet City Hub","type":"central_command","lat":24.8949,"lng":91.8687},
            {"id":"N2","name":"Osmani Airport Node","type":"supply_drop","lat":24.9632,"lng":91.8668},
            {"id":"N3","name":"Sunamganj Sadar Camp","type":"relief_camp","lat":25.0658,"lng":91.4073},
            {"id":"N4","name":"Companyganj Outpost","type":"relief_camp","lat":25.0715,"lng":91.7554}
          ],
          "edges": [
            {"id":"E1","source":"N1","target":"N2","type":"road","base_weight_mins":20,"is_flooded":false},
            {"id":"E3","source":"N2","target":"N4","type":"road","base_weight_mins":45,"is_flooded":false},
            {"id":"E6","source":"N1","target":"N3","type":"river","base_weight_mins":150,"is_flooded":false},
            {"id":"E7","source":"N3","target":"N4","type":"river","base_weight_mins":50,"is_flooded":false},
            {"id":"A1","source":"N1","target":"N4","type":"airway","base_weight_mins":30,"is_flooded":false,"simulated":true}
          ]
        }
    """.trimIndent()

    @Test
    fun `fixture parser validates references and normalizes river edges`() {
        val parsed = SylhetMapParser().parse(fixture)

        assertEquals("Sylhet Division", parsed.metadata.region)
        assertEquals(EdgeMode.WATERWAY, parsed.graph.edges.first { it.id == "E6" }.mode)
        assertEquals(EdgeMode.AIRWAY, parsed.graph.edges.first { it.id == "A1" }.mode)
        assertTrue(parsed.graph.edges.first { it.id == "A1" }.simulated)
    }

    @Test
    fun `failed preferred road recomputes to policy ordered boat route with measured latency`() {
        val graph = SylhetMapParser().parse(fixture).graph
        val ticks = ArrayDeque(listOf(1_000_000L, 1_340_000L, 2_000_000L, 2_870_000L))
        val engine = DynamicRouteEngine(nanoTime = { ticks.removeFirst() })

        val initial = engine.recompute(graph, "N1", "N4", VehicleType.TRUCK)
        val rerouted = engine.recompute(
            graph.withEdgeState("E3", EdgeState.FAILED),
            "N1",
            "N4",
            VehicleType.TRUCK,
        )

        assertEquals(VehicleType.TRUCK, initial.routeVehicle)
        assertEquals(listOf("E1", "E3"), initial.route.edgeIds)
        assertFalse(initial.fallbackUsed)
        assertEquals(340_000L, initial.computationNanos)

        assertEquals(VehicleType.BOAT, rerouted.routeVehicle)
        assertEquals(listOf("E6", "E7"), rerouted.route.edgeIds)
        assertEquals(200, rerouted.route.totalMinutes)
        assertTrue(rerouted.fallbackUsed)
        assertEquals(870_000L, rerouted.computationNanos)
    }

    @Test
    fun `route scenario can fail and reset without internet or mutable global data`() {
        val scenario = OfflineRouteScenario(SylhetMapParser().parse(fixture).graph)

        assertEquals(VehicleType.TRUCK, scenario.snapshot().decision.routeVehicle)
        assertEquals(VehicleType.BOAT, scenario.triggerEdgeFailure("E3").decision.routeVehicle)
        assertEquals(setOf("E3"), scenario.snapshot().failedEdgeIds)
        assertEquals(VehicleType.TRUCK, scenario.reset().decision.routeVehicle)
        assertTrue(scenario.snapshot().failedEdgeIds.isEmpty())
    }
}
