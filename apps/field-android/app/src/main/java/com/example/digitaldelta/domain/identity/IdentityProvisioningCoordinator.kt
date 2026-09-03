package com.example.digitaldelta.domain.identity

import com.example.digitaldelta.proto.v1.IdentityProvisioningCredential
import com.example.digitaldelta.proto.v1.IdentityRole
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class IdentityProvisioningSnapshot(
    val localNodeId: String,
    val localEncryptionKeyId: String,
    val enrollmentCode: String,
    val trustedIssuerFingerprint: String?,
    val acceptedRecipient: AcceptedRecipient? = null,
)

data class AcceptedRecipient(
    val nodeId: String,
    val displayName: String,
    val encryptionKeyId: String,
)

interface IdentityProvisioningCoordinator {
    suspend fun snapshot(): IdentityProvisioningSnapshot
    suspend fun pinTrustAnchor(code: String): IdentityProvisioningSnapshot
    suspend fun acceptRecipientCredential(code: String): AcceptedRecipient
}

class DefaultIdentityProvisioningCoordinator(
    private val deviceKeys: AndroidDeviceIdentityKeyStore,
    private val trustAnchors: TrustAnchorRepository,
    private val recipients: RecipientProvisioningRepository,
    private val enrollmentRequests: EnrollmentRequestService = EnrollmentRequestService(),
    private val nowUnixMs: () -> Long = System::currentTimeMillis,
    private val secureRandom: SecureRandom = SecureRandom(),
) : IdentityProvisioningCoordinator {
    override suspend fun snapshot(): IdentityProvisioningSnapshot = withContext(Dispatchers.IO) {
        snapshotInternal()
    }

    override suspend fun pinTrustAnchor(code: String): IdentityProvisioningSnapshot = withContext(Dispatchers.IO) {
        trustAnchors.pin(decodeCode(code, TRUST_PREFIX))
        snapshotInternal()
    }

    override suspend fun acceptRecipientCredential(code: String): AcceptedRecipient = withContext(Dispatchers.IO) {
        val trustedIssuer = trustAnchors.trustedIssuer.first()
            ?: throw IllegalStateException("administrator trust key is not pinned")
        val credentialBytes = decodeCode(code, CREDENTIAL_PREFIX)
        val accepted = recipients.accept(
            credentialBytes = credentialBytes,
            trustedIssuerPublicKeyDer = trustedIssuer.publicKeyDer,
            nowUnixMs = nowUnixMs(),
        )
        val claims = IdentityProvisioningCredential.parseFrom(credentialBytes).claims
        AcceptedRecipient(accepted.nodeId, claims.displayName, accepted.keyId)
    }

    private suspend fun snapshotInternal(): IdentityProvisioningSnapshot {
        val publicIdentity = deviceKeys.createOrGet(LOCAL_NODE_ID)
        val nonce = ByteArray(16).also(secureRandom::nextBytes)
        val enrollment = enrollmentRequests.create(
            identityId = LOCAL_IDENTITY_ID,
            displayName = LOCAL_DISPLAY_NAME,
            role = IdentityRole.IDENTITY_ROLE_CLINIC,
            publicIdentity = publicIdentity,
            createdAtUnixMs = nowUnixMs(),
            nonce = nonce,
        )
        return IdentityProvisioningSnapshot(
            localNodeId = LOCAL_NODE_ID,
            localEncryptionKeyId = publicIdentity.encryptionKeyId,
            enrollmentCode = ENROLLMENT_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(enrollment),
            trustedIssuerFingerprint = trustAnchors.trustedIssuer.first()?.fingerprint,
            acceptedRecipient = recipients.mostRecentlyAccepted(),
        )
    }

    private fun decodeCode(raw: String, prefix: String): ByteArray {
        val normalized = raw.trim().removePrefix(prefix)
        require(normalized.isNotBlank()) { "provisioning code is required" }
        return Base64.getUrlDecoder().decode(normalized)
    }

    companion object {
        const val ENROLLMENT_PREFIX = "DIGITALDELTA:ENROLLMENT:"
        const val TRUST_PREFIX = "DIGITALDELTA:TRUST:"
        const val CREDENTIAL_PREFIX = "DIGITALDELTA:CREDENTIAL:"
        private const val LOCAL_NODE_ID = "N4"
        private const val LOCAL_IDENTITY_ID = "clinic-sylhet-01"
        private const val LOCAL_DISPLAY_NAME = "Companyganj Outpost"
    }
}
