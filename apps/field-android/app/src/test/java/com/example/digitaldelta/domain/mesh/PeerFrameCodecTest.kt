package com.example.digitaldelta.domain.mesh

import com.example.digitaldelta.proto.v1.Acknowledgement
import com.example.digitaldelta.proto.v1.AcknowledgementStatus
import com.example.digitaldelta.proto.v1.PriorityClass
import org.junit.Assert.assertEquals
import org.junit.Test

class PeerFrameCodecTest {
    @Test
    fun `nearby frame distinguishes protobuf envelopes from acknowledgements`() {
        val envelope = MeshWireCodec.createEnvelope(
            messageId = "frame-1",
            senderNodeId = "A",
            recipientNodeId = "C",
            createdAtUnixMs = 100,
            expiresAtUnixMs = 200,
            hopLimit = 4,
            priority = PriorityClass.PRIORITY_CLASS_P0,
            payloadHash = ByteArray(32),
            simulated = false,
            scenarioSeed = "",
        )
        val acknowledgement = Acknowledgement.newBuilder()
            .setMessageId("frame-1")
            .setNodeId("B")
            .setStatus(AcknowledgementStatus.ACKNOWLEDGEMENT_STATUS_DURABLY_STORED)
            .build()

        assertEquals(
            PeerFrameBody.EnvelopeBytes(envelope.toByteArray()),
            PeerFrameCodec.decode(PeerFrameCodec.encodeEnvelope(envelope.toByteArray())),
        )
        assertEquals(
            PeerFrameBody.AcknowledgementMessage(acknowledgement),
            PeerFrameCodec.decode(PeerFrameCodec.encodeAcknowledgement(acknowledgement)),
        )
    }
}
