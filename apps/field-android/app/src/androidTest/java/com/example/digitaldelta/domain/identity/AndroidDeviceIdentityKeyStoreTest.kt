package com.example.digitaldelta.domain.identity

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.digitaldelta.domain.mesh.AndroidMeshAcknowledgementSigner
import com.example.digitaldelta.domain.mesh.DirectoryMeshAcknowledgementVerifier
import com.example.digitaldelta.domain.mesh.HybridPayloadCipher
import com.example.digitaldelta.domain.mesh.PeerSigningIdentity
import com.example.digitaldelta.domain.mesh.PeerSigningIdentityDirectory
import com.example.digitaldelta.proto.v1.Acknowledgement
import com.example.digitaldelta.proto.v1.AcknowledgementStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import com.example.digitaldelta.domain.pod.newRsaPssSignature
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

    @Test
    fun deviceBoundSigningKeyProducesVerifiableRsaPssSignature() {
        val keyStore = AndroidDeviceIdentityKeyStore()
        val identity = keyStore.createOrGet("identity-test-pod-signer")
        val payload = "signed protobuf delivery offer".encodeToByteArray()

        val signature = keyStore.sign("identity-test-pod-signer", payload)

        val publicKey = KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(identity.signingPublicKeyDer),
        )
        val verified = newRsaPssSignature().run {
            initVerify(publicKey)
            update(payload)
            verify(signature)
        }
        assertEquals(true, verified)
    }

    @Test
    fun deviceBoundKeySignsAcknowledgementAcceptedByProvisionedPeerDirectory() = runTest {
        val now = 1_800_000_000_000L
        val keyStore = AndroidDeviceIdentityKeyStore()
        val publicIdentity = keyStore.createOrGet("identity-test-mesh-node")
        val signed = AndroidMeshAcknowledgementSigner("identity-test-mesh-node", keyStore).sign(
            Acknowledgement.newBuilder()
                .setMessageId("mesh-message-1")
                .setNodeId("identity-test-mesh-node")
                .setStatus(AcknowledgementStatus.ACKNOWLEDGEMENT_STATUS_DURABLY_STORED)
                .setRecordedAtUnixMs(now)
                .build(),
        )
        val verifier = DirectoryMeshAcknowledgementVerifier(
            PeerSigningIdentityDirectory {
                PeerSigningIdentity(
                    nodeId = "identity-test-mesh-node",
                    keyId = publicIdentity.signingKeyId,
                    publicKeyDer = publicIdentity.signingPublicKeyDer,
                    validFromUnixMs = now - 1_000,
                    validUntilUnixMs = now + 1_000,
                    revokedAtUnixMs = null,
                )
            },
        )

        assertEquals(true, verifier.verify(signed, "identity-test-mesh-node", now))
    }
}
