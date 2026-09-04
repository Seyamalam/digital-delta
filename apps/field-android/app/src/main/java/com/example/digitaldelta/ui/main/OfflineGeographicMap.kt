package com.example.digitaldelta.ui.main

import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.digitaldelta.domain.routing.OfflineMapContract
import com.example.digitaldelta.domain.routing.VehicleType
import com.example.digitaldelta.theme.AlertCoral
import com.example.digitaldelta.theme.DeltaTeal
import com.example.digitaldelta.theme.RiskAmber
import com.example.digitaldelta.theme.RiverBlue
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.all
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource

private sealed interface BasemapState {
    data object Loading : BasemapState
    data class Ready(val geoJson: String) : BasemapState
    data class Failed(val reason: String) : BasemapState
}

private class MapController {
    var map: MapLibreMap? = null

    fun updateMission(geoJson: String) {
        map?.style?.getSourceAs<GeoJsonSource>("mission")?.setGeoJson(geoJson)
    }
}

@Composable
internal fun OfflineGeographicMap(
    routeProgress: Float,
    showFailure: Boolean,
    showRisk: Boolean,
    routeVehicle: VehicleType,
    contentDescription: String,
    loadingLabel: String,
    unavailableLabel: String,
    localSourceLabel: String,
    attributionLabel: String,
    fallback: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val basemapState by produceState<BasemapState>(BasemapState.Loading, context) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = context.assets.open(OfflineMapContract.BASEMAP_ASSET).use { it.readBytes() }
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                check(digest == OfflineMapContract.BASEMAP_SHA256) { "offline basemap checksum mismatch" }
                BasemapState.Ready(bytes.toString(Charsets.UTF_8))
            }.getOrElse { BasemapState.Failed(it.message ?: "offline basemap unavailable") }
        }
    }

    when (val state = basemapState) {
        BasemapState.Loading -> MapLoadingState(loadingLabel)
        is BasemapState.Failed -> {
            Box(Modifier.fillMaxSize()) {
                fallback()
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .94f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).testTag("offline-map-fallback"),
                ) {
                    Text(unavailableLabel, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontSize = 11.sp)
                }
            }
        }
        is BasemapState.Ready -> {
            NativeOfflineMap(
                basemapGeoJson = state.geoJson,
                missionGeoJson = OfflineMapContract.missionGeoJson(
                    vehicle = routeVehicle,
                    failedRoad = showFailure,
                    predictedRisk = showRisk,
                    routeProgress = routeProgress,
                ),
                contentDescription = contentDescription,
                localSourceLabel = localSourceLabel,
                attributionLabel = attributionLabel,
                fallback = fallback,
            )
        }
    }
}

@Composable
private fun NativeOfflineMap(
    basemapGeoJson: String,
    missionGeoJson: String,
    contentDescription: String,
    localSourceLabel: String,
    attributionLabel: String,
    fallback: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controller = remember { MapController() }
    var mapReady by remember { mutableStateOf(false) }
    var rendererFailed by remember { mutableStateOf(false) }
    val mapView = remember(context) {
        MapLibre.getInstance(context.applicationContext)
        MapLibre.setConnected(false)
        val options = MapLibreMapOptions.createFromAttributes(context)
            .logoEnabled(false)
            .attributionEnabled(false)
            .compassEnabled(false)
            .rotateGesturesEnabled(false)
            .scrollGesturesEnabled(false)
            .tiltGesturesEnabled(false)
            .zoomGesturesEnabled(false)
            .doubleTapGesturesEnabled(false)
            .quickZoomGesturesEnabled(false)
            .minZoomPreference(6.0)
            .maxZoomPreference(13.0)
            .foregroundLoadColor(android.graphics.Color.rgb(234, 241, 237))
        MapView(context, options).also { view ->
            view.onCreate(Bundle())
            view.addOnDidFailLoadingMapListener { rendererFailed = true }
            view.addOnRenderErrorListener { rendererFailed = true }
            view.getMapAsync { map ->
                controller.map = map
                map.uiSettings.setAllGesturesEnabled(false)
                map.setStyle(Style.Builder().fromJson(OfflineMapContract.baseStyleJson)) { style ->
                    addOfflineLayers(style, basemapGeoJson, missionGeoJson)
                    val missionBounds = LatLngBounds.from(
                        OfflineMapContract.NORTH,
                        OfflineMapContract.EAST,
                        OfflineMapContract.SOUTH,
                        OfflineMapContract.WEST,
                    )
                    map.setLatLngBoundsForCameraTarget(missionBounds)
                    map.moveCamera(CameraUpdateFactory.newLatLngBounds(missionBounds, 36))
                    mapReady = true
                }
            }
        }
    }

    DisposableEffect(mapView, lifecycle) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.map = null
            if (!mapView.isDestroyed) mapView.onDestroy()
        }
    }

    LaunchedEffect(missionGeoJson) {
        controller.updateMission(missionGeoJson)
    }

    Box(Modifier.fillMaxSize().testTag("offline-geographic-map")) {
        if (rendererFailed) {
            fallback()
        } else {
            AndroidView(
                factory = { mapView },
                update = { controller.updateMission(missionGeoJson) },
                modifier = Modifier.fillMaxSize().testTag("maplibre-native-view"),
            )
        }
        AnimatedVisibility(!mapReady && !rendererFailed) {
            MapLoadingState(localSourceLabel)
        }
        if (rendererFailed) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .94f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).testTag("map-renderer-failed"),
            ) {
                Text(contentDescription, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontSize = 11.sp)
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
            shape = RoundedCornerShape(9.dp),
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
        ) {
            Text(localSourceLabel, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), fontSize = 10.sp)
        }
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
            shape = RoundedCornerShape(7.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(7.dp),
        ) {
            Text(attributionLabel, Modifier.padding(horizontal = 6.dp, vertical = 3.dp), fontSize = 9.sp)
        }
    }
}

