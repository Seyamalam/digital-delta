package com.example.digitaldelta.domain.identity

import com.example.digitaldelta.data.local.RecipientKeyDao
import com.example.digitaldelta.data.local.RecipientKeyEntity
import com.example.digitaldelta.domain.mesh.RecipientEncryptionKey
import com.example.digitaldelta.domain.mesh.RecipientKeyDirectory

class RecipientProvisioningRepository(
    private val dao: RecipientKeyDao,
    private val credentials: ProvisioningCredentialService = ProvisioningCredentialService(),
) {
    suspend fun accept(
        credentialBytes: ByteArray,
        trustedIssuerPublicKeyDer: ByteArray,
        nowUnixMs: Long = System.currentTimeMillis(),
    ): RecipientEncryptionKey {
        val claims = credentials.verify(credentialBytes, trustedIssuerPublicKeyDer, nowUnixMs)
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
                revokedAtUnixMs = null,
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

    suspend fun mostRecentlyAccepted(): AcceptedRecipient? =
        dao.mostRecentlyProvisioned()?.let { entity ->
            AcceptedRecipient(
                nodeId = entity.nodeId,
                displayName = entity.displayName,
                encryptionKeyId = entity.encryptionKeyId,
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
