package com.example.digitaldelta.domain.pod

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.domain.identity.AndroidDeviceIdentityKeyStore
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

    private fun workflow() = RoomProofOfDeliveryWorkflow(
        database = database,
        deviceKeys = AndroidDeviceIdentityKeyStore(),
        nowUnixMs = { now },
        nonceBytes = { ByteArray(16) { index -> (sequence + index).toByte() }.also { sequence += 32 } },
        eventId = { "custody-event-${++sequence}" },
    )
}
