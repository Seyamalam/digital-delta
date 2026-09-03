package com.example.digitaldelta.domain.mesh

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * Hybrid encryption for mesh payloads. The payload is protected with a fresh AES-256-GCM key;
 * only the final recipient can unwrap that key with its RSA-2048 private key.
 */
class HybridPayloadCipher(
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    fun encrypt(
        recipientKeyId: String,
        recipientPublicKeyDer: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ProtectedPayload {
        require(recipientKeyId.isNotBlank()) { "recipient key id is required" }
        require(plaintext.isNotEmpty()) { "plaintext is required" }
        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(recipientPublicKeyDer))
        require(publicKey.algorithm == "RSA") { "recipient key must be RSA" }

        val contentKeyBytes = ByteArray(AES_KEY_BYTES).also(secureRandom::nextBytes)
        val nonce = ByteArray(GCM_NONCE_BYTES).also(secureRandom::nextBytes)
        val ciphertext = Cipher.getInstance(CONTENT_TRANSFORMATION).run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(contentKeyBytes, "AES"), GCMParameterSpec(128, nonce))
            updateAAD(associatedData)
            doFinal(plaintext)
        }
        val wrappedKey = rsaOaepCipher(Cipher.ENCRYPT_MODE, publicKey).doFinal(contentKeyBytes)
        contentKeyBytes.fill(0)

        return ProtectedPayload(
            recipientKeyId = recipientKeyId,
            ciphertext = ciphertext,
            nonce = nonce,
            associatedDataSha256 = sha256(associatedData),
            wrappedAes256Key = wrappedKey,
            keyWrapAlgorithm = KEY_WRAP_ALGORITHM,
            contentAlgorithm = CONTENT_ALGORITHM,
        )
    }

    fun decrypt(
        payload: ProtectedPayload,
        recipientPrivateKeyDer: ByteArray,
        associatedData: ByteArray,
    ): ByteArray = try {
        require(payload.keyWrapAlgorithm == KEY_WRAP_ALGORITHM) { "unsupported key-wrap algorithm" }
        require(payload.contentAlgorithm == CONTENT_ALGORITHM) { "unsupported content algorithm" }
        require(MessageDigest.isEqual(payload.associatedDataSha256, sha256(associatedData))) {
            "associated data does not match"
        }
        val privateKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(recipientPrivateKeyDer))
        val contentKeyBytes = rsaOaepCipher(Cipher.DECRYPT_MODE, privateKey).doFinal(payload.wrappedAes256Key)
        try {
            Cipher.getInstance(CONTENT_TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(contentKeyBytes, "AES"), GCMParameterSpec(128, payload.nonce))
                updateAAD(associatedData)
                doFinal(payload.ciphertext)
            }
        } finally {
            contentKeyBytes.fill(0)
        }
    } catch (error: Exception) {
        throw SecurityException("encrypted payload verification failed", error)
    }

    private fun rsaOaepCipher(mode: Int, key: java.security.Key): Cipher =
        Cipher.getInstance(RSA_TRANSFORMATION).apply {
            init(
                mode,
                key,
                OAEPParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA1,
                    PSource.PSpecified.DEFAULT,
                ),
            )
        }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    companion object {
        const val KEY_WRAP_ALGORITHM = "RSA-2048-OAEP-SHA256-MGF1-SHA1"
        const val CONTENT_ALGORITHM = "AES-256-GCM"
        private const val RSA_TRANSFORMATION = "RSA/ECB/OAEPPadding"
        private const val CONTENT_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_KEY_BYTES = 32
        private const val GCM_NONCE_BYTES = 12
    }
}
