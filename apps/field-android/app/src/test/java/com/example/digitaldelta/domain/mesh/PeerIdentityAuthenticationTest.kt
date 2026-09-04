package com.example.digitaldelta.domain.mesh

import com.example.digitaldelta.domain.identity.ProvisioningCredentialService
import com.example.digitaldelta.proto.v1.IdentityProvisioningClaims
import com.example.digitaldelta.proto.v1.IdentityProvisioningCredential
import com.example.digitaldelta.proto.v1.IdentityRole
import com.google.protobuf.ByteString
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerIdentityAuthenticationTest {
    private val now = 1_800_000_000_000L

    @Test
    fun `administrator provisioned peer proves its identity for a fresh challenge`() {
        val administrator = rsaKeys()
        val peer = rsaKeys()
        val credential = credential(administrator, peer)
        val challenge = PeerIdentityAuthentication.newChallenge(
            challengerNodeId = "N4",
            nowUnixMs = now,
            nonce = ByteArray(32) { it.toByte() },
        )
        val proof = PeerIdentityAuthentication.createProof(
            challenge = challenge,
            proverNodeId = "N6",
            credential = credential,
            signedAtUnixMs = now + 1,
            signingKeyId = "n6-signing-1",
            signBytes = { payload -> rsaPss().run { initSign(peer.private); update(payload); sign() } },
        )

        assertTrue(
            PeerIdentityAuthentication.verifyProof(
                proof = proof,
                expectedChallenge = challenge,
                expectedPeerNodeId = "N6",
                trustedIssuerPublicKeyDer = administrator.public.encoded,
                nowUnixMs = now + 2,
            ),
        )
        assertFalse(
            PeerIdentityAuthentication.verifyProof(
                proof.toBuilder().setProverNodeId("attacker").build(),
                challenge,
                "N6",
                administrator.public.encoded,
                now + 2,
            ),
        )
    }

    @Test
    fun `changed stale unknown-key and replayed proofs fail closed`() {
        val administrator = rsaKeys()
        val peer = rsaKeys()
        val credential = credential(administrator, peer)
        val challenge = PeerIdentityAuthentication.newChallenge(
            "N4",
            now,
            ByteArray(32) { (it + 4).toByte() },
        )
        val proof = PeerIdentityAuthentication.createProof(
            challenge,
            "N6",
            credential,
            now,
            "n6-signing-1",
        ) { payload -> rsaPss().run { initSign(peer.private); update(payload); sign() } }
        val registry = PendingPeerChallenges()
        registry.put("endpoint-6", challenge)

        assertFalse(
            PeerIdentityAuthentication.verifyProof(
                proof.toBuilder().setSignedAtUnixMs(now + 5).build(),
                challenge,
                "N6",
                administrator.public.encoded,
                now,
            ),
        )
        assertFalse(
            PeerIdentityAuthentication.verifyProof(
                proof.toBuilder().setNodeSignature(proof.nodeSignature.toBuilder().setKeyId("unknown")).build(),
                challenge,
                "N6",
                administrator.public.encoded,
                now,
            ),
        )
        assertFalse(
            PeerIdentityAuthentication.verifyProof(
                proof,
                challenge,
                "N6",
                administrator.public.encoded,
                now + PeerIdentityAuthentication.CHALLENGE_TTL_MILLIS + 1,
            ),
        )
        assertTrue(registry.consume("endpoint-6", challenge))
        assertFalse(registry.consume("endpoint-6", challenge))
    }

    private fun credential(
        administrator: java.security.KeyPair,
        peer: java.security.KeyPair,
    ): IdentityProvisioningCredential {
        val claims = IdentityProvisioningClaims.newBuilder()
            .setCredentialId("credential-n6")
            .setIdentityId("hospital-n6")
            .setNodeId("N6")
            .setDisplayName("Habiganj Medical")
            .setRole(IdentityRole.IDENTITY_ROLE_HOSPITAL)
            .setEncryptionKeyId("n6-encryption-1")
            .setRsa2048EncryptionPublicKeyDer(ByteString.copyFrom(peer.public.encoded))
            .setSigningKeyId("n6-signing-1")
            .setRsa2048SigningPublicKeyDer(ByteString.copyFrom(peer.public.encoded))
            .setIssuedAtUnixMs(now - 1_000)
            .setExpiresAtUnixMs(now + 60_000)
            .setIssuerIdentityId("delta-admin-1")
            .setNonce(ByteString.copyFrom(ByteArray(16) { 7 }))
            .build()
        return ProvisioningCredentialService().issue(
            claims,
            issuerKeyId = "admin-signing-1",
            issuerPrivateKeyDer = administrator.private.encoded,
        ).let(IdentityProvisioningCredential::parseFrom)
    }

    private fun rsaKeys() = KeyPairGenerator.getInstance("RSA").run {
        initialize(2048)
        generateKeyPair()
    }

    private fun rsaPss(): Signature = Signature.getInstance("RSASSA-PSS").apply {
        setParameter(PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1))
    }
}
