package com.example.digitaldelta.domain.identity

import com.example.digitaldelta.data.local.RecipientKeyDao
import com.example.digitaldelta.data.local.RecipientKeyEntity
import com.example.digitaldelta.domain.mesh.RecipientEncryptionKey
import com.example.digitaldelta.domain.mesh.RecipientKeyDirectory
import com.example.digitaldelta.proto.v1.IdentityRole

data class InstalledIdentityCredential(
    val credentialId: String,
    val identityId: String,
    val nodeId: String,
    val role: IdentityRole,
    val encryptionKeyId: String,
    val encryptionPublicKeyDer: ByteArray,
    val signingKeyId: String,
    val signingPublicKeyDer: ByteArray,
    val issuerIdentityId: String,
    val issuedAtUnixMs: Long,
    val expiresAtUnixMs: Long,
    val revokedAtUnixMs: Long?,
)

data class RevocationReceipt(
    val revocationId: String,
    val credentialId: String,
    val identityId: String,
    val nodeId: String,
    val revokedAtUnixMs: Long,
    val reasonCode: String,
)

class RecipientProvisioningRepository(
    private val dao: RecipientKeyDao,
    private val credentials: ProvisioningCredentialService = ProvisioningCredentialService(),
    private val revocations: CredentialRevocationService = CredentialRevocationService(),
) {
    suspend fun accept(
        credentialBytes: ByteArray,
        trustedIssuerPublicKeyDer: ByteArray,
        nowUnixMs: Long = System.currentTimeMillis(),
    ): RecipientEncryptionKey {
        val claims = credentials.verify(credentialBytes, trustedIssuerPublicKeyDer, nowUnixMs)
        val existingRevokedAt = dao.findByNodeId(claims.nodeId)
            ?.takeIf { it.credentialBytes.contentEquals(credentialBytes) }
            ?.revokedAtUnixMs
        dao.upsert(
            RecipientKeyEntity(
                nodeId = claims.nodeId,
                identityId = claims.identityId,
                displayName = claims.displayName,
                roleCode = claims.role.name,
                encryptionKeyId = claims.encryptionKeyId,
                encryptionPublicKeyDer = claims.rsa2048EncryptionPublicKeyDer.toByteArray(),
                signingKeyId = claims.signingKeyId,
                signingPublicKeyDer = claims.rsa2048SigningPublicKeyDer.toByteArray(),
                issuerIdentityId = claims.issuerIdentityId,
                credentialBytes = credentialBytes.copyOf(),
                issuedAtUnixMs = claims.issuedAtUnixMs,
                expiresAtUnixMs = claims.expiresAtUnixMs,
                revokedAtUnixMs = existingRevokedAt,
                provisionedAtUnixMs = nowUnixMs,
            ),
        )
        return RecipientEncryptionKey(
            nodeId = claims.nodeId,
            keyId = claims.encryptionKeyId,
            publicKeyDer = claims.rsa2048EncryptionPublicKeyDer.toByteArray(),
            validUntilUnixMs = claims.expiresAtUnixMs,
            revokedAtUnixMs = null,
        )
    }

    suspend fun acceptRevocation(
        revocationBytes: ByteArray,
        trustedIssuerPublicKeyDer: ByteArray,
        nowUnixMs: Long = System.currentTimeMillis(),
    ): RevocationReceipt {
        val claims = revocations.verify(revocationBytes, trustedIssuerPublicKeyDer, nowUnixMs)
        val installed = dao.findByNodeId(claims.nodeId)
            ?: throw ProvisioningCredentialException("revocation target is not installed")
        val installedClaims = runCatching {
            com.example.digitaldelta.proto.v1.IdentityProvisioningCredential
                .parseFrom(installed.credentialBytes)
                .claims
        }.getOrElse { throw ProvisioningCredentialException("installed credential is malformed", it) }
        if (
            installedClaims.credentialId != claims.credentialId ||
            installed.identityId != claims.identityId ||
            installed.issuerIdentityId != claims.issuerIdentityId
        ) {
            throw ProvisioningCredentialException("revocation target does not match the installed credential")
        }
        val updated = dao.revokeExactCredential(
            nodeId = claims.nodeId,
            identityId = claims.identityId,
            credentialBytes = installed.credentialBytes,
            revokedAtUnixMs = claims.revokedAtUnixMs,
        )
        if (updated == 0 && installed.revokedAtUnixMs == null) {
            throw ProvisioningCredentialException("revocation target changed before it could be applied")
        }
        return RevocationReceipt(
            revocationId = claims.revocationId,
            credentialId = claims.credentialId,
            identityId = claims.identityId,
            nodeId = claims.nodeId,
            revokedAtUnixMs = claims.revokedAtUnixMs,
            reasonCode = claims.reasonCode,
        )
    }

    suspend fun mostRecentlyAccepted(): AcceptedRecipient? =
        dao.mostRecentlyProvisioned()?.let { entity ->
            AcceptedRecipient(
                nodeId = entity.nodeId,
                displayName = entity.displayName,
                encryptionKeyId = entity.encryptionKeyId,
            )
        }

    suspend fun installedIdentity(nodeId: String): InstalledIdentityCredential? =
        dao.findByNodeId(nodeId)?.let { entity ->
            val claims = runCatching {
                com.example.digitaldelta.proto.v1.IdentityProvisioningCredential
                    .parseFrom(entity.credentialBytes)
                    .claims
            }.getOrNull() ?: return@let null
            InstalledIdentityCredential(
                credentialId = claims.credentialId,
                identityId = entity.identityId,
                nodeId = entity.nodeId,
                role = runCatching { IdentityRole.valueOf(entity.roleCode) }
                    .getOrDefault(IdentityRole.IDENTITY_ROLE_UNSPECIFIED),
                encryptionKeyId = entity.encryptionKeyId,
                encryptionPublicKeyDer = entity.encryptionPublicKeyDer.copyOf(),
                signingKeyId = entity.signingKeyId,
                signingPublicKeyDer = entity.signingPublicKeyDer.copyOf(),
                issuerIdentityId = entity.issuerIdentityId,
                issuedAtUnixMs = entity.issuedAtUnixMs,
                expiresAtUnixMs = entity.expiresAtUnixMs,
                revokedAtUnixMs = entity.revokedAtUnixMs,
            )
        }
}

class RoomRecipientKeyDirectory(private val dao: RecipientKeyDao) : RecipientKeyDirectory {
    override suspend fun findByNodeId(nodeId: String): RecipientEncryptionKey? =
        dao.findByNodeId(nodeId)?.let { entity ->
            RecipientEncryptionKey(
                nodeId = entity.nodeId,
                keyId = entity.encryptionKeyId,
                publicKeyDer = entity.encryptionPublicKeyDer,
                validUntilUnixMs = entity.expiresAtUnixMs,
                revokedAtUnixMs = entity.revokedAtUnixMs,
            )
        }
}
