package com.example.digitaldelta.domain.pod

import com.example.digitaldelta.proto.v1.DeliveryOffer
import com.example.digitaldelta.proto.v1.Signature as ProtoSignature
import com.example.digitaldelta.proto.v1.SignedDeliveryOffer
import com.google.protobuf.ByteString
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class DeliveryOfferDraft(
    val deliveryId: String,
    val missionId: String,
    val senderIdentityId: String,
    val recipientIdentityId: String,
    val payloadSha256: ByteArray,
    val nonce: ByteArray,
    val timestampUnixMs: Long,
    val previousReceiptSha256: ByteArray,
    val simulatedVehicle: Boolean,
)

data class TrustedDeliveryContext(
    val deliveryId: String,
    val missionId: String,
    val senderIdentityId: String,
    val recipientIdentityId: String,
    val payloadSha256: ByteArray,
    val senderSigningKeyId: String,
    val senderSigningPublicKeyDer: ByteArray,
    val nowUnixMs: Long,
    val allowedClockSkewMs: Long,
    val credentialValidUntilUnixMs: Long = Long.MAX_VALUE,
    val credentialRevokedAtUnixMs: Long? = null,
)

interface DeliverySigningKey {
    val keyId: String
    val publicKeyDer: ByteArray
    fun sign(bytes: ByteArray): ByteArray
}

class RsaPssDeliverySigner(
    override val keyId: String,
    private val privateKey: PrivateKey,
    private val publicKey: PublicKey,
) : DeliverySigningKey {
    override val publicKeyDer: ByteArray = publicKey.encoded

    override fun sign(bytes: ByteArray): ByteArray = newRsaPssSignature().run {
        initSign(privateKey)
        update(bytes)
        sign()
    }

    companion object {
        fun generate(keyId: String): RsaPssDeliverySigner {
            val keys = KeyPairGenerator.getInstance("RSA").run {
                initialize(2048)
                generateKeyPair()
            }
            return RsaPssDeliverySigner(keyId, keys.private, keys.public)
        }
    }
}

enum class DeliveryOfferRejection {
    MALFORMED,
    UNKNOWN_SIGNING_KEY,
    CREDENTIAL_EXPIRED,
    CREDENTIAL_REVOKED,
    KEY_MISMATCH,
    INVALID_SIGNATURE,
    WRONG_DELIVERY,
    WRONG_MISSION,
    WRONG_SENDER,
    WRONG_RECIPIENT,
    PAYLOAD_HASH_MISMATCH,
    CLOCK_SKEW,
    PREVIOUS_RECEIPT_MISMATCH,
    REPLAY_REJECTED,
}

sealed interface DeliveryOfferVerification {
    data class Verified(val signedOffer: SignedDeliveryOffer) : DeliveryOfferVerification
    data class Rejected(val reason: DeliveryOfferRejection) : DeliveryOfferVerification
}

class DeliveryOfferCodec {
    fun createCode(draft: DeliveryOfferDraft, signer: DeliverySigningKey): String {
        validateDraft(draft)
        require(signer.keyId.isNotBlank()) { "signing key id is required" }
        val publicKey = decodeRsaPublicKey(signer.publicKeyDer)
        requireRsa2048(publicKey)
        val offer = DeliveryOffer.newBuilder()
            .setDeliveryId(draft.deliveryId)
            .setMissionId(draft.missionId)
            .setSenderIdentityId(draft.senderIdentityId)
            .setRecipientIdentityId(draft.recipientIdentityId)
            .setPayloadSha256(ByteString.copyFrom(draft.payloadSha256))
            .setNonce(ByteString.copyFrom(draft.nonce))
            .setTimestampUnixMs(draft.timestampUnixMs)
            .setPreviousReceiptSha256(ByteString.copyFrom(draft.previousReceiptSha256))
            .setSimulatedVehicle(draft.simulatedVehicle)
            .build()
        val signature = ProtoSignature.newBuilder()
            .setKeyId(signer.keyId)
            .setAlgorithm(SIGNATURE_ALGORITHM)
            .setRsa2048PssSha256(ByteString.copyFrom(signer.sign(offer.toByteArray())))
            .build()
        val signed = SignedDeliveryOffer.newBuilder()
            .setOffer(offer)
            .setSenderSigningKeyId(signer.keyId)
            .setSenderSigningPublicKeyDer(ByteString.copyFrom(signer.publicKeyDer))
            .setSenderSignature(signature)
            .build()
        return CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(signed.toByteArray())
    }

    fun decodeCode(code: String): SignedDeliveryOffer {
        require(code.length in 1..16_384) { "delivery offer is too large" }
        require(code.startsWith(CODE_PREFIX)) { "delivery offer prefix is missing" }
        return SignedDeliveryOffer.parseFrom(
            Base64.getUrlDecoder().decode(code.removePrefix(CODE_PREFIX)),
        )
    }

    /** Unverified display data only; never grants custody or selects trusted keys. */
    fun preview(code: String): DeliveryOfferReady {
        val signed = decodeCode(code)
        val offer = signed.offer
        return DeliveryOfferReady(code, offer.deliveryId, offer.senderIdentityId, offer.recipientIdentityId,
            signed.senderSigningKeyId, offer.payloadSha256.toByteArray(), offer.nonce.toByteArray(),
            offer.timestampUnixMs, offer.previousReceiptSha256.toByteArray(), offer.simulatedVehicle)
    }

