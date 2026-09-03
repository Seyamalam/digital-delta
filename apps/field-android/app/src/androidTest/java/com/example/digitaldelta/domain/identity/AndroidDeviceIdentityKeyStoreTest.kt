package com.example.digitaldelta.domain.identity

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.digitaldelta.domain.mesh.HybridPayloadCipher
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidDeviceIdentityKeyStoreTest {
    @Test
    fun matchingDeviceBoundKeyDecryptsWhileAnotherIdentityCannot() {
        val keyStore = AndroidDeviceIdentityKeyStore()
        val recipient = keyStore.createOrGet("identity-test-recipient")
        val sameRecipient = keyStore.createOrGet("identity-test-recipient")
        val other = keyStore.createOrGet("identity-test-relay")
        val plaintext = "offline P0 payload".encodeToByteArray()
        val aad = "message|sender|recipient|time".encodeToByteArray()
        val encrypted = HybridPayloadCipher().encrypt(
            recipient.encryptionKeyId,
            recipient.encryptionPublicKeyDer,
            plaintext,
            aad,
        )

        assertEquals(recipient.encryptionKeyId, sameRecipient.encryptionKeyId)
        assertArrayEquals(recipient.encryptionPublicKeyDer, sameRecipient.encryptionPublicKeyDer)
        assertNotEquals(recipient.encryptionKeyId, other.encryptionKeyId)
        assertArrayEquals(plaintext, keyStore.decrypt("identity-test-recipient", encrypted, aad))
        assertThrows(SecurityException::class.java) {
            keyStore.decrypt("identity-test-relay", encrypted, aad)
        }
    }
}
