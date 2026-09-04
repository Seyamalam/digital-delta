package com.example.digitaldelta.domain.routing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class SylhetMapAssetTest {
    @Test
    fun bundledScenarioIsOfflineCompleteAndVehicleConstrained() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixture = context.assets.open("sylhet_map.json").bufferedReader().use { it.readText() }

        val scenario = SylhetMapParser().parse(fixture)

        assertEquals(7, scenario.graph.nodes.size)
        assertEquals(9, scenario.graph.edges.size)
        assertTrue(scenario.graph.edges.any { it.mode == EdgeMode.ROAD })
        assertTrue(scenario.graph.edges.any { it.mode == EdgeMode.WATERWAY })
        assertTrue(scenario.graph.edges.any { it.mode == EdgeMode.AIRWAY && it.simulated })
        assertEquals(
            listOf("E1", "E3"),
            RoutePlanner().findRoute(scenario.graph, "N1", "N4", VehicleType.TRUCK).edgeIds,
        )
        assertEquals(
            listOf("A2"),
            RoutePlanner().findRoute(scenario.graph, "N1", "N7", VehicleType.DRONE).edgeIds,
        )
        assertEquals(
            com.example.digitaldelta.domain.fleet.Reachability.DRONE_REQUIRED,
            com.example.digitaldelta.domain.fleet.FleetOrchestrator().classifyReachability(
                scenario.graph,
                "N1",
                "N7",
            ),
        )
    }

    @Test
    fun bundledGeographicBasemapIsPinnedOsmDerivedAndCoversMissionBounds() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixture = context.assets.open(OfflineMapContract.BASEMAP_ASSET)
            .bufferedReader()
            .use { it.readText() }
        val root = JSONObject(fixture)
        val metadata = root.getJSONObject("metadata")
        val bounds = metadata.getJSONArray("bounds")

        assertEquals("FeatureCollection", root.getString("type"))
        assertTrue(root.getJSONArray("features").length() >= 1_500)
        assertTrue(metadata.getString("source").contains("OpenStreetMap"))
        assertEquals(OfflineMapContract.SOURCE_ARCHIVE_SHA256, metadata.getString("archive_sha256"))
        assertTrue(bounds.getDouble(0) <= 91.4073)
        assertTrue(bounds.getDouble(1) <= 24.3840)
        assertTrue(bounds.getDouble(2) >= 92.2611)
        assertTrue(bounds.getDouble(3) >= 25.1200)
    }
}
