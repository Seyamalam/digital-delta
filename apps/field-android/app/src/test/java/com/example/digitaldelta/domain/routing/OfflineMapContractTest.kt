package com.example.digitaldelta.domain.routing

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class OfflineMapContractTest {
    @Test
    fun `base style cannot request internet resources`() {
        val style = OfflineMapContract.baseStyleJson

        assertThat(style).doesNotContain("http://")
        assertThat(style).doesNotContain("https://")
        assertThat(style).doesNotContain("glyphs")
        assertThat(style).doesNotContain("sprite")
        assertThat(style).contains("offline-background")
    }

    @Test
    fun `mission overlay uses geographic Sylhet coordinates and explicit simulation flags`() {
        val overlay = OfflineMapContract.missionGeoJson(
            vehicle = VehicleType.BOAT,
            failedRoad = true,
            predictedRisk = true,
            routeGeometryGeoJson = routeGeometryFixture,
        )

        assertThat(overlay).contains("91.8687")
        assertThat(overlay).contains("24.8949")
        assertThat(overlay).contains("\"transport\":\"water\"")
        assertThat(overlay).contains("\"failed\":true")
        assertThat(overlay).contains("\"predicted_risk\":true")
        assertThat(overlay).contains("\"simulated\":true")
        assertThat(overlay).contains("openstreetmap-offline-waterway")

        val features = Json.parseToJsonElement(overlay).jsonObject.getValue("features").jsonArray
        val waterRoute = features.first {
            it.jsonObject.getValue("properties").jsonObject.getValue("id").jsonPrimitive.content == "E6"
        }
        assertThat(
            waterRoute.jsonObject.getValue("geometry").jsonObject.getValue("coordinates").jsonArray.size,
        ).isGreaterThan(2)
    }

    private val routeGeometryFixture = """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "properties": {"id":"E3","source":"N2","target":"N4","transport":"road","simulated":false,"geometry_source":"openstreetmap-osrm"},
              "geometry": {"type":"LineString","coordinates":[[91.8668,24.9632],[91.82,25.01],[91.7554,25.0715]]}
            },
            {
              "type": "Feature",
              "properties": {"id":"E6","source":"N1","target":"N3","transport":"water","simulated":false,"geometry_source":"openstreetmap-offline-waterway"},
              "geometry": {"type":"LineString","coordinates":[[91.8687,24.8949],[91.7,25.0],[91.4073,25.0658]]}
            },
            {
              "type": "Feature",
              "properties": {"id":"E7","source":"N3","target":"N4","transport":"water","simulated":false,"geometry_source":"openstreetmap-offline-waterway"},
              "geometry": {"type":"LineString","coordinates":[[91.4073,25.0658],[91.6,25.08],[91.7554,25.0715]]}
            },
            {
              "type": "Feature",
              "properties": {"id":"A2","source":"N1","target":"N7","transport":"air","simulated":true,"geometry_source":"simulated-direct-airway"},
              "geometry": {"type":"LineString","coordinates":[[91.8687,24.8949],[91.68,25.12]]}
            }
          ]
        }
    """.trimIndent()
}
