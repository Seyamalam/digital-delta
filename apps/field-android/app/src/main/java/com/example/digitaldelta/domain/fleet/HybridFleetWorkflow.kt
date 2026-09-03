package com.example.digitaldelta.domain.fleet

import com.example.digitaldelta.domain.pod.CustodyReceiptRecord
import com.example.digitaldelta.domain.pod.DeliveryOfferReady
import com.example.digitaldelta.domain.pod.DeliveryReceiptResult
import com.example.digitaldelta.domain.pod.ProofOfDeliveryWorkflow
import com.example.digitaldelta.domain.routing.TransportGraph

data class HybridFleetMission(
    val missionId: String,
    val originNodeId: String,
    val destinationNodeId: String,
    val boatVehicleId: String,
    val droneVehicleId: String,
    val graph: TransportGraph,
    val rendezvousInputs: HybridFleetInputs,
    val simulated: Boolean,
)

data class HybridFleetPlan(
    val mission: HybridFleetMission,
    val reachability: Reachability,
    val rendezvous: RendezvousPlan,
)

enum class HybridFleetBlockReason {
    DESTINATION_NOT_DRONE_REQUIRED,
    NO_SUPPORTED_ROUTE,
    LOW_BATTERY,
    PROOF_OF_DELIVERY_REJECTED,
}

sealed interface HybridFleetState {
    data object Unavailable : HybridFleetState
    data class Ready(val plan: HybridFleetPlan) : HybridFleetState
    data class BoatArrived(val plan: HybridFleetPlan) : HybridFleetState
    data class PreparingDroneOffer(val plan: HybridFleetPlan) : HybridFleetState
    data class DroneArrived(val plan: HybridFleetPlan, val offer: DeliveryOfferReady) : HybridFleetState
    data class VerifyingTransfer(val plan: HybridFleetPlan, val offer: DeliveryOfferReady) : HybridFleetState
    data class Transferred(
        val plan: HybridFleetPlan,
        val receipt: CustodyReceiptRecord,
        val chain: List<CustodyReceiptRecord>,
    ) : HybridFleetState
    data class Blocked(
        val reason: HybridFleetBlockReason,
        val mission: HybridFleetMission,
    ) : HybridFleetState
}

interface HybridFleetWorkflow {
    fun snapshot(): HybridFleetState
    suspend fun advance(): HybridFleetState
    fun reset(): HybridFleetState
}

interface HybridFleetEventRecorder {
    suspend fun recordRendezvous(plan: HybridFleetPlan)
    suspend fun recordBoatArrival(plan: HybridFleetPlan)
    suspend fun recordDroneArrival(plan: HybridFleetPlan)
    suspend fun recordDroneCustodyAccepted(plan: HybridFleetPlan)
}

object NoOpHybridFleetEventRecorder : HybridFleetEventRecorder {
    override suspend fun recordRendezvous(plan: HybridFleetPlan) = Unit
    override suspend fun recordBoatArrival(plan: HybridFleetPlan) = Unit
    override suspend fun recordDroneArrival(plan: HybridFleetPlan) = Unit
    override suspend fun recordDroneCustodyAccepted(plan: HybridFleetPlan) = Unit
}

class DefaultHybridFleetWorkflow(
    private val mission: HybridFleetMission,
    private val orchestrator: FleetOrchestrator,
    private val proofOfDelivery: ProofOfDeliveryWorkflow,
    private val eventRecorder: HybridFleetEventRecorder = NoOpHybridFleetEventRecorder,
) : HybridFleetWorkflow {
    private var state: HybridFleetState = createInitialState()

    override fun snapshot(): HybridFleetState = state

    override suspend fun advance(): HybridFleetState {
        state = when (val current = state) {
            is HybridFleetState.Ready -> {
                eventRecorder.recordRendezvous(current.plan)
                eventRecorder.recordBoatArrival(current.plan)
                HybridFleetState.BoatArrived(current.plan)
            }
            is HybridFleetState.BoatArrived -> {
                state = HybridFleetState.PreparingDroneOffer(current.plan)
                val offer = proofOfDelivery.prepare()
                eventRecorder.recordDroneArrival(current.plan)
                HybridFleetState.DroneArrived(current.plan, offer)
            }
            is HybridFleetState.DroneArrived -> {
                state = HybridFleetState.VerifyingTransfer(current.plan, current.offer)
                when (val result = proofOfDelivery.verify(current.offer.qrCode)) {
                    is DeliveryReceiptResult.Verified -> {
                        eventRecorder.recordDroneCustodyAccepted(current.plan)
                        HybridFleetState.Transferred(
                            plan = current.plan,
                            receipt = result.receipt,
                            chain = result.chain,
                        )
                    }
                    is DeliveryReceiptResult.Rejected -> HybridFleetState.Blocked(
                        HybridFleetBlockReason.PROOF_OF_DELIVERY_REJECTED,
                        mission,
                    )
                }
            }
            is HybridFleetState.Transferred -> createInitialState()
            is HybridFleetState.Blocked,
            HybridFleetState.Unavailable,
            is HybridFleetState.PreparingDroneOffer,
            is HybridFleetState.VerifyingTransfer,
            -> current
        }
        return state
    }

    override fun reset(): HybridFleetState {
        state = createInitialState()
        return state
    }

    private fun createInitialState(): HybridFleetState {
        val reachability = orchestrator.classifyReachability(
            mission.graph,
            mission.originNodeId,
            mission.destinationNodeId,
        )
        if (reachability != Reachability.DRONE_REQUIRED) {
            return HybridFleetState.Blocked(
                reason = if (reachability == Reachability.UNREACHABLE) {
                    HybridFleetBlockReason.NO_SUPPORTED_ROUTE
                } else {
                    HybridFleetBlockReason.DESTINATION_NOT_DRONE_REQUIRED
                },
                mission = mission,
            )
        }
        val rendezvous = try {
            orchestrator.computeRendezvous(mission.rendezvousInputs)
        } catch (_: NoFeasibleRendezvousException) {
            return HybridFleetState.Blocked(HybridFleetBlockReason.LOW_BATTERY, mission)
        }
        return HybridFleetState.Ready(HybridFleetPlan(mission, reachability, rendezvous))
    }
}