    /** Deterministic fault injection for the fair demo; never used on an accepted path. */
    fun tamperRecipientForDemo(code: String, recipientIdentityId: String): String {
        val signed = decodeCode(code)
        val tampered = signed.toBuilder()
            .setOffer(signed.offer.toBuilder().setRecipientIdentityId(recipientIdentityId))
            .build()
        return CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(tampered.toByteArray())
    }

    fun verifyCode(code: String, trusted: TrustedDeliveryContext?): DeliveryOfferVerification {
        val signed = runCatching { decodeCode(code) }.getOrElse {
            return DeliveryOfferVerification.Rejected(DeliveryOfferRejection.MALFORMED)
        }
        if (trusted == null) return rejected(DeliveryOfferRejection.UNKNOWN_SIGNING_KEY)
        require(trusted.allowedClockSkewMs >= 0) { "allowed clock skew cannot be negative" }
        if (trusted.nowUnixMs < 0) return rejected(DeliveryOfferRejection.CLOCK_SKEW)
        if (trusted.credentialRevokedAtUnixMs?.let { it <= trusted.nowUnixMs } == true) {
            return rejected(DeliveryOfferRejection.CREDENTIAL_REVOKED)
        }
        if (trusted.nowUnixMs >= trusted.credentialValidUntilUnixMs) {
            return rejected(DeliveryOfferRejection.CREDENTIAL_EXPIRED)
        }
        val offer = signed.offer
        if (!MessageDigest.isEqual(
                signed.senderSigningPublicKeyDer.toByteArray(),
                trusted.senderSigningPublicKeyDer,
            ) || signed.senderSigningKeyId != trusted.senderSigningKeyId
        ) {
            return DeliveryOfferVerification.Rejected(DeliveryOfferRejection.KEY_MISMATCH)
        }
        val signature = signed.senderSignature
        if (signature.algorithm != SIGNATURE_ALGORITHM ||
            signature.keyId != signed.senderSigningKeyId ||
            signature.rsa2048PssSha256.isEmpty
        ) {
            return DeliveryOfferVerification.Rejected(DeliveryOfferRejection.INVALID_SIGNATURE)
        }
        val validSignature = runCatching {
            val publicKey = decodeRsaPublicKey(trusted.senderSigningPublicKeyDer)
            requireRsa2048(publicKey)
            newRsaPssSignature().run {
                initVerify(publicKey)
                update(offer.toByteArray())
                verify(signature.rsa2048PssSha256.toByteArray())
            }
        }.getOrDefault(false)
        if (!validSignature) {
            return DeliveryOfferVerification.Rejected(DeliveryOfferRejection.INVALID_SIGNATURE)
        }
        if (offer.deliveryId != trusted.deliveryId) return rejected(DeliveryOfferRejection.WRONG_DELIVERY)
        if (offer.missionId != trusted.missionId) return rejected(DeliveryOfferRejection.WRONG_MISSION)
        if (offer.senderIdentityId != trusted.senderIdentityId) return rejected(DeliveryOfferRejection.WRONG_SENDER)
        if (offer.recipientIdentityId != trusted.recipientIdentityId) {
            return rejected(DeliveryOfferRejection.WRONG_RECIPIENT)
        }
        if (!MessageDigest.isEqual(offer.payloadSha256.toByteArray(), trusted.payloadSha256)) {
            return rejected(DeliveryOfferRejection.PAYLOAD_HASH_MISMATCH)
        }
        if (offer.timestampUnixMs < 0) {
            return rejected(DeliveryOfferRejection.CLOCK_SKEW)
        }
        val clockDelta = if (trusted.nowUnixMs >= offer.timestampUnixMs) {
            trusted.nowUnixMs - offer.timestampUnixMs
        } else {
            offer.timestampUnixMs - trusted.nowUnixMs
        }
        if (clockDelta > trusted.allowedClockSkewMs) {
            return rejected(DeliveryOfferRejection.CLOCK_SKEW)
        }
        return DeliveryOfferVerification.Verified(signed)
    }

    private fun validateDraft(draft: DeliveryOfferDraft) {
        require(draft.deliveryId.isNotBlank()) { "delivery id is required" }
        require(draft.missionId.isNotBlank()) { "mission id is required" }
        require(draft.senderIdentityId.isNotBlank()) { "sender identity is required" }
        require(draft.recipientIdentityId.isNotBlank()) { "recipient identity is required" }
        require(draft.payloadSha256.size == SHA256_BYTES) { "payload hash must be SHA-256" }
        require(draft.nonce.size >= MINIMUM_NONCE_BYTES) { "nonce must be at least 128 bits" }
        require(draft.timestampUnixMs >= 0) { "timestamp is invalid" }
        require(draft.previousReceiptSha256.size == SHA256_BYTES) {
            "previous receipt hash must be SHA-256"
        }
    }

    private fun rejected(reason: DeliveryOfferRejection) = DeliveryOfferVerification.Rejected(reason)

    companion object {
        const val CODE_PREFIX = "DIGITALDELTA:POD:"
        const val SIGNATURE_ALGORITHM = "RSA-2048-PSS-SHA256"
        private const val SHA256_BYTES = 32
        private const val MINIMUM_NONCE_BYTES = 16
    }
}

fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

internal fun newRsaPssSignature(): Signature = runCatching {
    Signature.getInstance("RSASSA-PSS").apply {
        setParameter(PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1))
    }
}.getOrElse {
    Signature.getInstance("SHA256withRSA/PSS")
}

private fun decodeRsaPublicKey(der: ByteArray): PublicKey =
    KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))

private fun requireRsa2048(key: PublicKey) {
    require(key is RSAKey && key.modulus.bitLength() >= 2048) { "RSA key must be at least 2048 bits" }
}
