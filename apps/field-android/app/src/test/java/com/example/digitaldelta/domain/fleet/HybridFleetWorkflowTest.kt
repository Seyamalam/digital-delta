package com.example.digitaldelta.domain.fleet

import com.example.digitaldelta.domain.pod.CustodyChain
import com.example.digitaldelta.domain.pod.CustodyReceiptRecord
import com.example.digitaldelta.domain.pod.DeliveryOfferReady
import com.example.digitaldelta.domain.pod.DeliveryReceiptResult
import com.example.digitaldelta.domain.pod.ProofOfDeliveryWorkflow
import com.example.digitaldelta.domain.routing.EdgeMode
import com.example.digitaldelta.domain.routing.MapEdge
import com.example.digitaldelta.domain.routing.MapNode
import com.example.digitaldelta.domain.routing.TransportGraph
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridFleetWorkflowTest {
    @Test
    fun `drone required plan advances through signed custody transfer`() = runTest {
        val proof = FakeDroneProofOfDelivery()
        val events = FakeHybridFleetEventRecorder()
        val workflow = DefaultHybridFleetWorkflow(mission(), FleetOrchestrator(), proof, events)

        val ready = workflow.snapshot() as HybridFleetState.Ready
        assertEquals(Reachability.DRONE_REQUIRED, ready.plan.reachability)
        assertTrue(ready.plan.rendezvous.projectedDroneBatteryPercent >= 20)

        assertTrue(workflow.advance() is HybridFleetState.BoatArrived)
        val droneArrived = workflow.advance() as HybridFleetState.DroneArrived
        assertEquals("simulated-drone-07", droneArrived.offer.recipientIdentityId)

        val transferred = workflow.advance() as HybridFleetState.Transferred
        assertEquals("simulated-drone-07", transferred.receipt.recipientIdentityId)
        assertEquals(1, transferred.chain.size)
        assertEquals(1, proof.prepareCalls)
        assertEquals(1, proof.verifyCalls)
        assertEquals(
            listOf("rendezvous", "boat-arrived", "drone-arrived", "custody-accepted"),
            events.recorded,
        )
    }

    @Test
    fun `low battery begins blocked and cannot create custody offer`() = runTest {
        val proof = FakeDroneProofOfDelivery()
        val lowBatteryMission = mission().copy(
            rendezvousInputs = mission().rendezvousInputs.copy(droneBatteryPercent = 21),
        )
        val workflow = DefaultHybridFleetWorkflow(lowBatteryMission, FleetOrchestrator(), proof)

        val blocked = workflow.snapshot() as HybridFleetState.Blocked
        assertEquals(HybridFleetBlockReason.LOW_BATTERY, blocked.reason)
        assertTrue(workflow.advance() is HybridFleetState.Blocked)
        assertEquals(0, proof.prepareCalls)
    }

    private fun mission(): HybridFleetMission {
        val nodes = listOf(
            MapNode("N1", "Sylhet Hub", 24.8949, 91.8687),
            MapNode("N7", "Haor Clinic", 25.12, 91.68),
        )
        return HybridFleetMission(
            missionId = "mission-drone-demo-01",
            originNodeId = "N1",
            destinationNodeId = "N7",
            boatVehicleId = "boat-02",
            droneVehicleId = "drone-07",
            graph = TransportGraph(
                nodes = nodes,
                edges = listOf(MapEdge("A7", "N1", "N7", EdgeMode.AIRWAY, 28, simulated = true)),
            ),
            rendezvousInputs = HybridFleetInputs(
                boatPosition = GeoPoint(25.04, 91.57),
                droneBase = GeoPoint(24.9632, 91.8668),
                droneDestination = GeoPoint(25.12, 91.68),
                candidates = listOf(
                    NamedPoint("R1", GeoPoint(25.0658, 91.6073)),
                    NamedPoint("R2", GeoPoint(25.0715, 91.7554)),
                    NamedPoint("R3", GeoPoint(25.02, 91.70)),
                ),
                boatSpeedKph = 24.0,
                droneSpeedKph = 55.0,
                droneBatteryPercent = 74,
                droneRangeAtFullChargeKm = 60.0,
                reserveBatteryPercent = 20,
            ),
            simulated = true,
        )
    }
}

private class FakeHybridFleetEventRecorder : HybridFleetEventRecorder {
    val recorded = mutableListOf<String>()

    override suspend fun recordRendezvous(plan: HybridFleetPlan) {
        recorded += "rendezvous"
    }

    override suspend fun recordBoatArrival(plan: HybridFleetPlan) {
        recorded += "boat-arrived"
    }

    override suspend fun recordDroneArrival(plan: HybridFleetPlan) {
        recorded += "drone-arrived"
    }

    override suspend fun recordDroneCustodyAccepted(plan: HybridFleetPlan) {
        recorded += "custody-accepted"
    }
}

private class FakeDroneProofOfDelivery : ProofOfDeliveryWorkflow {
    var prepareCalls = 0
    var verifyCalls = 0

    private val receipt = CustodyReceiptRecord(
        eventId = "custody-drone-1",
        deliveryId = "DELTA-DRONE-0001",
        senderIdentityId = "boat-operator-02",
        recipientIdentityId = "simulated-drone-07",
        previousReceiptSha256 = ByteArray(32),
        receiptHash = ByteArray(32) { 7 },
        recordedAtUnixMs = 1_800_000_000_100,
    )

    override suspend fun prepare(): DeliveryOfferReady {
        prepareCalls++
        return DeliveryOfferReady(
            qrCode = "DIGITALDELTA:POD:drone",
            deliveryId = receipt.deliveryId,
            senderIdentityId = receipt.senderIdentityId,
            recipientIdentityId = receipt.recipientIdentityId,
            senderSigningKeyId = "boat-key",
            payloadSha256 = ByteArray(32) { 3 },
            nonce = ByteArray(16) { 4 },
            timestampUnixMs = 1_800_000_000_000,
            previousReceiptSha256 = ByteArray(32),
            simulatedVehicle = true,
        )
    }

    override suspend fun verify(code: String): DeliveryReceiptResult {
        verifyCalls++
        return DeliveryReceiptResult.Verified(receipt, listOf(receipt))
    }

    override suspend fun reconstructChain(): CustodyChain = CustodyChain(listOf(receipt), true)
    override fun tamperForDemo(code: String): String = code
}
