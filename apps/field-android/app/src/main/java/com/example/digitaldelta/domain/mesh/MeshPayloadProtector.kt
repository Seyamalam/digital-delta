package com.example.digitaldelta.domain.mesh

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ProtectedPayload(
    val recipientKeyId: String,
    val ciphertext: ByteArray,
    val nonce: ByteArray,
    val associatedDataSha256: ByteArray,
    val wrappedAes256Key: ByteArray = byteArrayOf(),
    val keyWrapAlgorithm: String = "",
    val contentAlgorithm: String = "AES-256-GCM",
)

interface MeshPayloadProtector {
    suspend fun protect(recipientNodeId: String, plaintext: ByteArray, associatedData: ByteArray): ProtectedPayload
}

class AndroidKeystorePayloadProtector : MeshPayloadProtector {
    override suspend fun protect(
        recipientNodeId: String,
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ProtectedPayload {
        require(plaintext.isNotEmpty()) { "plaintext is required" }
        val alias = keyAlias(recipientNodeId)
        val key = loadOrCreateKey(alias)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext)
        val nonce = cipher.getParameters().getParameterSpec(GCMParameterSpec::class.java).iv
        return ProtectedPayload(
            recipientKeyId = alias,
            ciphertext = ciphertext,
            nonce = nonce,
            associatedDataSha256 = sha256(associatedData),
        )
    }

    private fun loadOrCreateKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun keyAlias(recipientNodeId: String): String =
        "digital-delta-recipient-${sha256(recipientNodeId.encodeToByteArray()).toHex().take(16)}"

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

data class RecipientEncryptionKey(
    val nodeId: String,
    val keyId: String,
    val publicKeyDer: ByteArray,
    val validUntilUnixMs: Long,
    val revokedAtUnixMs: Long?,
)

interface RecipientKeyDirectory {
    suspend fun findByNodeId(nodeId: String): RecipientEncryptionKey?
}

class RecipientKeyUnavailableException(nodeId: String) :
    IllegalStateException("no active encryption key is provisioned for $nodeId")

class DirectoryBackedMeshPayloadProtector(
    private val directory: RecipientKeyDirectory,
    private val cipher: HybridPayloadCipher = HybridPayloadCipher(),
    private val nowUnixMs: () -> Long = System::currentTimeMillis,
) : MeshPayloadProtector {
    override suspend fun protect(
        recipientNodeId: String,
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ProtectedPayload {
        val now = nowUnixMs()
        val key = directory.findByNodeId(recipientNodeId)
            ?.takeIf { it.revokedAtUnixMs == null && it.validUntilUnixMs > now }
            ?: throw RecipientKeyUnavailableException(recipientNodeId)
        return cipher.encrypt(
            recipientKeyId = key.keyId,
            recipientPublicKeyDer = key.publicKeyDer,
            plaintext = plaintext,
            associatedData = associatedData,
        )
    }
}