private fun addOfflineLayers(style: Style, basemapGeoJson: String, missionGeoJson: String) {
    style.addSource(GeoJsonSource("basemap", basemapGeoJson))
    style.addSource(GeoJsonSource("mission", missionGeoJson))
    style.addLayer(
        FillLayer("landuse", "basemap")
            .withFilter(eq(get("source_layer"), literal("landuse")))
            .withProperties(fillColor("#dce8df"), fillOpacity(.70f)),
    )
    style.addLayer(
        FillLayer("water-fill", "basemap")
            .withFilter(eq(get("source_layer"), literal("water")))
            .withProperties(fillColor("#b8ddf3"), fillOpacity(.88f)),
    )
    style.addLayer(
        LineLayer("water-line", "basemap")
            .withFilter(eq(get("source_layer"), literal("water")))
            .withProperties(lineColor("#83bddb"), lineWidth(2.2f), lineOpacity(.9f)),
    )
    style.addLayer(
        LineLayer("boundaries", "basemap")
            .withFilter(eq(get("source_layer"), literal("boundaries")))
            .withProperties(lineColor("#91a39e"), lineWidth(1.0f), lineDasharray(arrayOf(3f, 3f))),
    )
    style.addLayer(
        LineLayer("roads", "basemap")
            .withFilter(eq(get("source_layer"), literal("roads")))
            .withProperties(lineColor("#a7b5b1"), lineWidth(1.8f), lineOpacity(.9f)),
    )
    style.addLayer(
        CircleLayer("places", "basemap")
            .withFilter(eq(get("source_layer"), literal("places")))
            .withProperties(circleColor("#6e807b"), circleRadius(2.4f), circleStrokeColor("#f7f5ef"), circleStrokeWidth(.8f)),
    )
    style.addLayer(
        LineLayer("mission-network", "mission")
            .withFilter(all(eq(get("kind"), literal("route")), eq(get("active"), literal(false))))
            .withProperties(lineColor("#677773"), lineWidth(2.6f), lineOpacity(.28f)),
    )
    style.addLayer(
        LineLayer("mission-active-road", "mission")
            .withFilter(all(eq(get("kind"), literal("route")), eq(get("active"), literal(true)), eq(get("transport"), literal("road"))))
            .withProperties(lineColor("#087681"), lineWidth(6.5f), lineOpacity(.96f)),
    )
    style.addLayer(
        LineLayer("mission-active-water", "mission")
            .withFilter(all(eq(get("kind"), literal("route")), eq(get("active"), literal(true)), eq(get("transport"), literal("water"))))
            .withProperties(lineColor("#2b84ad"), lineWidth(6.5f), lineOpacity(.96f)),
    )
    style.addLayer(
        LineLayer("mission-active-air", "mission")
            .withFilter(all(eq(get("kind"), literal("route")), eq(get("active"), literal(true)), eq(get("transport"), literal("air"))))
            .withProperties(lineColor("#d9912b"), lineWidth(5.5f), lineDasharray(arrayOf(2f, 2f))),
    )
    style.addLayer(
        LineLayer("mission-risk", "mission")
            .withFilter(eq(get("predicted_risk"), literal(true)))
            .withProperties(lineColor("#d9912b"), lineWidth(10f), lineOpacity(.44f), lineDasharray(arrayOf(1f, 1.5f))),
    )
    style.addLayer(
        LineLayer("mission-failed", "mission")
            .withFilter(eq(get("failed"), literal(true)))
            .withProperties(lineColor("#ef5f5c"), lineWidth(8f), lineDasharray(arrayOf(2f, 1.4f))),
    )
    style.addLayer(
        CircleLayer("mission-nodes", "mission")
            .withFilter(eq(get("kind"), literal("node")))
            .withProperties(circleColor("#073940"), circleRadius(5.5f), circleStrokeColor("#f7f5ef"), circleStrokeWidth(2f)),
    )
    style.addLayer(
        CircleLayer("mission-vehicle-halo", "mission")
            .withFilter(eq(get("kind"), literal("vehicle")))
            .withProperties(circleColor("#ffffff"), circleRadius(10f), circleStrokeColor("#087681"), circleStrokeWidth(2.5f)),
    )
    style.addLayer(
        CircleLayer("mission-vehicle", "mission")
            .withFilter(eq(get("kind"), literal("vehicle")))
            .withProperties(circleColor("#087681"), circleRadius(5.2f)),
    )
}

@Composable
private fun MapLoadingState(label: String) {
    val transition = rememberInfiniteTransition(label = "map-wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_100), RepeatMode.Restart),
        label = "map-wave-phase",
    )
    Box(
        Modifier.fillMaxSize().background(Color(0xFFEAF1ED)).testTag("offline-map-loading"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Canvas(Modifier.size(width = 92.dp, height = 30.dp)) {
                repeat(3) { index ->
                    val local = (phase + index / 3f) % 1f
                    val y = size.height * (.5f + kotlin.math.sin(local * Math.PI * 2).toFloat() * .22f)
                    drawCircle(
                        color = listOf(DeltaTeal, RiverBlue, RiskAmber)[index],
                        radius = 6f + 4f * (1f - kotlin.math.abs(local - .5f) * 2f),
                        center = Offset(size.width * (.2f + index * .3f), y),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = DeltaTeal)
                Spacer(Modifier.width(4.dp))
                Text("•", color = AlertCoral)
            }
        }
    }
}
