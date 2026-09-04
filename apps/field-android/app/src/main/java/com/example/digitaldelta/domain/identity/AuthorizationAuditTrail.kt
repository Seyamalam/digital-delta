package com.example.digitaldelta.domain.identity

import androidx.room.withTransaction
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.data.local.OperationEntity
import com.example.digitaldelta.proto.v1.AuthorizationAuditEntry
import com.example.digitaldelta.proto.v1.DomainEvent
import com.example.digitaldelta.proto.v1.IdentityRole
import com.example.digitaldelta.proto.v1.SignedAuthorizationAuditEntry
import com.example.digitaldelta.proto.v1.Signature as WireSignature
import com.google.protobuf.ByteString
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

data class AuthorizationAuditRecord(
    val auditId: String,
    val permission: Permission,
    val allowed: Boolean,
    val reasonCode: String,
    val occurredAtUnixMs: Long,
)

interface AuthorizationAuditTrail {
    suspend fun record(
        actorIdentityId: String,
        actorNodeId: String,
        role: IdentityRole,
        permission: Permission,
        allowed: Boolean,
        reasonCode: String,
    ): AuthorizationAuditRecord

    suspend fun verifyChain(): Boolean
}

class RoomSignedAuthorizationAuditTrail(
    private val database: DeltaDatabase,
    private val deviceKeys: AndroidDeviceIdentityKeyStore,
    private val nowUnixMs: () -> Long = System::currentTimeMillis,
    private val auditId: () -> String = { "audit-${UUID.randomUUID()}" },
) : AuthorizationAuditTrail {
    override suspend fun record(
        actorIdentityId: String,
        actorNodeId: String,
        role: IdentityRole,
        permission: Permission,
        allowed: Boolean,
        reasonCode: String,
    ): AuthorizationAuditRecord {
        require(actorIdentityId.isNotBlank())
        require(actorNodeId.isNotBlank())
        val id = auditId()
        var recordedAtUnixMs = 0L
        database.withTransaction {
            val previous = database.operationLogDao().authorizationAudit().lastOrNull()
            val previousPayload = previous?.payloadBytes
            val now = maxOf(nowUnixMs(), (previous?.createdAtUnixMs ?: 0L) + 1)
            recordedAtUnixMs = now
            val entry = AuthorizationAuditEntry.newBuilder()
                .setAuditId(id)
                .setActorIdentityId(actorIdentityId)
                .setActorNodeId(actorNodeId)
                .setRole(role)
                .setPermissionCode(permission.name)
                .setAllowed(allowed)
                .setReasonCode(reasonCode)
                .setOccurredAtUnixMs(now)
                .setPreviousRecordSha256(
                    ByteString.copyFrom(previousPayload?.let(::sha256) ?: ByteArray(32)),
                )
                .build()
            val publicIdentity = deviceKeys.createOrGet(actorNodeId)
            val signed = SignedAuthorizationAuditEntry.newBuilder()
                .setEntry(entry)
                .setActorSignature(
                    WireSignature.newBuilder()
                        .setKeyId(publicIdentity.signingKeyId)
                        .setRsa2048PssSha256(ByteString.copyFrom(deviceKeys.sign(actorNodeId, entry.toByteArray())))
                        .setAlgorithm(ProvisioningCredentialService.SIGNATURE_ALGORITHM)
                        .build(),
                )
                .build()
            val event = DomainEvent.newBuilder()
                .setEventId(id)
                .setSchemaVersion(1)
                .setActorIdentityId(actorIdentityId)
                .setOccurredAtUnixMs(now)
                .setSimulated(false)
                .setAuthorizationAudit(signed)
                .build()
            database.operationLogDao().append(
                OperationEntity(
                    eventId = id,
                    missionId = "security-$actorNodeId",
                    eventType = EVENT_TYPE,
                    payloadBytes = event.toByteArray(),
                    createdAtUnixMs = now,
                ),
            )
        }
        return AuthorizationAuditRecord(id, permission, allowed, reasonCode, recordedAtUnixMs)
    }

    override suspend fun verifyChain(): Boolean =
        verifyPayloads(database.operationLogDao().authorizationAudit().map { it.payloadBytes })

    fun verifyPayloads(payloads: List<ByteArray>): Boolean = runCatching {
        var previousPayload: ByteArray? = null
        payloads.forEach { payload ->
            val event = DomainEvent.parseFrom(payload)
            require(event.hasAuthorizationAudit())
            val signed = event.authorizationAudit
            val entry = signed.entry
            require(event.eventId == entry.auditId)
            require(event.actorIdentityId == entry.actorIdentityId)
            require(event.occurredAtUnixMs == entry.occurredAtUnixMs)
            require(
                MessageDigest.isEqual(
                    entry.previousRecordSha256.toByteArray(),
                    previousPayload?.let(::sha256) ?: ByteArray(32),
                ),
            )
            val publicIdentity = deviceKeys.createOrGet(entry.actorNodeId)
            require(signed.actorSignature.keyId == publicIdentity.signingKeyId)
            require(signed.actorSignature.algorithm == ProvisioningCredentialService.SIGNATURE_ALGORITHM)
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(
                X509EncodedKeySpec(publicIdentity.signingPublicKeyDer),
            )
            require(newSignature().run {
                initVerify(publicKey)
                update(entry.toByteArray())
                verify(signed.actorSignature.rsa2048PssSha256.toByteArray())
            })
            previousPayload = payload
        }
        true
    }.getOrDefault(false)

    private fun newSignature(): Signature = runCatching {
        Signature.getInstance("RSASSA-PSS").apply {
            setParameter(PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1))
        }
    }.getOrElse { Signature.getInstance("SHA256withRSA/PSS") }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    companion object {
        const val EVENT_TYPE = "AUTHORIZATION_AUDIT"
    }
}
