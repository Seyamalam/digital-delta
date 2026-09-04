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
    val droneLastMileMinutes: Double,
    val deliveryArrivalMinutes: Double,
    val droneMissionDistanceKm: Double,
    val projectedDroneBatteryPercent: Double,
    val boatStartDelayMinutes: Double = 0.0,
    val objective: String = "minimize-delivery-completion-with-reserve",
    val simulated: Boolean = true,
)

data class HybridFleetInputs(
    val boatPosition: GeoPoint,
    val droneBase: GeoPoint,
    val droneDestination: GeoPoint,
    val candidates: List<NamedPoint>,
    val boatSpeedKph: Double,
    val droneSpeedKph: Double,
    val droneBatteryPercent: Int,
    val droneRangeAtFullChargeKm: Double,
    val reserveBatteryPercent: Int,
    val boatStartDelayMinutes: Double = 0.0,
)

class NoFeasibleRendezvousException(message: String) : IllegalStateException(message)

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
        droneDestination: GeoPoint,
        candidates: List<NamedPoint>,
        boatSpeedKph: Double,
        droneSpeedKph: Double,
        droneBatteryPercent: Int,
        droneRangeAtFullChargeKm: Double,
        reserveBatteryPercent: Int,
    ): RendezvousPlan = computeRendezvous(
        HybridFleetInputs(
            boatPosition = boatPosition,
            droneBase = droneBase,
            droneDestination = droneDestination,
            candidates = candidates,
            boatSpeedKph = boatSpeedKph,
            droneSpeedKph = droneSpeedKph,
            droneBatteryPercent = droneBatteryPercent,
            droneRangeAtFullChargeKm = droneRangeAtFullChargeKm,
            reserveBatteryPercent = reserveBatteryPercent,
        ),
    )

    fun computeRendezvous(inputs: HybridFleetInputs): RendezvousPlan {
        require(inputs.candidates.isNotEmpty())
        require(inputs.boatSpeedKph > 0.0)
        require(inputs.droneSpeedKph > 0.0)
        require(inputs.droneBatteryPercent in 0..100)
        require(inputs.reserveBatteryPercent in 0..100)
        require(inputs.droneRangeAtFullChargeKm > 0.0)
        require(inputs.reserveBatteryPercent <= inputs.droneBatteryPercent)
        require(inputs.boatStartDelayMinutes >= 0.0)

        return inputs.candidates.mapNotNull { candidate ->
            val boatMinutes = inputs.boatStartDelayMinutes + travelMinutes(
                inputs.boatPosition,
                candidate.coordinate,
                inputs.boatSpeedKph,
            )
            val droneToHandoffKm = haversineKilometers(inputs.droneBase, candidate.coordinate)
            val droneLastMileKm = haversineKilometers(candidate.coordinate, inputs.droneDestination)
            val missionDistanceKm = droneToHandoffKm + droneLastMileKm
            val projectedBattery = inputs.droneBatteryPercent -
                (missionDistanceKm / inputs.droneRangeAtFullChargeKm * 100.0)
            if (projectedBattery < inputs.reserveBatteryPercent) return@mapNotNull null
            val droneMinutes = droneToHandoffKm / inputs.droneSpeedKph * 60.0
            val handoffReadyMinutes = max(boatMinutes, droneMinutes)
            val lastMileMinutes = droneLastMileKm / inputs.droneSpeedKph * 60.0
            RendezvousPlan(
                point = candidate,
                boatArrivalMinutes = boatMinutes,
                droneArrivalMinutes = droneMinutes,
                maxArrivalMinutes = handoffReadyMinutes,
                droneLastMileMinutes = lastMileMinutes,
                deliveryArrivalMinutes = handoffReadyMinutes + lastMileMinutes,
                droneMissionDistanceKm = missionDistanceKm,
                projectedDroneBatteryPercent = projectedBattery,
                boatStartDelayMinutes = inputs.boatStartDelayMinutes,
            )
        }.minWithOrNull(
            compareBy(
                RendezvousPlan::deliveryArrivalMinutes,
                RendezvousPlan::maxArrivalMinutes,
                { it.point.id },
            ),
        ) ?: throw NoFeasibleRendezvousException(
            "no rendezvous preserves the ${inputs.reserveBatteryPercent}% drone reserve",
        )
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
