package com.example.digitaldelta.domain.mesh

import com.example.digitaldelta.proto.v1.PriorityClass
import com.example.digitaldelta.proto.v1.TransportMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class MeshWireCodecTest {
    @Test
    fun `round trips a protobuf envelope without JSON`() {
        val payloadHash = ByteArray(32) { 0x2a }
        val envelope = MeshWireCodec.createEnvelope(
            messageId = "msg-101",
            senderNodeId = "boat-7",
            recipientNodeId = "hospital-2",
            createdAtUnixMs = 1_800_000_000_000,
            expiresAtUnixMs = 1_800_003_600_000,
            hopLimit = 8,
            priority = PriorityClass.PRIORITY_CLASS_P0,
            payloadHash = payloadHash,
            simulated = true,
            scenarioSeed = "fair-demo-01",
        )

        val encoded = MeshWireCodec.encode(envelope)
        val decoded = MeshWireCodec.decode(encoded)

        assertEquals("msg-101", decoded.messageId)
        assertEquals("boat-7", decoded.senderNodeId)
        assertEquals(8, decoded.hopLimit)
        assertArrayEquals(payloadHash, decoded.payloadSha256.toByteArray())
        assertFalse(encoded.contentEquals(envelope.toString().encodeToByteArray()))
    }

    @Test
    fun `rejects an invalid SHA-256 length before transmission`() {
        assertThrows(IllegalArgumentException::class.java) {
            MeshWireCodec.createEnvelope(
                messageId = "msg-invalid",
                senderNodeId = "boat-7",
                recipientNodeId = "hospital-2",
                createdAtUnixMs = 1,
                expiresAtUnixMs = 2,
                hopLimit = 8,
                priority = PriorityClass.PRIORITY_CLASS_P1,
                payloadHash = ByteArray(31),
                simulated = false,
                scenarioSeed = "",
            )
        }
    }

    @Test
    fun `shared transport modes include road waterway and airway`() {
        assertEquals(1, TransportMode.TRANSPORT_MODE_ROAD.number)
        assertEquals(2, TransportMode.TRANSPORT_MODE_WATERWAY.number)
        assertEquals(3, TransportMode.TRANSPORT_MODE_AIRWAY.number)
    }
}
