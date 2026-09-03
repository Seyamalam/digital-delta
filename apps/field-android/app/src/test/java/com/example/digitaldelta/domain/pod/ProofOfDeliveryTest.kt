package com.example.digitaldelta.domain.pod

import org.junit.Assert.assertEquals
import org.junit.Test

class ProofOfDeliveryTest {
    private val keyPair = DeliverySigner.generateKeyPair()
    private val signer = DeliverySigner(keyPair.private, keyPair.public)

    @Test
    fun `valid signed handoff is accepted exactly once`() {
        val unsigned = UnsignedHandoff(
            deliveryId = "DELTA-2026-0001",
            senderId = "Boat-02",
            recipientId = "Hospital-01",
            payloadSha256 = DeliverySigner.sha256("medicine:10|ors:20"),
            nonce = "nonce-0001",
            timestampMillis = 1_788_374_217_000L,
            previousReceiptHash = "genesis",
        )
        val proof = signer.sign(unsigned)
        val verifier = ProofOfDeliveryVerifier(InMemoryNonceStore(), allowedClockSkewMillis = 60_000)

        assertEquals(VerificationResult.VERIFIED, verifier.verify(proof, keyPair.public, 1_788_374_230_000L))
        assertEquals(VerificationResult.REPLAY_REJECTED, verifier.verify(proof, keyPair.public, 1_788_374_231_000L))
    }

    @Test
    fun `tampered delivery field is rejected`() {
        val proof = signer.sign(
            UnsignedHandoff(
                deliveryId = "DELTA-2026-0001",
                senderId = "Boat-02",
                recipientId = "Hospital-01",
                payloadSha256 = DeliverySigner.sha256("medicine:10"),
                nonce = "nonce-0002",
                timestampMillis = 1_788_374_217_000L,
                previousReceiptHash = "genesis",
            ),
        )
        val tampered = proof.copy(unsigned = proof.unsigned.copy(recipientId = "Unknown"))

        assertEquals(
            VerificationResult.INVALID_SIGNATURE,
            ProofOfDeliveryVerifier(InMemoryNonceStore(), 60_000)
                .verify(tampered, keyPair.public, 1_788_374_230_000L),
        )
    }
}
