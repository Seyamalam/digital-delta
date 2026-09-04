package com.example.digitaldelta.domain.mesh

import com.example.digitaldelta.proto.v1.Acknowledgement
import com.example.digitaldelta.proto.v1.Envelope
import com.example.digitaldelta.proto.v1.PeerFrame
import com.example.digitaldelta.proto.v1.PeerIdentityChallenge
import com.example.digitaldelta.proto.v1.PeerIdentityProof

sealed interface PeerFrameBody {
    data class EnvelopeBytes(val wireBytes: ByteArray) : PeerFrameBody {
        override fun equals(other: Any?): Boolean =
            other is EnvelopeBytes && wireBytes.contentEquals(other.wireBytes)

        override fun hashCode(): Int = wireBytes.contentHashCode()
    }

    data class AcknowledgementMessage(val acknowledgement: Acknowledgement) : PeerFrameBody
    data class IdentityChallengeMessage(val challenge: PeerIdentityChallenge) : PeerFrameBody
    data class IdentityProofMessage(val proof: PeerIdentityProof) : PeerFrameBody
}

object PeerFrameCodec {
    fun encodeEnvelope(wireBytes: ByteArray): ByteArray = PeerFrame.newBuilder()
        .setEnvelope(Envelope.parseFrom(wireBytes))
        .build()
        .toByteArray()

    fun encodeAcknowledgement(acknowledgement: Acknowledgement): ByteArray = PeerFrame.newBuilder()
        .setAcknowledgement(acknowledgement)
        .build()
        .toByteArray()

    fun encodeIdentityChallenge(challenge: PeerIdentityChallenge): ByteArray = PeerFrame.newBuilder()
        .setIdentityChallenge(challenge)
        .build()
        .toByteArray()

    fun encodeIdentityProof(proof: PeerIdentityProof): ByteArray = PeerFrame.newBuilder()
        .setIdentityProof(proof)
        .build()
        .toByteArray()

    fun decode(bytes: ByteArray): PeerFrameBody {
        val frame = PeerFrame.parseFrom(bytes)
        return when (frame.bodyCase) {
            PeerFrame.BodyCase.ENVELOPE -> PeerFrameBody.EnvelopeBytes(frame.envelope.toByteArray())
            PeerFrame.BodyCase.ACKNOWLEDGEMENT -> PeerFrameBody.AcknowledgementMessage(frame.acknowledgement)
            PeerFrame.BodyCase.IDENTITY_CHALLENGE -> PeerFrameBody.IdentityChallengeMessage(frame.identityChallenge)
            PeerFrame.BodyCase.IDENTITY_PROOF -> PeerFrameBody.IdentityProofMessage(frame.identityProof)
            PeerFrame.BodyCase.BODY_NOT_SET,
            null,
            -> error("peer frame body is required")
        }
    }
}
