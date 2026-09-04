package com.example.digitaldelta.domain.mesh

import com.example.digitaldelta.proto.v1.EncryptedPayload
import com.example.digitaldelta.proto.v1.Envelope
import com.example.digitaldelta.proto.v1.Signature
import com.google.protobuf.ByteString
import java.security.KeyPairGenerator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvelopeSecurityTest {
    @Test
    fun originSignatureProtectsImmutableEnvelopeButAllowsRelayHopIncrement() {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val unsigned = Envelope.newBuilder()
            .setSchemaVersion(1).setMessageId("origin-event").setSenderNodeId("N4")
            .setRecipientNodeId("N6").setHopLimit(4).setExpiresAtUnixMs(1_800_000_060_000)
            .setEncryptedPayload(EncryptedPayload.newBuilder().setAes256GcmCiphertext(ByteString.copyFromUtf8("opaque ciphertext")))
            .build()
        val bytes = MeshAcknowledgementSecurity.rsaPss().run {
            initSign(key.private)
            update(EnvelopeSecurity.canonical(unsigned))
            sign()
        }
        val signed = unsigned.toBuilder().setSenderSignature(Signature.newBuilder()
            .setAlgorithm(MeshAcknowledgementSecurity.SIGNATURE_ALGORITHM)
            .setRsa2048PssSha256(ByteString.copyFrom(bytes))).build()
        assertTrue(EnvelopeSecurity.verifySignature(signed, key.public.encoded))
        assertTrue(EnvelopeSecurity.verifySignature(signed.toBuilder().setHopCount(2).build(), key.public.encoded))
        for (altered in listOf(
            signed.toBuilder().setSenderNodeId("N1").build(),
            signed.toBuilder().setRecipientNodeId("N1").build(),
            signed.toBuilder().setExpiresAtUnixMs(Long.MAX_VALUE).build(),
            signed.toBuilder().setHopLimit(100).build(),
            signed.toBuilder().clearEncryptedPayload().build(),
            signed.toBuilder().clearSenderSignature().build(),
        )) assertFalse(EnvelopeSecurity.verifySignature(altered, key.public.encoded))
    }
}
