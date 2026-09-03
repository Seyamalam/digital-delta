package com.example.digitaldelta.domain.mesh

import java.security.KeyPairGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HybridPayloadCipherTest {
    @Test
    fun `only matching recipient private key opens an authenticated payload`() {
        val recipient = rsaKeyPair()
        val unrelatedRelay = rsaKeyPair()
        val cipher = HybridPayloadCipher()
        val plaintext = "P0 medicine request for N6".encodeToByteArray()
        val associatedData = "message-1|clinic-1|N6|1800000000000".encodeToByteArray()

        val encrypted = cipher.encrypt(
            recipientKeyId = "n6-rsa-key-1",
            recipientPublicKeyDer = recipient.public.encoded,
            plaintext = plaintext,
            associatedData = associatedData,
        )

        assertEquals("n6-rsa-key-1", encrypted.recipientKeyId)
        assertEquals(HybridPayloadCipher.KEY_WRAP_ALGORITHM, encrypted.keyWrapAlgorithm)
        assertEquals(HybridPayloadCipher.CONTENT_ALGORITHM, encrypted.contentAlgorithm)
        assertNotEquals(plaintext.toList(), encrypted.ciphertext.toList())
        assertArrayEquals(
            plaintext,
            cipher.decrypt(encrypted, recipient.private.encoded, associatedData),
        )
        assertThrows(SecurityException::class.java) {
            cipher.decrypt(encrypted, unrelatedRelay.private.encoded, associatedData)
        }
        assertThrows(SecurityException::class.java) {
            cipher.decrypt(encrypted, recipient.private.encoded, "tampered".encodeToByteArray())
        }
    }

    private fun rsaKeyPair() = KeyPairGenerator.getInstance("RSA").run {
        initialize(2048)
        generateKeyPair()
    }
}
