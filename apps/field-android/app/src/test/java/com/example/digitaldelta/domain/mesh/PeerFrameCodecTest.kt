package com.example.digitaldelta.domain.mesh

import com.example.digitaldelta.proto.v1.Acknowledgement
import com.example.digitaldelta.proto.v1.AcknowledgementStatus
import com.example.digitaldelta.proto.v1.PriorityClass
import com.example.digitaldelta.proto.v1.PeerIdentityChallenge
import com.example.digitaldelta.proto.v1.PeerIdentityProof
import com.google.protobuf.ByteString
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
        val challenge = PeerIdentityChallenge.newBuilder()
            .setChallengerNodeId("A")
            .setNonce(ByteString.copyFrom(ByteArray(32) { 1 }))
            .setIssuedAtUnixMs(100)
            .setExpiresAtUnixMs(130)
            .build()
        val proof = PeerIdentityProof.newBuilder()
            .setChallenge(challenge)
            .setProverNodeId("B")
            .setSignedAtUnixMs(101)
            .build()

        assertEquals(
            PeerFrameBody.EnvelopeBytes(envelope.toByteArray()),
            PeerFrameCodec.decode(PeerFrameCodec.encodeEnvelope(envelope.toByteArray())),
        )
        assertEquals(
            PeerFrameBody.AcknowledgementMessage(acknowledgement),
            PeerFrameCodec.decode(PeerFrameCodec.encodeAcknowledgement(acknowledgement)),
        )
        assertEquals(
            PeerFrameBody.IdentityChallengeMessage(challenge),
            PeerFrameCodec.decode(PeerFrameCodec.encodeIdentityChallenge(challenge)),
        )
        assertEquals(
            PeerFrameBody.IdentityProofMessage(proof),
            PeerFrameCodec.decode(PeerFrameCodec.encodeIdentityProof(proof)),
        )
    }
}
