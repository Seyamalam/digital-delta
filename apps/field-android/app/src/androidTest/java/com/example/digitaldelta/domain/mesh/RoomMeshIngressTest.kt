package com.example.digitaldelta.domain.mesh

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.proto.v1.AcknowledgementStatus
import com.example.digitaldelta.proto.v1.PriorityClass
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMeshIngressTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: DeltaDatabase

    @Before
    fun createDatabase() {
        context.deleteDatabase(TEST_DATABASE)
        database = openDatabase()
    }

    @After
    fun closeDatabase() {
        database.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun durableRelaySurvivesRestartAndRejectsDuplicate() = runTest {
        val now = 1_800_000_000_000
        val original = envelope("relay-survives", now, expiresAt = now + 60_000, hopCount = 0, hopLimit = 3)
        val ingress = RoomMeshIngress(database, localNodeId = "B", nowUnixMs = { now })

        val accepted = ingress.receive(MeshWireCodec.encode(original))

        assertEquals(AcknowledgementStatus.ACKNOWLEDGEMENT_STATUS_DURABLY_STORED, accepted.status)
        assertNotNull(database.meshInboxDao().find("relay-survives"))
        val queued = database.outboxDao().pending(now, 10).single()
        assertEquals(1, MeshWireCodec.decode(queued.wireBytes).hopCount)

        database.close()
        database = openDatabase()
        assertEquals("relay-survives", database.outboxDao().pending(now, 10).single().messageId)

        val duplicate = RoomMeshIngress(database, localNodeId = "B", nowUnixMs = { now + 1 })
            .receive(MeshWireCodec.encode(original))
        assertEquals(AcknowledgementStatus.ACKNOWLEDGEMENT_STATUS_REJECTED, duplicate.status)
        assertEquals("DUPLICATE", duplicate.reasonCode)
        assertEquals(1, database.meshInboxDao().count())
    }

    @Test
    fun expiredAndHopLimitedMessagesAreRejectedWithoutPersistence() = runTest {
        val now = 1_800_000_000_000
        val ingress = RoomMeshIngress(database, localNodeId = "B", nowUnixMs = { now })

        val expired = ingress.receive(
            MeshWireCodec.encode(envelope("expired", now - 10, expiresAt = now, hopCount = 0, hopLimit = 3)),
        )
        val hopLimited = ingress.receive(
            MeshWireCodec.encode(envelope("hop-limit", now - 10, expiresAt = now + 10, hopCount = 3, hopLimit = 3)),
        )

        assertEquals("EXPIRED", expired.reasonCode)
        assertEquals("HOP_LIMIT_REACHED", hopLimited.reasonCode)
        assertTrue(database.meshInboxDao().count() == 0)
        assertTrue(database.outboxDao().pending(now, 10).isEmpty())
    }

    private fun envelope(
        messageId: String,
        createdAt: Long,
        expiresAt: Long,
        hopCount: Int,
        hopLimit: Int,
    ) = MeshWireCodec.createEnvelope(
        messageId = messageId,
        senderNodeId = "A",
        recipientNodeId = "C",
        createdAtUnixMs = createdAt,
        expiresAtUnixMs = expiresAt,
        hopLimit = hopLimit,
        priority = PriorityClass.PRIORITY_CLASS_P0,
        payloadHash = ByteArray(32) { 9 },
        simulated = false,
        scenarioSeed = "",
    ).toBuilder().setHopCount(hopCount).build()

    private fun openDatabase(): DeltaDatabase =
        Room.databaseBuilder(context, DeltaDatabase::class.java, TEST_DATABASE)
            .allowMainThreadQueries()
            .build()

    companion object {
        private const val TEST_DATABASE = "room-mesh-ingress-test"
    }
}
