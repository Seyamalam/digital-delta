package com.example.digitaldelta.domain.identity

import com.example.digitaldelta.proto.v1.IdentityProvisioningClaims
import com.example.digitaldelta.proto.v1.IdentityRole
import java.security.KeyPairGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProvisioningCredentialServiceTest {
    @Test
    fun `offline credential verifies issuer signature and validity window`() {
        val issuer = rsaKeyPair()
        val recipient = rsaKeyPair()
        val service = ProvisioningCredentialService()
        val claims = IdentityProvisioningClaims.newBuilder()
            .setCredentialId("credential-1")
            .setIdentityId("hospital-operator-1")
            .setNodeId("N6")
            .setDisplayName("Habiganj Medical")
            .setRole(IdentityRole.IDENTITY_ROLE_HOSPITAL)
            .setEncryptionKeyId("n6-encryption-1")
            .setRsa2048EncryptionPublicKeyDer(com.google.protobuf.ByteString.copyFrom(recipient.public.encoded))
            .setSigningKeyId("n6-signing-1")
            .setRsa2048SigningPublicKeyDer(com.google.protobuf.ByteString.copyFrom(recipient.public.encoded))
            .setIssuedAtUnixMs(1_800_000_000_000)
            .setExpiresAtUnixMs(1_900_000_000_000)
            .setIssuerIdentityId("delta-admin-1")
            .build()

        val credentialBytes = service.issue(
            claims = claims,
            issuerKeyId = "admin-signing-1",
            issuerPrivateKeyDer = issuer.private.encoded,
        )

        val verified = service.verify(
            credentialBytes = credentialBytes,
            trustedIssuerPublicKeyDer = issuer.public.encoded,
            nowUnixMs = 1_850_000_000_000,
        )
        assertEquals("N6", verified.nodeId)
        assertEquals("n6-encryption-1", verified.encryptionKeyId)
    }

    @Test
    fun `offline credential rejects tampering expiry and an untrusted issuer`() {
        val issuer = rsaKeyPair()
        val otherIssuer = rsaKeyPair()
        val recipient = rsaKeyPair()
        val service = ProvisioningCredentialService()
        val claims = IdentityProvisioningClaims.newBuilder()
            .setCredentialId("credential-1")
            .setIdentityId("hospital-operator-1")
            .setNodeId("N6")
            .setDisplayName("Habiganj Medical")
            .setRole(IdentityRole.IDENTITY_ROLE_HOSPITAL)
            .setEncryptionKeyId("n6-encryption-1")
            .setRsa2048EncryptionPublicKeyDer(com.google.protobuf.ByteString.copyFrom(recipient.public.encoded))
            .setSigningKeyId("n6-signing-1")
            .setRsa2048SigningPublicKeyDer(com.google.protobuf.ByteString.copyFrom(recipient.public.encoded))
            .setIssuedAtUnixMs(1_800_000_000_000)
            .setExpiresAtUnixMs(1_900_000_000_000)
            .setIssuerIdentityId("delta-admin-1")
            .build()
        val credential = service.issue(claims, "admin-signing-1", issuer.private.encoded)

        assertThrows(ProvisioningCredentialException::class.java) {
            service.verify(credential, otherIssuer.public.encoded, 1_850_000_000_000)
        }
        assertThrows(ProvisioningCredentialException::class.java) {
            service.verify(credential, issuer.public.encoded, 1_950_000_000_000)
        }
        val tampered = credential.copyOf().also { it[it.lastIndex / 2] = (it[it.lastIndex / 2].toInt() xor 1).toByte() }
        assertThrows(ProvisioningCredentialException::class.java) {
            service.verify(tampered, issuer.public.encoded, 1_850_000_000_000)
        }
    }

    private fun rsaKeyPair() = KeyPairGenerator.getInstance("RSA").run {
        initialize(2048)
        generateKeyPair()
    }
}
