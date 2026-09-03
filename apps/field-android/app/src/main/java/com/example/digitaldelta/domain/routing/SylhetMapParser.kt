package com.example.digitaldelta.domain.routing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class ScenarioMetadata(
    val region: String,
    val scenario: String,
    val lastUpdated: String,
)

data class ParsedRouteScenario(
    val metadata: ScenarioMetadata,
    val graph: TransportGraph,
)

class SylhetMapParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun parse(value: String): ParsedRouteScenario {
        val fixture = json.decodeFromString<SylhetMapFixture>(value)
        require(fixture.metadata.region.isNotBlank()) { "scenario region is required" }
        require(fixture.nodes.isNotEmpty()) { "scenario requires at least one node" }
        require(fixture.edges.isNotEmpty()) { "scenario requires at least one edge" }

        return ParsedRouteScenario(
            metadata = ScenarioMetadata(
                region = fixture.metadata.region,
                scenario = fixture.metadata.scenario,
                lastUpdated = fixture.metadata.lastUpdated,
            ),
            graph = TransportGraph(
                nodes = fixture.nodes.map { node ->
                    require(node.type.isNotBlank()) { "node ${node.id} type is required" }
                    MapNode(node.id, node.name, node.latitude, node.longitude)
                },
                edges = fixture.edges.map { edge ->
                    require(edge.baseMinutes > 0) { "edge ${edge.id} travel time must be positive" }
                    MapEdge(
                        id = edge.id,
                        source = edge.source,
                        target = edge.target,
                        mode = edge.type.toEdgeMode(),
                        baseMinutes = edge.baseMinutes,
                        state = if (edge.isFlooded) EdgeState.FAILED else EdgeState.OPEN,
                        simulated = edge.simulated,
                    )
                },
            ),
        )
    }

    private fun String.toEdgeMode(): EdgeMode = when (lowercase()) {
        "road" -> EdgeMode.ROAD
        "river", "waterway" -> EdgeMode.WATERWAY
        "air", "airway" -> EdgeMode.AIRWAY
        else -> error("unsupported edge type: $this")
    }
}

@Serializable
private data class SylhetMapFixture(
    val metadata: MetadataFixture,
    val nodes: List<NodeFixture>,
    val edges: List<EdgeFixture>,
)

@Serializable
private data class MetadataFixture(
    val region: String,
    val scenario: String,
    @SerialName("last_updated") val lastUpdated: String,
)

@Serializable
private data class NodeFixture(
    val id: String,
    val name: String,
    val type: String,
    @SerialName("lat") val latitude: Double,
    @SerialName("lng") val longitude: Double,
)

@Serializable
private data class EdgeFixture(
    val id: String,
    val source: String,
    val target: String,
    val type: String,
    @SerialName("base_weight_mins") val baseMinutes: Int,
    @SerialName("is_flooded") val isFlooded: Boolean = false,
    val simulated: Boolean = false,
)
