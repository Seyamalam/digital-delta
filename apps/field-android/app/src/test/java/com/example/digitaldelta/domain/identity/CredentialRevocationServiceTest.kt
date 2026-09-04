package com.example.digitaldelta.domain.identity

import com.example.digitaldelta.proto.v1.CredentialRevocationClaims
import java.security.KeyPairGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CredentialRevocationServiceTest {
    @Test
    fun `signed revocation verifies offline and binds target credential`() {
        val issuer = rsaKeyPair()
        val service = CredentialRevocationService()
        val claims = CredentialRevocationClaims.newBuilder()
            .setRevocationId("revocation-1")
            .setCredentialId("credential-n4")
            .setIdentityId("clinic-sylhet-01")
            .setNodeId("N4")
            .setRevokedAtUnixMs(1_800_000_000_000)
            .setReasonCode("DEVICE_LOST")
            .setIssuerIdentityId("delta-admin-1")
            .setNonce(com.google.protobuf.ByteString.copyFrom(ByteArray(16) { it.toByte() }))
            .build()

        val encoded = service.issue(claims, "admin-signing-1", issuer.private.encoded)
        val verified = service.verify(encoded, issuer.public.encoded, 1_800_000_001_000)

        assertEquals("credential-n4", verified.credentialId)
        assertEquals("N4", verified.nodeId)
    }

    @Test
    fun `revocation rejects mutation wrong issuer malformed claims and future time`() {
        val issuer = rsaKeyPair()
        val otherIssuer = rsaKeyPair()
        val service = CredentialRevocationService()
        val claims = CredentialRevocationClaims.newBuilder()
            .setRevocationId("revocation-1")
            .setCredentialId("credential-n4")
            .setIdentityId("clinic-sylhet-01")
            .setNodeId("N4")
            .setRevokedAtUnixMs(1_800_000_000_000)
            .setReasonCode("DEVICE_LOST")
            .setIssuerIdentityId("delta-admin-1")
            .setNonce(com.google.protobuf.ByteString.copyFrom(ByteArray(16) { it.toByte() }))
            .build()
        val encoded = service.issue(claims, "admin-signing-1", issuer.private.encoded)

        assertThrows(ProvisioningCredentialException::class.java) {
            service.verify(encoded, otherIssuer.public.encoded, 1_800_000_001_000)
        }
        val tampered = encoded.copyOf().also { it[it.lastIndex / 2] = (it[it.lastIndex / 2].toInt() xor 1).toByte() }
        assertThrows(ProvisioningCredentialException::class.java) {
            service.verify(tampered, issuer.public.encoded, 1_800_000_001_000)
        }
        assertThrows(ProvisioningCredentialException::class.java) {
            service.verify(encoded, issuer.public.encoded, 1_799_999_000_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.issue(claims.toBuilder().clearReasonCode().build(), "admin-signing-1", issuer.private.encoded)
        }
    }

    private fun rsaKeyPair() = KeyPairGenerator.getInstance("RSA").run {
        initialize(2048)
        generateKeyPair()
    }
}
