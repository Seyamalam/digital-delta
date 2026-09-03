package com.example.digitaldelta.domain.mesh

import java.security.KeyPairGenerator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DirectoryBackedMeshPayloadProtectorTest {
    @Test
    fun `protect resolves the active recipient key and produces recipient-only ciphertext`() = runTest {
        val recipient = KeyPairGenerator.getInstance("RSA").run {
            initialize(2048)
            generateKeyPair()
        }
        val key = RecipientEncryptionKey(
            nodeId = "hospital-n6",
            keyId = "hospital-n6-key-7",
            publicKeyDer = recipient.public.encoded,
            validUntilUnixMs = 1_900_000_000_000,
            revokedAtUnixMs = null,
        )
        val cipher = HybridPayloadCipher()
        val protector = DirectoryBackedMeshPayloadProtector(
            directory = SingleKeyDirectory(key),
            cipher = cipher,
            nowUnixMs = { 1_800_000_000_000 },
        )
        val plaintext = "medical request".encodeToByteArray()
        val aad = "message metadata".encodeToByteArray()

        val protected = protector.protect("hospital-n6", plaintext, aad)

        assertEquals(key.keyId, protected.recipientKeyId)
        assertArrayEquals(plaintext, cipher.decrypt(protected, recipient.private.encoded, aad))
    }

    @Test
    fun `protect rejects missing expired and revoked recipient keys`() = runTest {
        val now = 1_800_000_000_000
        val missing = DirectoryBackedMeshPayloadProtector(EmptyKeyDirectory, nowUnixMs = { now })
        assertThrows(RecipientKeyUnavailableException::class.java) {
            kotlinx.coroutines.runBlocking { missing.protect("unknown", byteArrayOf(1), byteArrayOf(2)) }
        }

        val generated = KeyPairGenerator.getInstance("RSA").run {
            initialize(2048)
            generateKeyPair()
        }
        listOf(
            RecipientEncryptionKey("hospital", "expired", generated.public.encoded, now - 1, null),
            RecipientEncryptionKey("hospital", "revoked", generated.public.encoded, now + 1, now - 1),
        ).forEach { unusable ->
            val protector = DirectoryBackedMeshPayloadProtector(
                SingleKeyDirectory(unusable),
                nowUnixMs = { now },
            )
            assertThrows(RecipientKeyUnavailableException::class.java) {
                kotlinx.coroutines.runBlocking {
                    protector.protect("hospital", byteArrayOf(1), byteArrayOf(2))
                }
            }
        }
    }
}

private class SingleKeyDirectory(private val key: RecipientEncryptionKey) : RecipientKeyDirectory {
    override suspend fun findByNodeId(nodeId: String): RecipientEncryptionKey? =
        key.takeIf { it.nodeId == nodeId }
}

private object EmptyKeyDirectory : RecipientKeyDirectory {
    override suspend fun findByNodeId(nodeId: String): RecipientEncryptionKey? = null
}
