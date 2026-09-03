package com.example.digitaldelta.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.digitaldelta.domain.mesh.MeshWireCodec
import com.example.digitaldelta.proto.v1.PriorityClass
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeltaDatabaseTest {
    private lateinit var database: DeltaDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DeltaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun outboxPersistsBinaryEnvelopeAndAcknowledgementState() = runTest {
        val now = 1_800_000_000_000
        val envelope = MeshWireCodec.createEnvelope(
            messageId = "msg-room-1",
            senderNodeId = "clinic-a",
            recipientNodeId = "hospital-c",
            createdAtUnixMs = now,
            expiresAtUnixMs = now + 3_600_000,
            hopLimit = 8,
            priority = PriorityClass.PRIORITY_CLASS_P0,
            payloadHash = ByteArray(32) { 7 },
            simulated = true,
            scenarioSeed = "room-test",
        )

        database.outboxDao().enqueue(
            MeshEnvelopeEntity(
                messageId = envelope.messageId,
                wireBytes = MeshWireCodec.encode(envelope),
                priority = envelope.priorityValue,
                expiresAtUnixMs = envelope.expiresAtUnixMs,
                state = QueueState.PENDING.name,
                attemptCount = 0,
                nextAttemptAtUnixMs = now,
            ),
        )

        val pending = database.outboxDao().pending(now, 10)
        assertEquals(listOf("msg-room-1"), pending.map { it.messageId })
        assertEquals("hospital-c", MeshWireCodec.decode(pending.single().wireBytes).recipientNodeId)

        database.outboxDao().markAcknowledged("msg-room-1", now + 25)
        assertTrue(database.outboxDao().pending(now + 25, 10).isEmpty())
    }

    @Test
    fun nonceClaimIsAtomicAndRejectsReplay() = runTest {
        val nonce = UsedNonceEntity(
            nonceSha256 = "aabbcc",
            deliveryId = "delivery-77",
            usedAtUnixMs = 1_800_000_000_000,
        )

        val first = database.nonceDao().claim(nonce)
        val replay = database.nonceDao().claim(nonce)

        assertTrue(first > 0)
        assertEquals(-1L, replay)
    }

    @Test
    fun operationLogPreservesAppendOrderPerMission() = runTest {
        val dao = database.operationLogDao()
        dao.append(OperationEntity("event-b", "mission-1", "ROUTE_PLANNED", byteArrayOf(2), 20))
        dao.append(OperationEntity("event-a", "mission-1", "REQUEST_CREATED", byteArrayOf(1), 10))

        assertEquals(
            listOf("event-a", "event-b"),
            dao.forMission("mission-1").map { it.eventId },
        )
    }

    @Test
    fun recipientDirectoryPersistsProvisionedPublicKeysWithoutPrivateMaterial() = runTest {
        val record = RecipientKeyEntity(
            nodeId = "N6",
            identityId = "hospital-operator-1",
            displayName = "Habiganj Medical",
            roleCode = "HOSPITAL",
            encryptionKeyId = "n6-encryption-1",
            encryptionPublicKeyDer = byteArrayOf(1, 2, 3),
            signingKeyId = "n6-signing-1",
            signingPublicKeyDer = byteArrayOf(4, 5, 6),
            issuerIdentityId = "delta-admin-1",
            credentialBytes = byteArrayOf(7, 8, 9),
            issuedAtUnixMs = 100,
            expiresAtUnixMs = 200,
            revokedAtUnixMs = null,
            provisionedAtUnixMs = 110,
        )

        database.recipientKeyDao().upsert(record)

        val restored = requireNotNull(database.recipientKeyDao().findByNodeId("N6"))
        assertEquals("n6-encryption-1", restored.encryptionKeyId)
        assertEquals(listOf<Byte>(1, 2, 3), restored.encryptionPublicKeyDer.toList())
    }
}
