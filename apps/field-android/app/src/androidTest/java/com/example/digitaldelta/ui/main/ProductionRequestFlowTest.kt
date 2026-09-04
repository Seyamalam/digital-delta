package com.example.digitaldelta.ui.main

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.assertIsEnabled
import com.example.digitaldelta.MainActivity
import com.example.digitaldelta.di.DigitalDeltaGraphEntryPoint
import com.example.digitaldelta.domain.identity.ProvisioningCredentialService
import com.example.digitaldelta.domain.identity.DeviceProfiles
import com.example.digitaldelta.domain.mesh.HybridPayloadCipher
import com.example.digitaldelta.domain.mesh.MeshWireCodec
import com.example.digitaldelta.domain.mesh.ProtectedPayload
import com.example.digitaldelta.proto.v1.DomainEvent
import com.example.digitaldelta.proto.v1.IdentityProvisioningClaims
import com.example.digitaldelta.proto.v1.IdentityRole
import com.google.protobuf.ByteString
import dagger.hilt.android.EntryPointAccessors
import java.security.KeyPairGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ProductionRequestFlowTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun requestIsEncryptedAndPersistedThroughProductionGraph() {
        val recipient = rsaKeyPair()
        val entryPoint = productionEntryPoint()
        runBlocking {
            entryPoint.deviceProfileRepository().select(DeviceProfiles.CLINIC)
            val issuer = rsaKeyPair()
            val now = System.currentTimeMillis()
            val local = entryPoint.deviceIdentityKeyStore().createOrGet("N4")
            val localClaims = IdentityProvisioningClaims.newBuilder()
                .setCredentialId("credential-production-n4")
                .setIdentityId("clinic-sylhet-01")
                .setNodeId("N4")
                .setDisplayName("Companyganj Outpost")
                .setRole(IdentityRole.IDENTITY_ROLE_CLINIC)
                .setEncryptionKeyId(local.encryptionKeyId)
                .setRsa2048EncryptionPublicKeyDer(ByteString.copyFrom(local.encryptionPublicKeyDer))
                .setSigningKeyId(local.signingKeyId)
                .setRsa2048SigningPublicKeyDer(ByteString.copyFrom(local.signingPublicKeyDer))
                .setIssuedAtUnixMs(now - 1_000)
                .setExpiresAtUnixMs(now + 86_400_000)
                .setIssuerIdentityId("test-admin")
                .build()
            val recipientClaims = IdentityProvisioningClaims.newBuilder()
                .setCredentialId("credential-production-n6")
                .setIdentityId("hospital-operator-1")
                .setNodeId("N6")
                .setDisplayName("Habiganj Medical")
                .setRole(IdentityRole.IDENTITY_ROLE_HOSPITAL)
                .setEncryptionKeyId("n6-production-key")
                .setRsa2048EncryptionPublicKeyDer(ByteString.copyFrom(recipient.public.encoded))
                .setSigningKeyId("n6-production-signing-key")
                .setRsa2048SigningPublicKeyDer(ByteString.copyFrom(recipient.public.encoded))
                .setIssuedAtUnixMs(now - 1_000)
                .setExpiresAtUnixMs(now + 86_400_000)
                .setIssuerIdentityId("test-admin")
                .build()
            listOf(localClaims, recipientClaims).forEach { claims ->
                entryPoint.recipientProvisioningRepository().accept(
                    credentialBytes = ProvisioningCredentialService().issue(
                        claims = claims,
                        issuerKeyId = "test-admin-key",
                        issuerPrivateKeyDer = issuer.private.encoded,
                    ),
                    trustedIssuerPublicKeyDer = issuer.public.encoded,
                    nowUnixMs = now,
                )
            }
        }
        assertNotNull(runBlocking { entryPoint.identityProvisioningCoordinator().snapshot().localCredential })
        composeTestRule.runOnUiThread {
            ViewModelProvider(composeTestRule.activity)[MainScreenViewModel::class.java]
                .selectDeviceProfile(DeviceProfiles.CLINIC)
        }

        chooseBanglaIfRequired("nav-request")
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("nav-request"), timeoutMillis = 4_000)
        composeTestRule.onNode(hasTestTag("nav-request")).performClick()
        composeTestRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        composeTestRule.onNode(hasTestTag("send-request")).assertIsEnabled().performClick()

        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("request-queued"), timeoutMillis = 4_000)
        composeTestRule.onNode(hasTestTag("request-queued")).assertExists()

        val envelope = runBlocking {
            entryPoint.database().outboxDao().pending(System.currentTimeMillis(), 100)
                .map { MeshWireCodec.decode(it.wireBytes) }
                .first { it.recipientNodeId == "N6" && !it.encryptedPayload.wrappedAes256Key.isEmpty }
        }
        val encrypted = envelope.encryptedPayload
        val associatedData = "${envelope.messageId}|${envelope.senderNodeId}|${envelope.recipientNodeId}|${envelope.createdAtUnixMs}"
            .encodeToByteArray()
        val plaintext = HybridPayloadCipher().decrypt(
            payload = ProtectedPayload(
                recipientKeyId = encrypted.recipientKeyId,
                ciphertext = encrypted.aes256GcmCiphertext.toByteArray(),
                nonce = encrypted.nonce.toByteArray(),
                associatedDataSha256 = encrypted.associatedDataSha256.toByteArray(),
                wrappedAes256Key = encrypted.wrappedAes256Key.toByteArray(),
                keyWrapAlgorithm = encrypted.keyWrapAlgorithm,
                contentAlgorithm = encrypted.contentAlgorithm,
            ),
            recipientPrivateKeyDer = recipient.private.encoded,
            associatedData = associatedData,
        )
        val event = DomainEvent.parseFrom(plaintext)
        assertEquals("N6", event.reliefRequestCreated.destinationNodeId)
        assertTrue(encrypted.wrappedAes256Key.size() >= 256)
    }

    private fun chooseBanglaIfRequired(destinationTag: String) {
        composeTestRule.waitUntil(timeoutMillis = 8_000) {
            composeTestRule.onAllNodes(hasTestTag("language-bangla")).fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodes(hasTestTag("pin-entry")).fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodes(hasTestTag(destinationTag)).fetchSemanticsNodes().isNotEmpty()
        }
        if (composeTestRule.onAllNodes(hasTestTag("language-bangla")).fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNode(hasTestTag("language-bangla")).performClick()
        }
        composeTestRule.waitUntil(timeoutMillis = 8_000) {
            composeTestRule.onAllNodes(hasTestTag("pin-entry")).fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodes(hasTestTag(destinationTag)).fetchSemanticsNodes().isNotEmpty()
        }
        if (composeTestRule.onAllNodes(hasTestTag("pin-entry")).fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNode(hasTestTag("pin-entry")).performTextInput("284619")
            if (composeTestRule.onAllNodes(hasTestTag("pin-confirm")).fetchSemanticsNodes().isNotEmpty()) {
                composeTestRule.onNode(hasTestTag("pin-confirm")).performTextInput("284619")
                composeTestRule.onNode(hasTestTag("configure-pin")).performClick()
            } else {
                composeTestRule.onNode(hasTestTag("unlock-pin")).performClick()
            }
        }
    }

    private fun productionEntryPoint(): DigitalDeltaGraphEntryPoint =
        EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext<Context>(),
            DigitalDeltaGraphEntryPoint::class.java,
        )

    private fun rsaKeyPair() = KeyPairGenerator.getInstance("RSA").run {
        initialize(2048)
        generateKeyPair()
    }
}
