package com.example.digitaldelta.domain.routing

data class DynamicRouteDecision(
    val route: PlannedRoute,
    val preferredVehicle: VehicleType,
    val routeVehicle: VehicleType,
    val fallbackUsed: Boolean,
    val computationNanos: Long,
    val cause: RouteDecisionCause = if (fallbackUsed) {
        RouteDecisionCause.PREFERRED_UNAVAILABLE
    } else {
        RouteDecisionCause.PREFERRED_ROUTE
    },
)

enum class RouteDecisionCause {
    PREFERRED_ROUTE,
    PREFERRED_UNAVAILABLE,
    PREDICTED_RISK,
}

class DynamicRouteEngine(
    private val planner: RoutePlanner = RoutePlanner(),
    private val fallbackOrder: List<VehicleType> = listOf(VehicleType.BOAT, VehicleType.DRONE),
    private val allowRiskDrivenFallback: Boolean = false,
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
        val routes = candidates.associateWith { vehicle ->
            try {
                planner.findRoute(graph, source, destination, vehicle)
            } catch (_: RouteNotFoundException) {
                null
            }
        }
        val preferredRoute = routes[preferredVehicle]
        val riskFallback = if (allowRiskDrivenFallback && preferredRoute?.riskAdjusted == true) {
            fallbackOrder.asSequence()
                .mapNotNull { vehicle -> routes[vehicle]?.let { vehicle to it } }
                .firstOrNull { (_, route) -> route.totalMinutes < preferredRoute.totalMinutes }
        } else {
            null
        }
        val selected = riskFallback
            ?: preferredRoute?.let { preferredVehicle to it }
            ?: fallbackOrder.asSequence()
                .mapNotNull { vehicle -> routes[vehicle]?.let { vehicle to it } }
                .firstOrNull()
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
            cause = when {
                riskFallback != null -> RouteDecisionCause.PREDICTED_RISK
                result.first != preferredVehicle -> RouteDecisionCause.PREFERRED_UNAVAILABLE
                else -> RouteDecisionCause.PREFERRED_ROUTE
            },
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
    fun applyPredictedRisk(edgeId: String, probability: Double): RouteScenarioSnapshot = snapshot()
    fun clearPredictedRisk(): RouteScenarioSnapshot = reset()
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
    override fun applyPredictedRisk(edgeId: String, probability: Double): RouteScenarioSnapshot {
        require(graph.edges.any { it.id == edgeId }) { "unknown edge: $edgeId" }
        graph = graph.withRisk(edgeId, probability)
        return currentSnapshot()
    }

    @Synchronized
    override fun clearPredictedRisk(): RouteScenarioSnapshot {
        graph = graph.copy(
            edges = graph.edges.map { edge -> edge.copy(riskProbability = 0.0) },
        )
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
