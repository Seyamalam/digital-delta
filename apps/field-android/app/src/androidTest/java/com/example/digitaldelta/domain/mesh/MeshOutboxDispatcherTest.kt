package com.example.digitaldelta.domain.mesh

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.data.local.MeshEnvelopeEntity
import com.example.digitaldelta.data.local.QueueState
import com.example.digitaldelta.proto.v1.Acknowledgement
import com.example.digitaldelta.proto.v1.AcknowledgementStatus
import com.example.digitaldelta.proto.v1.PriorityClass
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeshOutboxDispatcherTest {
    private lateinit var database: DeltaDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            DeltaDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun interruptedSendIsRetriedThenAcknowledgedAfterRemoteDurableReceipt() = runTest {
        val now = 1_800_000_000_000
        database.outboxDao().enqueue(pendingEnvelope(now))
        val transport = InterruptOnceTransport()

        val interrupted = MeshOutboxDispatcher(database, transport, nowUnixMs = { now })
            .dispatch(peerId = "C")

        assertEquals(1, interrupted.retryScheduled)
        val retry = requireNotNull(database.outboxDao().find("resume-1"))
        assertEquals(QueueState.PENDING.name, retry.state)
        assertEquals(1, retry.attemptCount)
        assertTrue(retry.nextAttemptAtUnixMs > now)

        val resumed = MeshOutboxDispatcher(database, transport, nowUnixMs = { retry.nextAttemptAtUnixMs })
            .dispatch(peerId = "C")

        assertEquals(1, resumed.acknowledged)
        assertEquals(QueueState.ACKNOWLEDGED.name, database.outboxDao().find("resume-1")?.state)
        assertEquals(2, transport.calls)
    }

    @Test
    fun expiredPendingMessageMovesToDeadLetterWithoutTransmission() = runTest {
        val now = 1_800_000_000_000
        database.outboxDao().enqueue(pendingEnvelope(now - 100_000))
        val transport = InterruptOnceTransport()

        val report = MeshOutboxDispatcher(database, transport, nowUnixMs = { now }).dispatch("C")

        assertEquals(1, report.deadLettered)
        assertEquals(0, transport.calls)
        assertEquals(QueueState.DEAD_LETTER.name, database.outboxDao().find("resume-1")?.state)
    }

    private fun pendingEnvelope(createdAt: Long): MeshEnvelopeEntity {
        val envelope = MeshWireCodec.createEnvelope(
            messageId = "resume-1",
            senderNodeId = "B",
            recipientNodeId = "C",
            createdAtUnixMs = createdAt,
            expiresAtUnixMs = createdAt + 60_000,
            hopLimit = 3,
            priority = PriorityClass.PRIORITY_CLASS_P0,
            payloadHash = ByteArray(32) { 4 },
            simulated = false,
            scenarioSeed = "",
        )
        return MeshEnvelopeEntity(
            messageId = envelope.messageId,
            wireBytes = MeshWireCodec.encode(envelope),
            priority = envelope.priorityValue,
            expiresAtUnixMs = envelope.expiresAtUnixMs,
            state = QueueState.PENDING.name,
            attemptCount = 0,
            nextAttemptAtUnixMs = createdAt,
        )
    }
}

private class InterruptOnceTransport : PeerTransport {
    var calls = 0

    override suspend fun send(peerId: String, wireBytes: ByteArray): Acknowledgement {
        calls += 1
        if (calls == 1) error("peer disconnected mid-transfer")
        return Acknowledgement.newBuilder()
            .setMessageId(MeshWireCodec.decode(wireBytes).messageId)
            .setNodeId(peerId)
            .setStatus(AcknowledgementStatus.ACKNOWLEDGEMENT_STATUS_DURABLY_STORED)
            .setRecordedAtUnixMs(1_800_000_001_000)
            .build()
    }
}
