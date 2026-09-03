package com.example.digitaldelta.domain.pod

import com.example.digitaldelta.proto.v1.SignedDeliveryOffer
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DeliveryOfferCodecTest {
    private val signer = RsaPssDeliverySigner.generate("boat-signing-key-1")
    private val codec = DeliveryOfferCodec()
    private val now = 1_800_000_000_000L

    @Test
    fun `protobuf QR verifies every required field against the trusted sender`() {
        val code = codec.createCode(
            DeliveryOfferDraft(
                deliveryId = "DELTA-2026-0001",
                missionId = "mission-sylhet-01",
                senderIdentityId = "boat-operator-02",
                recipientIdentityId = "hospital-operator-01",
                payloadSha256 = sha256("medicine:10|ors:20".encodeToByteArray()),
                nonce = ByteArray(16) { it.toByte() },
                timestampUnixMs = now,
                previousReceiptSha256 = sha256("genesis".encodeToByteArray()),
                simulatedVehicle = true,
            ),
            signer,
        )

        val result = codec.verifyCode(code, trusted(now)) as DeliveryOfferVerification.Verified

        assertEquals("DELTA-2026-0001", result.signedOffer.offer.deliveryId)
        assertEquals("RSA-2048-PSS-SHA256", result.signedOffer.senderSignature.algorithm)
        assertArrayEquals(signer.publicKeyDer, result.signedOffer.senderSigningPublicKeyDer.toByteArray())
    }

    @Test
    fun `changed recipient with original signature is rejected as tampered`() {
        val code = validCode()
        val signed = codec.decodeCode(code)
        val tampered = signed.toBuilder()
            .setOffer(signed.offer.toBuilder().setRecipientIdentityId("unknown-recipient"))
            .build()
        val tamperedCode = DeliveryOfferCodec.CODE_PREFIX +
            Base64.getUrlEncoder().withoutPadding().encodeToString(tampered.toByteArray())

        assertEquals(
            DeliveryOfferRejection.INVALID_SIGNATURE,
            (codec.verifyCode(tamperedCode, trusted(now)) as DeliveryOfferVerification.Rejected).reason,
        )
    }

    @Test
    fun `valid signature for another delivery is rejected distinctly`() {
        val wrongDelivery = codec.createCode(
            draft().copy(deliveryId = "DELTA-2026-9999"),
            signer,
        )

        assertEquals(
            DeliveryOfferRejection.WRONG_DELIVERY,
            (codec.verifyCode(wrongDelivery, trusted(now)) as DeliveryOfferVerification.Rejected).reason,
        )
    }

    @Test
    fun `expired offer is rejected before nonce persistence`() {
        assertEquals(
            DeliveryOfferRejection.CLOCK_SKEW,
            (codec.verifyCode(validCode(), trusted(now + 120_001)) as DeliveryOfferVerification.Rejected).reason,
        )
    }

    private fun validCode(): String = codec.createCode(draft(), signer)

    private fun draft() = DeliveryOfferDraft(
        deliveryId = "DELTA-2026-0001",
        missionId = "mission-sylhet-01",
        senderIdentityId = "boat-operator-02",
        recipientIdentityId = "hospital-operator-01",
        payloadSha256 = sha256("medicine:10|ors:20".encodeToByteArray()),
        nonce = ByteArray(16) { it.toByte() },
        timestampUnixMs = now,
        previousReceiptSha256 = sha256("genesis".encodeToByteArray()),
        simulatedVehicle = true,
    )

    private fun trusted(clock: Long) = TrustedDeliveryContext(
        deliveryId = "DELTA-2026-0001",
        missionId = "mission-sylhet-01",
        senderIdentityId = "boat-operator-02",
        recipientIdentityId = "hospital-operator-01",
        payloadSha256 = sha256("medicine:10|ors:20".encodeToByteArray()),
        senderSigningKeyId = signer.keyId,
        senderSigningPublicKeyDer = signer.publicKeyDer,
        nowUnixMs = clock,
        allowedClockSkewMs = 120_000,
    )
}
