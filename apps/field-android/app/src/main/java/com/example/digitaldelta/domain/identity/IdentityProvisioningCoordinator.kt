package com.example.digitaldelta.domain.identity

import com.example.digitaldelta.proto.v1.IdentityProvisioningCredential
import com.example.digitaldelta.proto.v1.IdentityRole
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class IdentityProvisioningSnapshot(
    val profileCode: String,
    val localNodeId: String,
    val localIdentityId: String,
    val localDisplayName: String,
    val localRole: IdentityRole,
    val localEncryptionKeyId: String,
    val enrollmentCode: String,
    val trustedIssuerFingerprint: String?,
    val acceptedRecipient: AcceptedRecipient? = null,
    val localCredential: OfflineCredential? = null,
)

data class AcceptedRecipient(
    val nodeId: String,
    val displayName: String,
    val encryptionKeyId: String,
)

interface IdentityProvisioningCoordinator {
    suspend fun snapshot(): IdentityProvisioningSnapshot
    suspend fun selectProfile(profileCode: String): IdentityProvisioningSnapshot
    suspend fun pinTrustAnchor(code: String): IdentityProvisioningSnapshot
    suspend fun acceptRecipientCredential(code: String): AcceptedRecipient
    suspend fun acceptCredentialRevocation(code: String): RevocationReceipt
}

class DefaultIdentityProvisioningCoordinator(
    private val deviceKeys: AndroidDeviceIdentityKeyStore,
    private val trustAnchors: TrustAnchorRepository,
    private val recipients: RecipientProvisioningRepository,
    private val deviceProfiles: DeviceProfileRepository,
    private val enrollmentRequests: EnrollmentRequestService = EnrollmentRequestService(),
    private val nowUnixMs: () -> Long = System::currentTimeMillis,
    private val secureRandom: SecureRandom = SecureRandom(),
    private val revocationPropagator: CredentialRevocationPropagator = NoOpCredentialRevocationPropagator,
) : IdentityProvisioningCoordinator {
    override suspend fun snapshot(): IdentityProvisioningSnapshot = withContext(Dispatchers.IO) {
        snapshotInternal()
    }

    override suspend fun selectProfile(profileCode: String): IdentityProvisioningSnapshot = withContext(Dispatchers.IO) {
        deviceProfiles.select(profileCode)
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

    override suspend fun acceptCredentialRevocation(code: String): RevocationReceipt = withContext(Dispatchers.IO) {
        val trustedIssuer = trustAnchors.trustedIssuer.first()
            ?: throw IllegalStateException("administrator trust key is not pinned")
        val bytes = decodeCode(code, REVOCATION_PREFIX)
        val receipt = recipients.acceptRevocation(
            revocationBytes = bytes,
            trustedIssuerPublicKeyDer = trustedIssuer.publicKeyDer,
            nowUnixMs = nowUnixMs(),
        )
        revocationPropagator.propagate(
            revocationBytes = bytes,
            receipt = receipt,
            senderNodeId = deviceProfiles.profile.first().nodeId,
        )
        receipt
    }

    private suspend fun snapshotInternal(): IdentityProvisioningSnapshot {
        val profile = deviceProfiles.profile.first()
        val publicIdentity = deviceKeys.createOrGet(profile.nodeId)
        val installedIdentity = recipients.installedIdentity(profile.nodeId)
        val localCredential = installedIdentity?.takeIf { credential ->
            credential.identityId == profile.identityId &&
                credential.role == profile.role &&
                credential.encryptionKeyId == publicIdentity.encryptionKeyId &&
                credential.encryptionPublicKeyDer.contentEquals(publicIdentity.encryptionPublicKeyDer) &&
                credential.signingKeyId == publicIdentity.signingKeyId &&
                credential.signingPublicKeyDer.contentEquals(publicIdentity.signingPublicKeyDer)
        }?.let { credential ->
            OfflineCredential(
                subjectId = credential.identityId,
                role = credential.role.toAuthorizationRole(),
                expiresAtMillis = credential.expiresAtUnixMs,
                revoked = credential.revokedAtUnixMs != null,
            )
        }
        val nonce = ByteArray(16).also(secureRandom::nextBytes)
        val enrollment = enrollmentRequests.create(
            identityId = profile.identityId,
            displayName = profile.displayName,
            role = profile.role,
            publicIdentity = publicIdentity,
            createdAtUnixMs = nowUnixMs(),
            nonce = nonce,
        )
        return IdentityProvisioningSnapshot(
            profileCode = profile.code,
            localNodeId = profile.nodeId,
            localIdentityId = profile.identityId,
            localDisplayName = profile.displayName,
            localRole = profile.role,
            localEncryptionKeyId = publicIdentity.encryptionKeyId,
            enrollmentCode = ENROLLMENT_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(enrollment),
            trustedIssuerFingerprint = trustAnchors.trustedIssuer.first()?.fingerprint,
            acceptedRecipient = recipients.mostRecentlyAccepted(),
            localCredential = localCredential,
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
        const val REVOCATION_PREFIX = "DIGITALDELTA:REVOCATION:"
    }
}
