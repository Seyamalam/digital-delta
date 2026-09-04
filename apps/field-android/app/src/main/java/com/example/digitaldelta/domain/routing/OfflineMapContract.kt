package com.example.digitaldelta.domain.routing

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object OfflineMapContract {
    const val BASEMAP_ASSET = "maps/sylhet_osm_basemap.geojson"
    const val SOURCE_ARCHIVE_SHA256 = "f45649f195b99106b3a851b456c629f0e1efd589093f80b304859ed54e3bdebc"
    const val BASEMAP_SHA256 = "8e0d3ae4e736a15cae5156e65b8ad9d38c3c0717baa9228f84a5888bf4c55707"
    const val ATTRIBUTION = "© OpenStreetMap contributors"

    const val WEST = 91.30
    const val SOUTH = 24.30
    const val EAST = 92.36
    const val NORTH = 25.19

    val baseStyleJson: String = """
        {
          "version": 8,
          "sources": {},
          "layers": [
            {
              "id": "offline-background",
              "type": "background",
              "paint": { "background-color": "#eaf1ed" }
            }
          ]
        }
    """.trimIndent()

    private data class Coordinate(val longitude: Double, val latitude: Double)
    private data class Edge(val id: String, val source: String, val target: String, val transport: String)

    private val nodes = linkedMapOf(
        "N1" to Coordinate(91.8687, 24.8949),
        "N2" to Coordinate(91.8668, 24.9632),
        "N3" to Coordinate(91.4073, 25.0658),
        "N4" to Coordinate(91.7554, 25.0715),
        "N5" to Coordinate(92.2611, 24.9945),
        "N6" to Coordinate(91.4169, 24.3840),
        "N7" to Coordinate(91.6800, 25.1200),
    )

    private val edges = listOf(
        Edge("E1", "N1", "N2", "road"),
        Edge("E2", "N1", "N3", "road"),
        Edge("E3", "N2", "N4", "road"),
        Edge("E4", "N1", "N5", "road"),
        Edge("E5", "N1", "N6", "road"),
        Edge("E6", "N1", "N3", "water"),
        Edge("E7", "N3", "N4", "water"),
        Edge("A1", "N1", "N4", "air"),
        Edge("A2", "N1", "N7", "air"),
    )

    fun missionGeoJson(
        vehicle: VehicleType,
        failedRoad: Boolean,
        predictedRisk: Boolean,
        routeProgress: Float = 1f,
    ): String {
        val activeEdges = when (vehicle) {
            VehicleType.TRUCK -> setOf("E1", "E3")
            VehicleType.BOAT -> setOf("E6", "E7")
            VehicleType.DRONE -> setOf("A2")
        }
        val features = buildList {
            edges.forEach { edge ->
                val source = checkNotNull(nodes[edge.source])
                val target = checkNotNull(nodes[edge.target])
                add(
                    feature(
                        properties = mapOf(
                            "id" to JsonPrimitive(edge.id),
                            "kind" to JsonPrimitive("route"),
                            "transport" to JsonPrimitive(edge.transport),
                            "active" to JsonPrimitive(edge.id in activeEdges),
                            "failed" to JsonPrimitive(failedRoad && edge.id == "E3"),
                            "predicted_risk" to JsonPrimitive(predictedRisk && edge.id == "E3"),
                            "simulated" to JsonPrimitive(edge.transport == "air"),
                        ),
                        geometryType = "LineString",
                        coordinates = JsonArray(listOf(coordinate(source), coordinate(target))),
                    ),
                )
            }
            nodes.forEach { (id, value) ->
                add(
                    feature(
                        properties = mapOf(
                            "id" to JsonPrimitive(id),
                            "kind" to JsonPrimitive("node"),
                            "simulated" to JsonPrimitive(false),
                        ),
                        geometryType = "Point",
                        coordinates = coordinate(value),
                    ),
                )
            }
            val routeNodes = when (vehicle) {
                VehicleType.TRUCK -> listOf("N1", "N2", "N4")
                VehicleType.BOAT -> listOf("N1", "N3", "N4")
                VehicleType.DRONE -> listOf("N1", "N7")
            }.map { checkNotNull(nodes[it]) }
            add(
                feature(
                    properties = mapOf(
                        "id" to JsonPrimitive("active-vehicle"),
                        "kind" to JsonPrimitive("vehicle"),
                        "transport" to JsonPrimitive(vehicle.name.lowercase()),
                        "simulated" to JsonPrimitive(vehicle == VehicleType.DRONE),
                    ),
                    geometryType = "Point",
                    coordinates = coordinate(interpolate(routeNodes, routeProgress)),
                ),
            )
        }
        return JsonObject(
            mapOf(
                "type" to JsonPrimitive("FeatureCollection"),
                "features" to JsonArray(features),
            ),
        ).toString()
    }

    private fun feature(
        properties: Map<String, JsonPrimitive>,
        geometryType: String,
        coordinates: kotlinx.serialization.json.JsonElement,
    ) = JsonObject(
        mapOf(
            "type" to JsonPrimitive("Feature"),
            "properties" to JsonObject(properties),
            "geometry" to JsonObject(
                mapOf(
                    "type" to JsonPrimitive(geometryType),
                    "coordinates" to coordinates,
                ),
            ),
        ),
    )

    private fun coordinate(value: Coordinate) = JsonArray(
        listOf(JsonPrimitive(value.longitude), JsonPrimitive(value.latitude)),
    )

    private fun interpolate(route: List<Coordinate>, progress: Float): Coordinate {
        val bounded = progress.coerceIn(0f, 1f)
        val scaled = bounded * (route.size - 1)
        val startIndex = scaled.toInt().coerceAtMost(route.lastIndex - 1)
        val segmentProgress = if (bounded == 1f) 1.0 else (scaled - startIndex).toDouble()
        val start = route[startIndex]
        val end = route[startIndex + 1]
        return Coordinate(
            longitude = start.longitude + (end.longitude - start.longitude) * segmentProgress,
            latitude = start.latitude + (end.latitude - start.latitude) * segmentProgress,
        )
    }
}
