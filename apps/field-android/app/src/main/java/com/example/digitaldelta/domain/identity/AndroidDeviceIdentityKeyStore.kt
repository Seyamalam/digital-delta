package com.example.digitaldelta.domain.identity

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.digitaldelta.domain.mesh.HybridPayloadCipher
import com.example.digitaldelta.domain.mesh.ProtectedPayload
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

data class DevicePublicIdentity(
    val nodeId: String,
    val encryptionKeyId: String,
    val encryptionPublicKeyDer: ByteArray,
    val signingKeyId: String,
    val signingPublicKeyDer: ByteArray,
)

/** Owns non-exportable private identity keys in Android Keystore. */
class AndroidDeviceIdentityKeyStore {
    fun createOrGet(nodeId: String): DevicePublicIdentity {
        require(nodeId.isNotBlank()) { "node id is required" }
        val encryptionAlias = alias(nodeId, "encryption")
        val signingAlias = alias(nodeId, "signing")
        val keyStore = keyStore()
        if (!keyStore.containsAlias(encryptionAlias)) generateEncryptionKey(encryptionAlias)
        if (!keyStore.containsAlias(signingAlias)) generateSigningKey(signingAlias)
        val encryptionPublic = requireNotNull(keyStore().getCertificate(encryptionAlias)).publicKey.encoded
        val signingPublic = requireNotNull(keyStore().getCertificate(signingAlias)).publicKey.encoded
        return DevicePublicIdentity(
            nodeId = nodeId,
            encryptionKeyId = keyId(encryptionPublic),
            encryptionPublicKeyDer = encryptionPublic,
            signingKeyId = keyId(signingPublic),
            signingPublicKeyDer = signingPublic,
        )
    }

    fun decrypt(nodeId: String, payload: ProtectedPayload, associatedData: ByteArray): ByteArray = try {
        val identity = createOrGet(nodeId)
        require(payload.recipientKeyId == identity.encryptionKeyId) { "payload targets a different key" }
        require(payload.keyWrapAlgorithm == HybridPayloadCipher.KEY_WRAP_ALGORITHM) {
            "unsupported key-wrap algorithm"
        }
        require(payload.contentAlgorithm == HybridPayloadCipher.CONTENT_ALGORITHM) {
            "unsupported content algorithm"
        }
        require(MessageDigest.isEqual(payload.associatedDataSha256, sha256(associatedData))) {
            "associated data does not match"
        }
        val privateKey = keyStore().getKey(alias(nodeId, "encryption"), null) as PrivateKey
        val contentKeyBytes = Cipher.getInstance("RSA/ECB/OAEPPadding").run {
            init(
                Cipher.DECRYPT_MODE,
                privateKey,
                OAEPParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA1,
                    PSource.PSpecified.DEFAULT,
                ),
            )
            doFinal(payload.wrappedAes256Key)
        }
        try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(contentKeyBytes, "AES"),
                    GCMParameterSpec(128, payload.nonce),
                )
                updateAAD(associatedData)
                doFinal(payload.ciphertext)
            }
        } finally {
            contentKeyBytes.fill(0)
        }
    } catch (error: Exception) {
        throw SecurityException("device could not decrypt payload", error)
    }

    private fun generateEncryptionKey(alias: String) {
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").run {
            initialize(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(2048)
                    .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                    .build(),
            )
            generateKeyPair()
        }
    }

    private fun generateSigningKey(alias: String) {
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").run {
            initialize(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                )
                    .setKeySize(2048)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build(),
            )
            generateKeyPair()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun alias(nodeId: String, purpose: String): String =
        "digital-delta-$purpose-${sha256(nodeId.encodeToByteArray()).toHex().take(24)}"

    private fun keyId(publicKeyDer: ByteArray): String = "rsa-${sha256(publicKeyDer).toHex().take(24)}"

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
