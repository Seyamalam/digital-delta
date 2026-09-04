package com.example.digitaldelta.domain.mesh

import com.example.digitaldelta.proto.v1.Acknowledgement
import com.example.digitaldelta.proto.v1.AcknowledgementStatus
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshAcknowledgementSecurityTest {
    private val now = 1_800_000_000_000L

    @Test
    fun `provisioned peer signature authenticates exact acknowledgement`() = runTest {
        val keys = KeyPairGenerator.getInstance("RSA").run {
            initialize(2048)
            generateKeyPair()
        }
        val identity = PeerSigningIdentity(
            nodeId = "B",
            keyId = "b-signing-1",
            publicKeyDer = keys.public.encoded,
            validFromUnixMs = now - 1_000,
            validUntilUnixMs = now + 60_000,
            revokedAtUnixMs = null,
        )
        val unsigned = acknowledgement()
        val signed = MeshAcknowledgementSecurity.sign(
            acknowledgement = unsigned,
            keyId = identity.keyId,
            signBytes = { payload -> rsaPss().run { initSign(keys.private); update(payload); sign() } },
        )
        val verifier = DirectoryMeshAcknowledgementVerifier(
            directory = PeerSigningIdentityDirectory { nodeId -> identity.takeIf { it.nodeId == nodeId } },
        )

        assertTrue(verifier.verify(signed, expectedNodeId = "B", nowUnixMs = now))
        assertFalse(
            verifier.verify(
                signed.toBuilder()
                    .setStatus(AcknowledgementStatus.ACKNOWLEDGEMENT_STATUS_REJECTED)
                    .setReasonCode("TAMPERED")
                    .build(),
                expectedNodeId = "B",
                nowUnixMs = now,
            ),
        )
        assertFalse(verifier.verify(signed, expectedNodeId = "attacker", nowUnixMs = now))
    }

    @Test
    fun `unsigned stale unknown and revoked acknowledgements fail closed`() = runTest {
        val keys = KeyPairGenerator.getInstance("RSA").run {
            initialize(2048)
            generateKeyPair()
        }
        val valid = PeerSigningIdentity("B", "key-1", keys.public.encoded, now - 1_000, now + 1_000, null)
        var directoryIdentity: PeerSigningIdentity? = valid
        val verifier = DirectoryMeshAcknowledgementVerifier(
            directory = PeerSigningIdentityDirectory { directoryIdentity },
        )
        val signed = MeshAcknowledgementSecurity.sign(acknowledgement(), valid.keyId) { payload ->
            rsaPss().run { initSign(keys.private); update(payload); sign() }
        }

        assertFalse(verifier.verify(acknowledgement(), "B", now))
        assertFalse(verifier.verify(signed, "B", now + MeshAcknowledgementSecurity.MAX_CLOCK_SKEW_MILLIS + 1))
        directoryIdentity = null
        assertFalse(verifier.verify(signed, "B", now))
        directoryIdentity = valid.copy(revokedAtUnixMs = now - 1)
        assertFalse(verifier.verify(signed, "B", now))
    }

    private fun acknowledgement(): Acknowledgement = Acknowledgement.newBuilder()
        .setMessageId("message-1")
        .setNodeId("B")
        .setStatus(AcknowledgementStatus.ACKNOWLEDGEMENT_STATUS_DURABLY_STORED)
        .setRecordedAtUnixMs(now)
        .build()

    private fun rsaPss(): Signature = Signature.getInstance("RSASSA-PSS").apply {
        setParameter(PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1))
    }
}
