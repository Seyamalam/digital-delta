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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalAuthorizationCredentialTest {
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
    fun onlySignedCredentialMatchingProfileAndKeystoreUnlocksRole() = runTest {
        val now = 1_788_374_217_000L
        val profile = DeviceProfiles.require(DeviceProfiles.CLINIC)
        val keys = AndroidDeviceIdentityKeyStore()
        val publicIdentity = keys.createOrGet(profile.nodeId)
        val recipients = RecipientProvisioningRepository(database.recipientKeyDao())
        val profiles = FakeDeviceProfiles(profile)
        val coordinator = DefaultIdentityProvisioningCoordinator(
            deviceKeys = keys,
            trustAnchors = EmptyTrustAnchorRepository,
            recipients = recipients,
            deviceProfiles = profiles,
            nowUnixMs = { now },
        )
        assertNull(coordinator.snapshot().localCredential)

        val issuer = KeyPairGenerator.getInstance("RSA").run {
            initialize(2048)
            generateKeyPair()
        }
        var credentialVersion = 0L
        suspend fun install(
            role: IdentityRole,
            identityId: String,
            encryptionPublicKeyDer: ByteArray = publicIdentity.encryptionPublicKeyDer,
            signingPublicKeyDer: ByteArray = publicIdentity.signingPublicKeyDer,
        ) {
            credentialVersion += 1
            val claims = IdentityProvisioningClaims.newBuilder()
                .setCredentialId("credential-${role.name}-$credentialVersion")
                .setIdentityId(identityId)
                .setNodeId(profile.nodeId)
                .setDisplayName(profile.displayName)
                .setRole(role)
                .setEncryptionKeyId(publicIdentity.encryptionKeyId)
                .setRsa2048EncryptionPublicKeyDer(ByteString.copyFrom(encryptionPublicKeyDer))
                .setSigningKeyId(publicIdentity.signingKeyId)
                .setRsa2048SigningPublicKeyDer(ByteString.copyFrom(signingPublicKeyDer))
                .setIssuedAtUnixMs(now - 1_000 + credentialVersion)
                .setExpiresAtUnixMs(now + 86_400_000)
                .setIssuerIdentityId("delta-admin-1")
                .build()
            recipients.accept(
                ProvisioningCredentialService().issue(claims, "admin-signing-1", issuer.private.encoded),
                issuer.public.encoded,
                now,
            )
        }

        install(profile.role, profile.identityId)
        val authorized = coordinator.snapshot().localCredential
        assertEquals(Role.REQUESTER, authorized?.role)
        assertTrue(AuthorizationPolicy().authorize(authorized!!, Permission.CREATE_REQUEST, now).allowed)

        val attacker = KeyPairGenerator.getInstance("RSA").run {
            initialize(2048)
            generateKeyPair()
        }
        install(profile.role, profile.identityId, attacker.public.encoded, attacker.public.encoded)
        assertNull(coordinator.snapshot().localCredential)

        install(IdentityRole.IDENTITY_ROLE_COORDINATOR, "attacker-coordinator")
        assertNull(coordinator.snapshot().localCredential)
    }

    private class FakeDeviceProfiles(initial: LocalDeviceProfile) : DeviceProfileRepository {
        private val selected = MutableStateFlow(initial)
        override val profile: Flow<LocalDeviceProfile> = selected

        override suspend fun select(code: String): LocalDeviceProfile = DeviceProfiles.require(code).also {
            selected.value = it
        }
    }

    private object EmptyTrustAnchorRepository : TrustAnchorRepository {
        override val trustedIssuer: Flow<TrustedIssuerKey?> = flowOf(null)
        override suspend fun pin(publicKeyDer: ByteArray): TrustedIssuerKey = error("not used")
    }
}
