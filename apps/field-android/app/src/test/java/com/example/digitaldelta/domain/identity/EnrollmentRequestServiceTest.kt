package com.example.digitaldelta.domain.identity

import com.example.digitaldelta.proto.v1.IdentityEnrollmentRequest
import com.example.digitaldelta.proto.v1.IdentityRole
import java.security.KeyPairGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EnrollmentRequestServiceTest {
    @Test
    fun `creates a language-neutral protobuf enrollment request for offline QR transfer`() {
        val encryptionKey = rsaKeyPair()
        val signingKey = rsaKeyPair()
        val identity = DevicePublicIdentity(
            nodeId = "N4",
            encryptionKeyId = "enc-key-1",
            encryptionPublicKeyDer = encryptionKey.public.encoded,
            signingKeyId = "sign-key-1",
            signingPublicKeyDer = signingKey.public.encoded,
        )

        val encoded = EnrollmentRequestService().create(
            identityId = "clinic-operator-1",
            displayName = "Companyganj Outpost",
            role = IdentityRole.IDENTITY_ROLE_CLINIC,
            publicIdentity = identity,
            createdAtUnixMs = 1_800_000_000_000,
            nonce = ByteArray(16) { 7 },
        )

        val decoded = IdentityEnrollmentRequest.parseFrom(encoded)
        assertEquals("N4", decoded.nodeId)
        assertEquals("enc-key-1", decoded.encryptionKeyId)
        assertEquals(IdentityRole.IDENTITY_ROLE_CLINIC, decoded.role)
        assertFalse(encoded.contentEquals(decoded.toString().encodeToByteArray()))
    }

    private fun rsaKeyPair() = KeyPairGenerator.getInstance("RSA").run {
        initialize(2048)
        generateKeyPair()
    }
}
