package com.example.digitaldelta.domain.routing

data class DynamicRouteDecision(
    val route: PlannedRoute,
    val preferredVehicle: VehicleType,
    val routeVehicle: VehicleType,
    val fallbackUsed: Boolean,
    val computationNanos: Long,
)

class DynamicRouteEngine(
    private val planner: RoutePlanner = RoutePlanner(),
    private val fallbackOrder: List<VehicleType> = listOf(VehicleType.BOAT, VehicleType.DRONE),
    private val nanoTime: () -> Long = System::nanoTime,
) {
    fun recompute(
        graph: TransportGraph,
        source: String,
        destination: String,
        preferredVehicle: VehicleType,
    ): DynamicRouteDecision {
        val startedAt = nanoTime()
        val candidates = (listOf(preferredVehicle) + fallbackOrder).distinct()
        var selected: Pair<VehicleType, PlannedRoute>? = null
        candidates.forEach { vehicle ->
            if (selected == null) {
                val route = try {
                    planner.findRoute(graph, source, destination, vehicle)
                } catch (_: RouteNotFoundException) {
                    null
                }
                if (route != null) selected = vehicle to route
            }
        }
        val result = selected ?: throw RouteNotFoundException(
            "No supported route from $source to $destination for ${candidates.joinToString { it.name }}",
        )
        val finishedAt = nanoTime()
        return DynamicRouteDecision(
            route = result.second,
            preferredVehicle = preferredVehicle,
            routeVehicle = result.first,
            fallbackUsed = result.first != preferredVehicle,
            computationNanos = (finishedAt - startedAt).coerceAtLeast(0),
        )
    }
}

data class RouteScenarioSnapshot(
    val failedEdgeIds: Set<String>,
    val decision: DynamicRouteDecision,
)

interface RouteScenario {
    fun snapshot(): RouteScenarioSnapshot
    fun triggerEdgeFailure(edgeId: String): RouteScenarioSnapshot
    fun reset(): RouteScenarioSnapshot
}

class OfflineRouteScenario(
    private val initialGraph: TransportGraph,
    private val source: String = "N1",
    private val destination: String = "N4",
    private val preferredVehicle: VehicleType = VehicleType.TRUCK,
    private val engine: DynamicRouteEngine = DynamicRouteEngine(),
) : RouteScenario {
    private var graph = initialGraph

    @Synchronized
    override fun snapshot(): RouteScenarioSnapshot = currentSnapshot()

    @Synchronized
    override fun triggerEdgeFailure(edgeId: String): RouteScenarioSnapshot {
        require(graph.edges.any { it.id == edgeId }) { "unknown edge: $edgeId" }
        graph = graph.withEdgeState(edgeId, EdgeState.FAILED)
        return currentSnapshot()
    }

    @Synchronized
    override fun reset(): RouteScenarioSnapshot {
        graph = initialGraph
        return currentSnapshot()
    }

    private fun currentSnapshot(): RouteScenarioSnapshot = RouteScenarioSnapshot(
        failedEdgeIds = graph.edges.filter { it.state == EdgeState.FAILED }.mapTo(sortedSetOf(), MapEdge::id),
        decision = engine.recompute(graph, source, destination, preferredVehicle),
    )
}
