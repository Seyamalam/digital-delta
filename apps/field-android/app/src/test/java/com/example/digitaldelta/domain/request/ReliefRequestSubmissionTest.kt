package com.example.digitaldelta.domain.request

import com.example.digitaldelta.data.local.MeshEnvelopeEntity
import com.example.digitaldelta.data.local.OperationEntity
import com.example.digitaldelta.domain.mesh.MeshWireCodec
import com.example.digitaldelta.domain.mesh.ProtectedPayload
import com.example.digitaldelta.domain.mesh.MeshPayloadProtector
import com.example.digitaldelta.proto.v1.DomainEvent
import com.example.digitaldelta.proto.v1.PriorityClass
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReliefRequestSubmissionTest {
    @Test
    fun `submit persists a domain event and encrypted protobuf envelope atomically`() = runTest {
        val persistence = RecordingRequestPersistence()
        val service = DefaultReliefRequestSubmission(
            persistence = persistence,
            payloadProtector = ReversingPayloadProtector,
            nowUnixMs = { 1_800_000_000_000 },
            nextId = sequenceOf("request-1", "event-1", "message-1").iterator()::next,
        )

        val receipt = service.submit(
            ReliefRequestDraft(
                requesterNodeId = "clinic-a",
                originNodeId = "camp-4",
                destinationNodeId = "hospital-1",
                cargo = listOf(
                    CargoDraft("medicine", 11, "pack"),
                    CargoDraft("ors", 20, "sachet"),
                ),
                priority = PriorityClass.PRIORITY_CLASS_P0,
                simulated = false,
                scenarioSeed = "",
            ),
        )

        assertEquals("request-1", receipt.requestId)
        assertEquals("message-1", receipt.messageId)
        assertEquals(1, persistence.calls)

        val operation = requireNotNull(persistence.operation)
        val event = DomainEvent.parseFrom(operation.payloadBytes)
        assertEquals("request-1", event.reliefRequestCreated.requestId)
        assertEquals(11, event.reliefRequestCreated.cargoList.first().quantity)

        val queued = requireNotNull(persistence.envelope)
        val envelope = MeshWireCodec.decode(queued.wireBytes)
        assertEquals(PriorityClass.PRIORITY_CLASS_P0, envelope.priority)
        assertEquals("hospital-1", envelope.recipientNodeId)
        assertEquals("hospital-1-key", envelope.encryptedPayload.recipientKeyId)
        assertTrue(envelope.encryptedPayload.aes256GcmCiphertext.size() > 0)
        assertFalse(envelope.encryptedPayload.aes256GcmCiphertext.toByteArray().contentEquals(operation.payloadBytes))
    }
}

private class RecordingRequestPersistence : RequestPersistence {
    var calls = 0
    var operation: OperationEntity? = null
    var envelope: MeshEnvelopeEntity? = null

    override suspend fun persist(operation: OperationEntity, envelope: MeshEnvelopeEntity) {
        calls += 1
        this.operation = operation
        this.envelope = envelope
    }
}

private object ReversingPayloadProtector : MeshPayloadProtector {
    override fun protect(recipientNodeId: String, plaintext: ByteArray, associatedData: ByteArray): ProtectedPayload =
        ProtectedPayload(
            recipientKeyId = "$recipientNodeId-key",
            ciphertext = plaintext.reversedArray() + byteArrayOf(9),
            nonce = ByteArray(12) { 3 },
            associatedDataSha256 = associatedData,
        )
}
