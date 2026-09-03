package com.example.digitaldelta.domain.identity

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.proto.v1.IdentityProvisioningClaims
import com.example.digitaldelta.proto.v1.IdentityRole
import com.google.protobuf.ByteString
import java.security.KeyPairGenerator
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
    }

    private fun rsaKeyPair() = KeyPairGenerator.getInstance("RSA").run {
        initialize(2048)
        generateKeyPair()
    }
}
