package com.example.digitaldelta.domain.mesh

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
