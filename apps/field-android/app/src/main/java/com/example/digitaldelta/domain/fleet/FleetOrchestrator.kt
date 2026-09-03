package com.example.digitaldelta.domain.fleet

import com.example.digitaldelta.domain.routing.RouteNotFoundException
import com.example.digitaldelta.domain.routing.RoutePlanner
import com.example.digitaldelta.domain.routing.TransportGraph
import com.example.digitaldelta.domain.routing.VehicleType
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class Reachability {
    GROUND_OR_WATER,
    DRONE_REQUIRED,
    UNREACHABLE,
}

data class GeoPoint(val latitude: Double, val longitude: Double)

data class NamedPoint(val id: String, val coordinate: GeoPoint)

data class RendezvousPlan(
    val point: NamedPoint,
    val boatArrivalMinutes: Double,
    val droneArrivalMinutes: Double,
    val maxArrivalMinutes: Double,
    val objective: String = "minimize-latest-arrival",
    val simulated: Boolean = true,
)

class FleetOrchestrator(
    private val routePlanner: RoutePlanner = RoutePlanner(),
) {
    fun classifyReachability(
        graph: TransportGraph,
        origin: String,
        destination: String,
    ): Reachability {
        val groundOrWater = listOf(VehicleType.TRUCK, VehicleType.BOAT).any { vehicle ->
            hasRoute(graph, origin, destination, vehicle)
        }
        if (groundOrWater) return Reachability.GROUND_OR_WATER
        return if (hasRoute(graph, origin, destination, VehicleType.DRONE)) {
            Reachability.DRONE_REQUIRED
        } else {
            Reachability.UNREACHABLE
        }
    }

    fun computeRendezvous(
        boatPosition: GeoPoint,
        droneBase: GeoPoint,
        candidates: List<NamedPoint>,
        boatSpeedKph: Double,
        droneSpeedKph: Double,
    ): RendezvousPlan {
        require(candidates.isNotEmpty())
        require(boatSpeedKph > 0.0)
        require(droneSpeedKph > 0.0)

        return candidates.map { candidate ->
            val boatMinutes = travelMinutes(boatPosition, candidate.coordinate, boatSpeedKph)
            val droneMinutes = travelMinutes(droneBase, candidate.coordinate, droneSpeedKph)
            RendezvousPlan(
                point = candidate,
                boatArrivalMinutes = boatMinutes,
                droneArrivalMinutes = droneMinutes,
                maxArrivalMinutes = max(boatMinutes, droneMinutes),
            )
        }.minWith(compareBy(RendezvousPlan::maxArrivalMinutes, { it.point.id }))
    }

    private fun hasRoute(
        graph: TransportGraph,
        origin: String,
        destination: String,
        vehicle: VehicleType,
    ): Boolean = try {
        routePlanner.findRoute(graph, origin, destination, vehicle)
        true
    } catch (_: RouteNotFoundException) {
        false
    }

    private fun travelMinutes(from: GeoPoint, to: GeoPoint, speedKph: Double): Double =
        haversineKilometers(from, to) / speedKph * 60.0

    private fun haversineKilometers(first: GeoPoint, second: GeoPoint): Double {
        val earthRadiusKm = 6_371.0
        val lat1 = Math.toRadians(first.latitude)
        val lat2 = Math.toRadians(second.latitude)
        val deltaLat = Math.toRadians(second.latitude - first.latitude)
        val deltaLon = Math.toRadians(second.longitude - first.longitude)
        val a = sin(deltaLat / 2).pow(2) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2).pow(2)
        return 2 * earthRadiusKm * asin(sqrt(a))
    }
}
