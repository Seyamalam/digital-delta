package com.example.digitaldelta.domain.pod

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.util.Base64
import kotlin.math.abs

data class UnsignedHandoff(
    val deliveryId: String,
    val senderId: String,
    val recipientId: String,
    val payloadSha256: String,
    val nonce: String,
    val timestampMillis: Long,
    val previousReceiptHash: String,
) {
    fun canonicalBytes(): ByteArray = listOf(
        deliveryId,
        senderId,
        recipientId,
        payloadSha256,
        nonce,
        timestampMillis.toString(),
        previousReceiptHash,
    ).joinToString(separator = "\u001f").toByteArray(Charsets.UTF_8)
}

data class SignedHandoff(
    val unsigned: UnsignedHandoff,
    val senderPublicKey: String,
    val signature: ByteArray,
) {
    fun receiptHash(): String = DeliverySigner.sha256(
        unsigned.canonicalBytes() + Base64.getDecoder().decode(senderPublicKey) + signature,
    )

    override fun equals(other: Any?): Boolean = other is SignedHandoff &&
        unsigned == other.unsigned &&
        senderPublicKey == other.senderPublicKey &&
        signature.contentEquals(other.signature)

    override fun hashCode(): Int = 31 * (31 * unsigned.hashCode() + senderPublicKey.hashCode()) +
        signature.contentHashCode()
}

class DeliverySigner(
    private val privateKey: PrivateKey,
    private val publicKey: PublicKey,
) {
    fun sign(handoff: UnsignedHandoff): SignedHandoff {
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(privateKey)
            update(handoff.canonicalBytes())
            sign()
        }
        return SignedHandoff(
            unsigned = handoff,
            senderPublicKey = Base64.getEncoder().encodeToString(publicKey.encoded),
            signature = signature,
        )
    }

    companion object {
        private const val SIGNATURE_ALGORITHM = "Ed25519"

        fun generateKeyPair(): KeyPair =
            KeyPairGenerator.getInstance(SIGNATURE_ALGORITHM).generateKeyPair()

        fun sha256(value: String): String = sha256(value.toByteArray(Charsets.UTF_8))

        fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(value)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

interface NonceStore {
    fun contains(nonce: String): Boolean
    fun record(nonce: String)
}

class InMemoryNonceStore : NonceStore {
    private val nonces = linkedSetOf<String>()

    override fun contains(nonce: String): Boolean = nonce in nonces

    override fun record(nonce: String) {
        nonces += nonce
    }
}

enum class VerificationResult {
    VERIFIED,
    INVALID_SIGNATURE,
    REPLAY_REJECTED,
    CLOCK_SKEW_REJECTED,
    KEY_MISMATCH,
}

class ProofOfDeliveryVerifier(
    private val nonceStore: NonceStore,
    private val allowedClockSkewMillis: Long,
) {
    init {
        require(allowedClockSkewMillis >= 0)
    }

    fun verify(
        handoff: SignedHandoff,
        expectedPublicKey: PublicKey,
        nowMillis: Long,
    ): VerificationResult {
        val expectedEncoded = Base64.getEncoder().encodeToString(expectedPublicKey.encoded)
        if (handoff.senderPublicKey != expectedEncoded) return VerificationResult.KEY_MISMATCH
        if (abs(nowMillis - handoff.unsigned.timestampMillis) > allowedClockSkewMillis) {
            return VerificationResult.CLOCK_SKEW_REJECTED
        }
        val validSignature = Signature.getInstance("Ed25519").run {
            initVerify(expectedPublicKey)
            update(handoff.unsigned.canonicalBytes())
            verify(handoff.signature)
        }
        if (!validSignature) return VerificationResult.INVALID_SIGNATURE
        if (nonceStore.contains(handoff.unsigned.nonce)) return VerificationResult.REPLAY_REJECTED
        nonceStore.record(handoff.unsigned.nonce)
        return VerificationResult.VERIFIED
    }
}
