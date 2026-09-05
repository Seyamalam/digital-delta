package com.example.digitaldelta.domain.identity

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.proto.v1.IdentityProvisioningClaims
import com.example.digitaldelta.proto.v1.IdentityRole
import com.example.digitaldelta.proto.v1.CredentialRevocationClaims
import com.google.protobuf.ByteString
import java.security.KeyPairGenerator
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipientProvisioningRepositoryTest {
    private lateinit var database: DeltaDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            DeltaDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun acceptsVerifiedCredentialIntoRecipientDirectory() = runTest {
        val issuer = rsaKeyPair()
        val recipient = rsaKeyPair()
        val claims = IdentityProvisioningClaims.newBuilder()
            .setCredentialId("credential-n6-1")
            .setIdentityId("hospital-operator-1")
            .setNodeId("N6")
            .setDisplayName("Habiganj Medical")
            .setRole(IdentityRole.IDENTITY_ROLE_HOSPITAL)
            .setEncryptionKeyId("n6-encryption-1")
            .setRsa2048EncryptionPublicKeyDer(ByteString.copyFrom(recipient.public.encoded))
            .setSigningKeyId("n6-signing-1")
            .setRsa2048SigningPublicKeyDer(ByteString.copyFrom(recipient.public.encoded))
            .setIssuedAtUnixMs(100)
            .setExpiresAtUnixMs(1_000)
            .setIssuerIdentityId("delta-admin-1")
            .build()
        val credential = ProvisioningCredentialService().issue(
            claims,
            "admin-signing-1",
            issuer.private.encoded,
        )
        val repository = RecipientProvisioningRepository(database.recipientKeyDao())

        val accepted = repository.accept(credential, issuer.public.encoded, nowUnixMs = 200)

        assertEquals("N6", accepted.nodeId)
        assertEquals(
            "n6-encryption-1",
            database.recipientKeyDao().findByNodeId("N6")?.encryptionKeyId,
        )
        assertEquals(
            "Habiganj Medical",
            repository.mostRecentlyAccepted()?.displayName,
        )
        val installed = repository.installedIdentity("N6")
        assertEquals("credential-n6-1", installed?.credentialId)
        assertEquals(IdentityRole.IDENTITY_ROLE_HOSPITAL, installed?.role)
        assertEquals(1_000L, installed?.expiresAtUnixMs)
    }

    @Test
    fun signedRevocationDisablesExactCredentialAndCannotBeUndoneByReplay() = runTest {
        val issuer = rsaKeyPair()
        val recipient = rsaKeyPair()
        val claims = IdentityProvisioningClaims.newBuilder()
            .setCredentialId("credential-n4-1")
            .setIdentityId("clinic-sylhet-01")
            .setNodeId("N4")
            .setDisplayName("Companyganj Clinic")
            .setRole(IdentityRole.IDENTITY_ROLE_CLINIC)
            .setEncryptionKeyId("n4-encryption-1")
            .setRsa2048EncryptionPublicKeyDer(ByteString.copyFrom(recipient.public.encoded))
            .setSigningKeyId("n4-signing-1")
            .setRsa2048SigningPublicKeyDer(ByteString.copyFrom(recipient.public.encoded))
            .setIssuedAtUnixMs(100)
            .setExpiresAtUnixMs(10_000)
            .setIssuerIdentityId("delta-admin-1")
            .build()
        val credential = ProvisioningCredentialService().issue(claims, "admin-signing-1", issuer.private.encoded)
        val repository = RecipientProvisioningRepository(database.recipientKeyDao())
        repository.accept(credential, issuer.public.encoded, nowUnixMs = 200)
        val revocationClaims = CredentialRevocationClaims.newBuilder()
            .setRevocationId("revocation-n4-1")
            .setCredentialId("credential-n4-1")
            .setIdentityId("clinic-sylhet-01")
            .setNodeId("N4")
            .setRevokedAtUnixMs(300)
            .setReasonCode("DEVICE_LOST")
            .setIssuerIdentityId("delta-admin-1")
            .setNonce(ByteString.copyFrom(ByteArray(16) { it.toByte() }))
            .build()
        val revocation = CredentialRevocationService().issue(
            revocationClaims,
            "admin-signing-1",
            issuer.private.encoded,
        )

        val receipt = repository.acceptRevocation(revocation, issuer.public.encoded, nowUnixMs = 400)

        assertEquals("revocation-n4-1", receipt.revocationId)
        assertEquals(300L, repository.installedIdentity("N4")?.revokedAtUnixMs)
        assertNull(RoomRecipientKeyDirectory(database.recipientKeyDao()).findByNodeId("N4")?.takeIf {
            it.revokedAtUnixMs == null
        })

        repository.accept(credential, issuer.public.encoded, nowUnixMs = 500)
        assertEquals(300L, repository.installedIdentity("N4")?.revokedAtUnixMs)

        val wrongTarget = CredentialRevocationService().issue(
            revocationClaims.toBuilder().setCredentialId("credential-other").build(),
            "admin-signing-1",
            issuer.private.encoded,
        )
        assertThrows(ProvisioningCredentialException::class.java) {
            kotlinx.coroutines.runBlocking {
                repository.acceptRevocation(wrongTarget, issuer.public.encoded, nowUnixMs = 600)
            }
        }
    }

    @Test
    fun rotationKeepsHistoricalSignerAndLateRevocationCannotDisableReplacement() = runTest {
        val issuer = rsaKeyPair()
        val oldKey = rsaKeyPair()
        val newKey = rsaKeyPair()
        fun credential(version: Int, key: java.security.KeyPair, issuedAt: Long): ByteArray =
            ProvisioningCredentialService().issue(IdentityProvisioningClaims.newBuilder()
                .setCredentialId("credential-n6-$version").setIdentityId("hospital-operator-1")
                .setNodeId("N6").setDisplayName("Habiganj Medical").setRole(IdentityRole.IDENTITY_ROLE_HOSPITAL)
                .setEncryptionKeyId("n6-encryption-$version").setRsa2048EncryptionPublicKeyDer(ByteString.copyFrom(key.public.encoded))
                .setSigningKeyId("n6-signing-$version").setRsa2048SigningPublicKeyDer(ByteString.copyFrom(key.public.encoded))
                .setIssuedAtUnixMs(issuedAt).setExpiresAtUnixMs(10_000).setIssuerIdentityId("delta-admin-1").build(),
                "admin-signing-1", issuer.private.encoded)
        val original = credential(1, oldKey, 100)
        val replacement = credential(2, newKey, 300)
        val repository = RecipientProvisioningRepository(database.recipientKeyDao())
        repository.accept(original, issuer.public.encoded, 200)
        repository.accept(replacement, issuer.public.encoded, 400)
        repository.accept(original, issuer.public.encoded, 500)
        assertEquals("n6-signing-2", repository.installedIdentity("N6")?.signingKeyId)
        assertEquals("n6-encryption-2", repository.accept(original, issuer.public.encoded, 500).keyId)
        val historical = repository.signingIdentity("hospital-operator-1", "n6-signing-1", 200)
        org.junit.Assert.assertArrayEquals(oldKey.public.encoded, historical?.signingPublicKeyDer)
        assertNull(repository.signingIdentity("hospital-operator-1", "n6-signing-1", 99))
        val revoked = CredentialRevocationService().issue(CredentialRevocationClaims.newBuilder()
            .setRevocationId("revocation-n6-old").setCredentialId("credential-n6-1")
            .setIdentityId("hospital-operator-1").setNodeId("N6").setRevokedAtUnixMs(250)
            .setReasonCode("DEVICE_LOST").setIssuerIdentityId("delta-admin-1")
            .setNonce(ByteString.copyFrom(ByteArray(16) { it.toByte() })).build(), "admin-signing-1", issuer.private.encoded)
        repository.acceptRevocation(revoked, issuer.public.encoded, 600)
        assertNull(repository.installedIdentity("N6")?.revokedAtUnixMs)
        assertEquals(250L, repository.signingIdentity("hospital-operator-1", "n6-signing-1", 200)?.revokedAtUnixMs)
        repository.accept(original, issuer.public.encoded, 700)
        assertEquals("n6-signing-2", repository.installedIdentity("N6")?.signingKeyId)
        assertEquals(250L, repository.signingIdentity("hospital-operator-1", "n6-signing-1", 400)?.revokedAtUnixMs)
    }

    private fun rsaKeyPair() = KeyPairGenerator.getInstance("RSA").run {
        initialize(2048)
        generateKeyPair()
    }
}
