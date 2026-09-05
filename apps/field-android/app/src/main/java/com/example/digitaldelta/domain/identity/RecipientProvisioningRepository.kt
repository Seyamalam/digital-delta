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
    val authorities = dao.observeAuthorities()

    suspend fun accept(
        credentialBytes: ByteArray,
        trustedIssuerPublicKeyDer: ByteArray,
        nowUnixMs: Long = System.currentTimeMillis(),
    ): RecipientEncryptionKey {
        val claims = credentials.verify(credentialBytes, trustedIssuerPublicKeyDer, nowUnixMs)
        val existingRevokedAt = dao.findByNodeId(claims.nodeId)
            ?.takeIf { it.credentialBytes.contentEquals(credentialBytes) }
            ?.revokedAtUnixMs
        val fingerprint = java.security.MessageDigest.getInstance("SHA-256").digest(credentialBytes).joinToString("") { "%02x".format(it) }
        dao.installCredential(
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
            com.example.digitaldelta.data.local.HistoricalCredentialEntity(fingerprint, claims.nodeId, claims.identityId, claims.credentialId,
                claims.signingKeyId, credentialBytes.copyOf(), claims.issuedAtUnixMs, claims.expiresAtUnixMs, existingRevokedAt),
        )
        val installed = requireNotNull(dao.findByNodeId(claims.nodeId))
        return RecipientEncryptionKey(
            nodeId = claims.nodeId,
            keyId = installed.encryptionKeyId,
            publicKeyDer = installed.encryptionPublicKeyDer,
            validUntilUnixMs = installed.expiresAtUnixMs,
            revokedAtUnixMs = installed.revokedAtUnixMs,
        )
    }

    suspend fun acceptRevocation(
        revocationBytes: ByteArray,
        trustedIssuerPublicKeyDer: ByteArray,
        nowUnixMs: Long = System.currentTimeMillis(),
    ): RevocationReceipt {
        val claims = revocations.verify(revocationBytes, trustedIssuerPublicKeyDer, nowUnixMs)
        val installed = dao.findByNodeId(claims.nodeId)
        val archived = dao.archivedRevocationTargets(claims.nodeId, claims.credentialId)
        val target = archived.firstOrNull()?.credentialBytes ?: installed?.credentialBytes
            ?: throw ProvisioningCredentialException("revocation target is not installed")
        val installedClaims = runCatching {
            com.example.digitaldelta.proto.v1.IdentityProvisioningCredential
                .parseFrom(target)
                .claims
        }.getOrElse { throw ProvisioningCredentialException("installed credential is malformed", it) }
        if (
            installedClaims.credentialId != claims.credentialId ||
            installedClaims.identityId != claims.identityId ||
            installedClaims.nodeId != claims.nodeId ||
            installedClaims.issuerIdentityId != claims.issuerIdentityId ||
            archived.any {
                val stored = com.example.digitaldelta.proto.v1.IdentityProvisioningCredential.parseFrom(it.credentialBytes).claims
                stored.identityId != claims.identityId || stored.issuerIdentityId != claims.issuerIdentityId
            }
        ) {
            throw ProvisioningCredentialException("revocation target does not match the installed credential")
        }
        val updated = dao.revokeExactCredential(
            nodeId = claims.nodeId,
            identityId = claims.identityId,
            credentialBytes = target,
            revokedAtUnixMs = claims.revokedAtUnixMs,
        )
        dao.revokeHistory(claims.nodeId, claims.credentialId, claims.revokedAtUnixMs)
        if (updated == 0 && archived.isEmpty() && installed?.revokedAtUnixMs == null) {
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

    suspend fun signingIdentity(identityId: String, keyId: String, at: Long): InstalledIdentityCredential? {
        val record = dao.historicalSigners(identityId, keyId, at).firstOrNull() ?: return dao.findSigningIdentity(identityId, keyId)?.let { installedIdentity(it.nodeId) }
        val claims = com.example.digitaldelta.proto.v1.IdentityProvisioningCredential.parseFrom(record.credentialBytes).claims
        return InstalledIdentityCredential(claims.credentialId, claims.identityId, claims.nodeId, claims.role,
            claims.encryptionKeyId, claims.rsa2048EncryptionPublicKeyDer.toByteArray(), claims.signingKeyId,
            claims.rsa2048SigningPublicKeyDer.toByteArray(), claims.issuerIdentityId, claims.issuedAtUnixMs,
            claims.expiresAtUnixMs, record.revokedAtUnixMs)
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
