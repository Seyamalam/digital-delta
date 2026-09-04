package com.example.digitaldelta.domain.pod

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.domain.identity.AndroidDeviceIdentityKeyStore
import com.example.digitaldelta.domain.identity.InstalledIdentityCredential
import com.example.digitaldelta.proto.v1.IdentityRole
import com.example.digitaldelta.proto.v1.DomainEvent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomProofOfDeliveryWorkflowTest {
    private lateinit var database: DeltaDatabase
    private var now = 1_800_000_000_000L
    private var sequence = 0

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DeltaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun close() = database.close()

    @Test
    fun validOfferIsRecordedOnceAndReplayCannotChangeCustody() = runTest {
        val workflow = workflow()
        val ready = workflow.prepare()

        val accepted = workflow.verify(ready.qrCode) as DeliveryReceiptResult.Verified
        val replay = workflow.verify(ready.qrCode) as DeliveryReceiptResult.Rejected

        assertEquals(1, accepted.chain.size)
        assertEquals(DeliveryOfferRejection.REPLAY_REJECTED, replay.reason)
        assertEquals(1, replay.preservedChain.size)
        assertEquals(1, database.operationLogDao().forMission("mission-pod-demo-01").size)
        val event = DomainEvent.parseFrom(
            database.operationLogDao().forMission("mission-pod-demo-01").single().payloadBytes,
        )
        assertTrue(event.custodyTransfer.senderSignature.rsa2048PssSha256.size() > 0)
        assertTrue(event.custodyTransfer.recipientSignature.rsa2048PssSha256.size() > 0)
        assertTrue(event.simulated)
    }

    @Test
    fun secondAcceptedOfferLinksToFirstReceiptHash() = runTest {
        val workflow = workflow()
        val first = workflow.verify(workflow.prepare().qrCode) as DeliveryReceiptResult.Verified
        now += 1_000

        val secondReady = workflow.prepare()
        val second = workflow.verify(secondReady.qrCode) as DeliveryReceiptResult.Verified

        assertEquals(2, second.chain.size)
        assertArrayEquals(first.receipt.receiptHash, secondReady.previousReceiptSha256)
        assertArrayEquals(first.receipt.receiptHash, second.receipt.previousReceiptSha256)
        assertEquals(true, workflow.reconstructChain().valid)
    }

    @Test
    fun tamperedOfferDoesNotClaimNonceOrAppendReceipt() = runTest {
        val workflow = workflow()
        val ready = workflow.prepare()
        val tampered = DeliveryOfferCodec().tamperRecipientForDemo(ready.qrCode, "unknown-recipient")

        val rejected = workflow.verify(tampered) as DeliveryReceiptResult.Rejected
        val acceptedAfterward = workflow.verify(ready.qrCode) as DeliveryReceiptResult.Verified

        assertEquals(DeliveryOfferRejection.INVALID_SIGNATURE, rejected.reason)
        assertTrue(rejected.preservedChain.isEmpty())
        assertEquals(1, acceptedAfterward.chain.size)
    }

    @Test
    fun tenMinuteFieldClockToleranceIsAcceptedButStillBounded() = runTest {
        val workflow = workflow()
        val ready = workflow.prepare()
        now += 599_999

        assertTrue(workflow.verify(ready.qrCode) is DeliveryReceiptResult.Verified)
        now += 2

        val expired = workflow.verify(ready.qrCode) as DeliveryReceiptResult.Rejected
        assertEquals(DeliveryOfferRejection.CLOCK_SKEW, expired.reason)
        assertEquals(1, expired.preservedChain.size)
    }

    @Test
    fun customDroneScenarioSignsAndStoresBoatToDroneCustody() = runTest {
        val scenario = DeliveryScenario(
            missionId = "mission-drone-demo-01",
            deliveryId = "DELTA-DRONE-0001",
            senderNodeId = "pod-demo-boat-02",
            recipientNodeId = "pod-demo-drone-07",
            senderIdentityId = "boat-operator-02",
            recipientIdentityId = "simulated-drone-07",
            payloadDescription = "p0-medicine:4|blood-cooler:1",
            scenarioSeed = "m8-drone-handoff-v1",
            simulatedVehicle = true,
        )
        val workflow = RoomProofOfDeliveryWorkflow(
            database = database,
            deviceKeys = AndroidDeviceIdentityKeyStore(),
            scenario = scenario,
            nowUnixMs = { now },
            nonceBytes = { ByteArray(16) { index -> (sequence + index).toByte() }.also { sequence += 32 } },
            eventId = { "custody-drone-${++sequence}" },
        )

        val offer = workflow.prepare()
        val result = workflow.verify(offer.qrCode) as DeliveryReceiptResult.Verified
        val event = DomainEvent.parseFrom(
            database.operationLogDao().forMission(scenario.missionId).single().payloadBytes,
        )

        assertEquals("simulated-drone-07", offer.recipientIdentityId)
        assertEquals("simulated-drone-07", result.receipt.recipientIdentityId)
        assertEquals("simulated-drone-07", event.custodyTransfer.recipientIdentityId)
        assertTrue(event.custodyTransfer.recipientSignature.rsa2048PssSha256.size() > 0)
        assertEquals("m8-drone-handoff-v1", event.scenarioSeed)
    }

    @Test
    fun realSenderWithoutInstalledCredentialIsRejectedWithoutClaimingNonce() = runTest {
        val workflow = RoomProofOfDeliveryWorkflow(
            database = database,
            deviceKeys = AndroidDeviceIdentityKeyStore(),
            scenario = RoomProofOfDeliveryWorkflow.DEFAULT_SCENARIO.copy(simulatedVehicle = false),
            nowUnixMs = { now },
            nonceBytes = { ByteArray(16) { it.toByte() } },
            eventId = { "must-not-be-recorded" },
            senderCredentialLookup = { null },
        )

        val result = workflow.verify(workflow.prepare().qrCode) as DeliveryReceiptResult.Rejected

        assertEquals(DeliveryOfferRejection.UNKNOWN_SIGNING_KEY, result.reason)
        assertTrue(result.preservedChain.isEmpty())
        assertTrue(database.operationLogDao().forMission("mission-pod-demo-01").isEmpty())
    }

    @Test
    fun realSenderWithExpiredCredentialIsRejectedWithoutChangingCustody() = runTest {
        val keys = AndroidDeviceIdentityKeyStore()
        val publicIdentity = keys.createOrGet(RoomProofOfDeliveryWorkflow.DEFAULT_SCENARIO.senderNodeId)
        val expired = InstalledIdentityCredential(
            credentialId = "cred-expired-sender",
            identityId = RoomProofOfDeliveryWorkflow.DEFAULT_SCENARIO.senderIdentityId,
            nodeId = RoomProofOfDeliveryWorkflow.DEFAULT_SCENARIO.senderNodeId,
            role = IdentityRole.IDENTITY_ROLE_DRIVER,
            encryptionKeyId = publicIdentity.encryptionKeyId,
            encryptionPublicKeyDer = publicIdentity.encryptionPublicKeyDer,
            signingKeyId = publicIdentity.signingKeyId,
            signingPublicKeyDer = publicIdentity.signingPublicKeyDer,
            issuerIdentityId = "admin-sylhet-01",
            issuedAtUnixMs = now - 60_000,
            expiresAtUnixMs = now,
            revokedAtUnixMs = null,
        )
        val workflow = RoomProofOfDeliveryWorkflow(
            database = database,
            deviceKeys = keys,
            scenario = RoomProofOfDeliveryWorkflow.DEFAULT_SCENARIO.copy(simulatedVehicle = false),
            nowUnixMs = { now },
            nonceBytes = { ByteArray(16) { it.toByte() } },
            eventId = { "must-not-be-recorded" },
            senderCredentialLookup = { expired },
        )

        val result = workflow.verify(workflow.prepare().qrCode) as DeliveryReceiptResult.Rejected

        assertEquals(DeliveryOfferRejection.CREDENTIAL_EXPIRED, result.reason)
        assertTrue(result.preservedChain.isEmpty())
        assertTrue(database.operationLogDao().forMission("mission-pod-demo-01").isEmpty())
    }

    private fun workflow() = RoomProofOfDeliveryWorkflow(
        database = database,
        deviceKeys = AndroidDeviceIdentityKeyStore(),
        nowUnixMs = { now },
        nonceBytes = { ByteArray(16) { index -> (sequence + index).toByte() }.also { sequence += 32 } },
        eventId = { "custody-event-${++sequence}" },
    )
}
