package com.example.digitaldelta.domain.identity

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.domain.mesh.MeshAcknowledgementSigner
import com.example.digitaldelta.domain.mesh.MeshWireCodec
import com.example.digitaldelta.domain.mesh.RoomMeshIngress
import com.example.digitaldelta.proto.v1.CredentialRevocationClaims
import com.example.digitaldelta.proto.v1.IdentityProvisioningClaims
import com.example.digitaldelta.proto.v1.IdentityRole
import com.google.protobuf.ByteString
import java.security.KeyPairGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialRevocationPropagationTest {
    private lateinit var source: DeltaDatabase
    private lateinit var destination: DeltaDatabase

    @Before
    fun createDatabases() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        source = Room.inMemoryDatabaseBuilder(context, DeltaDatabase::class.java).allowMainThreadQueries().build()
        destination = Room.inMemoryDatabaseBuilder(context, DeltaDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabases() {
        source.close()
        destination.close()
    }

    @Test
    fun signedRevocationIsEncryptedForwardedAppliedOnceAndTamperRejected() = runTest {
        val issuer = rsaKeyPair()
        val deviceKeys = AndroidDeviceIdentityKeyStore()
        val n6 = deviceKeys.createOrGet("N6")
        val n4 = rsaKeyPair()
        val credentialService = ProvisioningCredentialService()
        val n4Credential = credentialService.issue(
            identityClaims(
                credentialId = "credential-n4-propagation",
                identityId = "clinic-sylhet-01",
                nodeId = "N4",
                role = IdentityRole.IDENTITY_ROLE_CLINIC,
                encryptionKeyId = "n4-encryption",
                encryptionPublicKey = n4.public.encoded,
                signingKeyId = "n4-signing",
                signingPublicKey = n4.public.encoded,
            ),
            "admin-signing-1",
            issuer.private.encoded,
        )
        val n6Credential = credentialService.issue(
            identityClaims(
                credentialId = "credential-n6-propagation",
                identityId = "hospital-habiganj-01",
                nodeId = "N6",
                role = IdentityRole.IDENTITY_ROLE_HOSPITAL,
                encryptionKeyId = n6.encryptionKeyId,
                encryptionPublicKey = n6.encryptionPublicKeyDer,
                signingKeyId = n6.signingKeyId,
                signingPublicKey = n6.signingPublicKeyDer,
            ),
            "admin-signing-1",
            issuer.private.encoded,
        )
        val sourceRepository = RecipientProvisioningRepository(source.recipientKeyDao())
        val destinationRepository = RecipientProvisioningRepository(destination.recipientKeyDao())
        listOf(sourceRepository, destinationRepository).forEach { repository ->
            repository.accept(n4Credential, issuer.public.encoded, nowUnixMs = 200)
            repository.accept(n6Credential, issuer.public.encoded, nowUnixMs = 200)
        }
        val signedRevocation = CredentialRevocationService().issue(
            CredentialRevocationClaims.newBuilder()
                .setRevocationId("revocation-propagation-n4")
                .setCredentialId("credential-n4-propagation")
                .setIdentityId("clinic-sylhet-01")
                .setNodeId("N4")
                .setRevokedAtUnixMs(300)
                .setReasonCode("DEVICE_LOST")
                .setIssuerIdentityId("delta-admin-1")
                .setNonce(ByteString.copyFrom(ByteArray(16) { it.toByte() }))
                .build(),
            "admin-signing-1",
            issuer.private.encoded,
        )
        val receipt = sourceRepository.acceptRevocation(signedRevocation, issuer.public.encoded, nowUnixMs = 400)
        val propagator = RoomCredentialRevocationPropagator(source, nowUnixMs = { 500 })

        assertEquals(2, propagator.propagate(signedRevocation, receipt, senderNodeId = "N1"))
        val outgoing = source.outboxDao().pending(500, 10).first {
            MeshWireCodec.decode(it.wireBytes).recipientNodeId == "N6"
        }
        val envelope = MeshWireCodec.decode(outgoing.wireBytes)
        assertEquals("N6", envelope.recipientNodeId)
        assertNotNull(source.operationLogDao().find("revocation-propagation-n4"))

        RoomMeshIngress(
            destination,
            localNodeId = "N6",
            // This fixture isolates persistence/inner revocation checks from envelope authentication.
            envelopeVerifier = com.example.digitaldelta.domain.mesh.EnvelopeVerifier { _, _ -> true },
            acknowledgementSigner = MeshAcknowledgementSigner { it },
            nowUnixMs = { 600 },
        ).receive(outgoing.wireBytes)
        val processor = CredentialRevocationInboxProcessor(
            database = destination,
            deviceKeys = deviceKeys,
            recipients = destinationRepository,
            trustAnchors = FakeTrustAnchorRepository(issuer.public.encoded),
            propagator = object : CredentialRevocationPropagator {
                override suspend fun propagate(
                    revocationBytes: ByteArray,
                    receipt: RevocationReceipt,
                    senderNodeId: String,
                    excludedNodeIds: Set<String>,
                ) = 0
            },
            nowUnixMs = { 700 },
        )

        assertEquals(InboxApplicationBatch(1, 0, 0, 0), processor.process("N6"))
        assertEquals(300L, destinationRepository.installedIdentity("N4")?.revokedAtUnixMs)
        assertEquals("APPLIED", destination.inboxApplicationDao().find(envelope.messageId)?.state)
        assertEquals(InboxApplicationBatch(0, 0, 0, 0), processor.process("N6"))

        val tampered = envelope.toBuilder().setMessageId("tampered-revocation").build()
        RoomMeshIngress(
            destination,
            localNodeId = "N6",
            // This fixture isolates persistence/inner revocation checks from envelope authentication.
            envelopeVerifier = com.example.digitaldelta.domain.mesh.EnvelopeVerifier { _, _ -> true },
            acknowledgementSigner = MeshAcknowledgementSigner { it },
            nowUnixMs = { 800 },
        ).receive(MeshWireCodec.encode(tampered))
        assertEquals(InboxApplicationBatch(0, 1, 0, 0), processor.process("N6"))
        assertEquals("REJECTED", destination.inboxApplicationDao().find("tampered-revocation")?.state)
    }

    private fun identityClaims(
        credentialId: String,
        identityId: String,
        nodeId: String,
        role: IdentityRole,
        encryptionKeyId: String,
        encryptionPublicKey: ByteArray,
        signingKeyId: String,
        signingPublicKey: ByteArray,
    ) = IdentityProvisioningClaims.newBuilder()
        .setCredentialId(credentialId)
        .setIdentityId(identityId)
        .setNodeId(nodeId)
        .setDisplayName(identityId)
        .setRole(role)
        .setEncryptionKeyId(encryptionKeyId)
        .setRsa2048EncryptionPublicKeyDer(ByteString.copyFrom(encryptionPublicKey))
        .setSigningKeyId(signingKeyId)
        .setRsa2048SigningPublicKeyDer(ByteString.copyFrom(signingPublicKey))
        .setIssuedAtUnixMs(100)
        .setExpiresAtUnixMs(10_000)
        .setIssuerIdentityId("delta-admin-1")
        .build()

    private fun rsaKeyPair() = KeyPairGenerator.getInstance("RSA").run {
        initialize(2048)
        generateKeyPair()
    }

    private class FakeTrustAnchorRepository(publicKeyDer: ByteArray) : TrustAnchorRepository {
        private val value = TrustedIssuerKey(publicKeyDer, "test")
        override val trustedIssuer: Flow<TrustedIssuerKey?> = MutableStateFlow(value)
        override suspend fun pin(publicKeyDer: ByteArray): TrustedIssuerKey = value
    }
}
