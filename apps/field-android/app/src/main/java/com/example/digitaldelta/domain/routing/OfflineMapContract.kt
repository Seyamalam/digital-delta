package com.example.digitaldelta.domain.routing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.cos
import kotlin.math.hypot

object OfflineMapContract {
    const val BASEMAP_ASSET = "maps/sylhet_osm_basemap.geojson"
    const val ROUTE_GEOMETRY_ASSET = "sylhet_route_geometry.json"
    const val SOURCE_ARCHIVE_SHA256 = "f45649f195b99106b3a851b456c629f0e1efd589093f80b304859ed54e3bdebc"
    const val BASEMAP_SHA256 = "8e0d3ae4e736a15cae5156e65b8ad9d38c3c0717baa9228f84a5888bf4c55707"
    const val ROUTE_GEOMETRY_SHA256 = "3bf94d9302ea97a9b9fc93df04911ede77652240f366d124c5ef725584418e75"
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
    private data class Edge(
        val id: String,
        val source: String,
        val target: String,
        val transport: String,
        val simulated: Boolean,
        val geometrySource: String,
        val coordinates: List<Coordinate>,
    )

    private val nodes = linkedMapOf(
        "N1" to Coordinate(91.8687, 24.8949),
        "N2" to Coordinate(91.8668, 24.9632),
        "N3" to Coordinate(91.4073, 25.0658),
        "N4" to Coordinate(91.7554, 25.0715),
        "N5" to Coordinate(92.2611, 24.9945),
        "N6" to Coordinate(91.4169, 24.3840),
        "N7" to Coordinate(91.6800, 25.1200),
    )

    fun missionGeoJson(
        vehicle: VehicleType,
        failedRoad: Boolean,
        predictedRisk: Boolean,
        routeGeometryGeoJson: String,
        routeProgress: Float = 1f,
    ): String {
        val edges = parseEdges(routeGeometryGeoJson)
        val activeEdgeIds = when (vehicle) {
            VehicleType.TRUCK -> listOf("E1", "E3")
            VehicleType.BOAT -> listOf("E6", "E7")
            VehicleType.DRONE -> listOf("A2")
        }
        val activeEdges = activeEdgeIds.toSet()
        val features = buildList {
            edges.forEach { edge ->
                add(
                    feature(
                        properties = mapOf(
                            "id" to JsonPrimitive(edge.id),
                            "kind" to JsonPrimitive("route"),
                            "transport" to JsonPrimitive(edge.transport),
                            "active" to JsonPrimitive(edge.id in activeEdges),
                            "failed" to JsonPrimitive(failedRoad && edge.id == "E3"),
                            "predicted_risk" to JsonPrimitive(predictedRisk && edge.id == "E3"),
                            "simulated" to JsonPrimitive(edge.simulated),
                            "geometry_source" to JsonPrimitive(edge.geometrySource),
                        ),
                        geometryType = "LineString",
                        coordinates = JsonArray(edge.coordinates.map(::coordinate)),
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
            val routeLine = activeEdgeIds.flatMapIndexed { index, edgeId ->
                val coordinates = checkNotNull(edges.find { it.id == edgeId }) { "Missing route geometry for $edgeId" }.coordinates
                if (index == 0) coordinates else coordinates.drop(1)
            }
            add(
                feature(
                    properties = mapOf(
                        "id" to JsonPrimitive("active-vehicle"),
                        "kind" to JsonPrimitive("vehicle"),
                        "transport" to JsonPrimitive(vehicle.name.lowercase()),
                        "simulated" to JsonPrimitive(vehicle == VehicleType.DRONE),
                    ),
                    geometryType = "Point",
                    coordinates = coordinate(interpolate(routeLine, routeProgress)),
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
        val segmentLengths = route.zipWithNext(::distanceMetres)
        val target = segmentLengths.sum() * bounded
        var travelled = 0.0
        segmentLengths.forEachIndexed { index, length ->
            if (travelled + length >= target) {
                val amount = if (length == 0.0) 0.0 else (target - travelled) / length
                val start = route[index]
                val end = route[index + 1]
                return Coordinate(
                    longitude = start.longitude + (end.longitude - start.longitude) * amount,
                    latitude = start.latitude + (end.latitude - start.latitude) * amount,
                )
            }
            travelled += length
        }
        return route.last()
    }

    private fun parseEdges(routeGeometryGeoJson: String): List<Edge> {
        val root = Json.parseToJsonElement(routeGeometryGeoJson).jsonObject
        return checkNotNull(root["features"]).jsonArray.map { element ->
            val feature = element.jsonObject
            val properties = checkNotNull(feature["properties"]).jsonObject
            val coordinates = checkNotNull(feature["geometry"])
                .jsonObject
                .getValue("coordinates")
                .jsonArray
                .map { point ->
                    val pair = point.jsonArray
                    Coordinate(pair[0].jsonPrimitive.content.toDouble(), pair[1].jsonPrimitive.content.toDouble())
                }
            Edge(
                id = properties.getValue("id").jsonPrimitive.content,
                source = properties.getValue("source").jsonPrimitive.content,
                target = properties.getValue("target").jsonPrimitive.content,
                transport = properties.getValue("transport").jsonPrimitive.content,
                simulated = properties.getValue("simulated").jsonPrimitive.boolean,
                geometrySource = properties.getValue("geometry_source").jsonPrimitive.content,
                coordinates = coordinates,
            )
        }.also { edges ->
            require(edges.map(Edge::id).toSet().size == edges.size) { "Route geometry IDs must be unique" }
            edges.forEach { edge ->
                require(edge.coordinates.size >= 2) { "Route geometry ${edge.id} must have at least two points" }
                require(nodes.containsKey(edge.source) && nodes.containsKey(edge.target)) { "Route geometry ${edge.id} references an unknown node" }
            }
        }
    }

    private fun distanceMetres(left: Coordinate, right: Coordinate): Double {
        val latitude = (left.latitude + right.latitude) * Math.PI / 360.0
        val dx = (left.longitude - right.longitude) * 111_320.0 * cos(latitude)
        val dy = (left.latitude - right.latitude) * 110_540.0
        return hypot(dx, dy)
    }
}
