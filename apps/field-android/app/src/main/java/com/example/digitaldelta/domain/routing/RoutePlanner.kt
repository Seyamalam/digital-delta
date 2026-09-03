package com.example.digitaldelta.domain.routing

import java.util.PriorityQueue
import kotlin.math.roundToInt

enum class EdgeMode { ROAD, WATERWAY, AIRWAY }

enum class EdgeState { OPEN, FAILED }

enum class VehicleType(val supportedMode: EdgeMode) {
    TRUCK(EdgeMode.ROAD),
    BOAT(EdgeMode.WATERWAY),
    DRONE(EdgeMode.AIRWAY),
}

data class MapNode(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

data class MapEdge(
    val id: String,
    val source: String,
    val target: String,
    val mode: EdgeMode,
    val baseMinutes: Int,
    val state: EdgeState = EdgeState.OPEN,
    val riskProbability: Double = 0.0,
    val simulated: Boolean = false,
) {
    init {
        require(baseMinutes >= 0) { "Edge travel time cannot be negative" }
        require(riskProbability in 0.0..1.0) { "Risk probability must be between 0 and 1" }
    }
}

data class TransportGraph(
    val nodes: List<MapNode>,
    val edges: List<MapEdge>,
) {
    private val nodeIds = nodes.map(MapNode::id).toSet()

    init {
        require(nodeIds.size == nodes.size) { "Node IDs must be unique" }
        require(edges.map(MapEdge::id).toSet().size == edges.size) { "Edge IDs must be unique" }
        require(edges.all { it.source in nodeIds && it.target in nodeIds }) {
            "Every edge must reference an existing node"
        }
    }

    fun withEdgeState(edgeId: String, state: EdgeState): TransportGraph =
        copy(edges = edges.map { edge -> if (edge.id == edgeId) edge.copy(state = state) else edge })

    fun withRisk(edgeId: String, probability: Double): TransportGraph =
        copy(edges = edges.map { edge ->
            if (edge.id == edgeId) edge.copy(riskProbability = probability) else edge
        })
}

data class PlannedRoute(
    val nodeIds: List<String>,
    val edgeIds: List<String>,
    val totalMinutes: Int,
    val riskAdjusted: Boolean,
    val explanation: String,
)

class RouteNotFoundException(message: String) : IllegalStateException(message)

class RoutePlanner(
    private val riskPenaltyMinutes: Int = 60,
) {
    init {
        require(riskPenaltyMinutes >= 0)
    }

    fun findRoute(
        graph: TransportGraph,
        source: String,
        destination: String,
        vehicle: VehicleType,
    ): PlannedRoute {
        require(graph.nodes.any { it.id == source }) { "Unknown source node: $source" }
        require(graph.nodes.any { it.id == destination }) { "Unknown destination node: $destination" }

        val eligible = graph.edges
            .asSequence()
            .filter { it.state == EdgeState.OPEN }
            .filter { it.mode == vehicle.supportedMode }
            .groupBy(MapEdge::source)

        val distances = mutableMapOf(source to 0)
        val previous = mutableMapOf<String, MapEdge>()
        val pending = PriorityQueue(compareBy<NodeCost>({ it.cost }, { it.nodeId }))
        pending += NodeCost(source, 0)

        while (pending.isNotEmpty()) {
            val current = pending.remove()
            if (current.cost != distances[current.nodeId]) continue
            if (current.nodeId == destination) break

            eligible[current.nodeId]
                .orEmpty()
                .sortedBy(MapEdge::id)
                .forEach { edge ->
                    val edgeCost = edge.baseMinutes +
                        (edge.riskProbability * riskPenaltyMinutes).roundToInt()
                    val candidate = current.cost + edgeCost
                    val known = distances[edge.target]
                    val shouldReplace = known == null || candidate < known ||
                        (candidate == known && edge.id < (previous[edge.target]?.id ?: "\uFFFF"))
                    if (shouldReplace) {
                        distances[edge.target] = candidate
                        previous[edge.target] = edge
                        pending += NodeCost(edge.target, candidate)
                    }
                }
        }

        val total = distances[destination]
            ?: throw RouteNotFoundException("No ${vehicle.name.lowercase()} route from $source to $destination")
        val reversedEdges = buildList {
            var cursor = destination
            while (cursor != source) {
                val edge = previous[cursor]
                    ?: throw RouteNotFoundException("Incomplete route from $source to $destination")
                add(edge)
                cursor = edge.source
            }
        }
        val routeEdges = reversedEdges.asReversed()

        return PlannedRoute(
            nodeIds = listOf(source) + routeEdges.map(MapEdge::target),
            edgeIds = routeEdges.map(MapEdge::id),
            totalMinutes = total,
            riskAdjusted = routeEdges.any { it.riskProbability > 0.0 },
            explanation = "${vehicle.name} uses ${vehicle.supportedMode.name.lowercase()} edges; " +
                if (routeEdges.any { it.riskProbability > 0.0 }) "predicted risk penalty applied" else "lowest travel time selected",
        )
    }

    private data class NodeCost(val nodeId: String, val cost: Int)
}
