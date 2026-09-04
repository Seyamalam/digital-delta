package com.example.digitaldelta.domain.identity

import androidx.room.withTransaction
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.data.local.MeshEnvelopeEntity
import com.example.digitaldelta.data.local.OperationEntity
import com.example.digitaldelta.data.local.QueueState
import com.example.digitaldelta.domain.mesh.HybridPayloadCipher
import com.example.digitaldelta.domain.mesh.MeshWireCodec
import com.example.digitaldelta.proto.v1.DomainEvent
import com.example.digitaldelta.proto.v1.PriorityClass
import com.example.digitaldelta.proto.v1.SignedCredentialRevocation
import java.security.MessageDigest

interface CredentialRevocationPropagator {
    suspend fun propagate(
        revocationBytes: ByteArray,
        receipt: RevocationReceipt,
        senderNodeId: String,
        excludedNodeIds: Set<String> = emptySet(),
    ): Int
}

object NoOpCredentialRevocationPropagator : CredentialRevocationPropagator {
    override suspend fun propagate(
        revocationBytes: ByteArray,
        receipt: RevocationReceipt,
        senderNodeId: String,
        excludedNodeIds: Set<String>,
    ): Int = 0
}

class RoomCredentialRevocationPropagator(
    private val database: DeltaDatabase,
    private val cipher: HybridPayloadCipher = HybridPayloadCipher(),
    private val nowUnixMs: () -> Long = System::currentTimeMillis,
    private val envelopeSigner: com.example.digitaldelta.domain.mesh.EnvelopeSigner = com.example.digitaldelta.domain.mesh.EnvelopeSigner { it },
) : CredentialRevocationPropagator {
    override suspend fun propagate(
        revocationBytes: ByteArray,
        receipt: RevocationReceipt,
        senderNodeId: String,
        excludedNodeIds: Set<String>,
    ): Int {
        require(senderNodeId.isNotBlank())
        val signed = SignedCredentialRevocation.parseFrom(revocationBytes)
        require(signed.claims.revocationId == receipt.revocationId)
        val event = DomainEvent.newBuilder()
            .setEventId(receipt.revocationId)
            .setSchemaVersion(1)
            .setActorIdentityId(signed.claims.issuerIdentityId)
            .setOccurredAtUnixMs(receipt.revokedAtUnixMs)
            .setSimulated(false)
            .setCredentialRevoked(signed)
            .build()
        val eventBytes = event.toByteArray()
        val eventHash = sha256(eventBytes)
        val now = nowUnixMs()
        val recipients = database.recipientKeyDao().validAt(now)
            .filterNot { it.nodeId == senderNodeId || it.nodeId in excludedNodeIds }
        val envelopes = recipients.map { recipient ->
            val messageId = "${receipt.revocationId}:$senderNodeId:${recipient.nodeId}"
            val associatedData = "$messageId|$senderNodeId|${recipient.nodeId}|$now".encodeToByteArray()
            val protected = cipher.encrypt(
                recipientKeyId = recipient.encryptionKeyId,
                recipientPublicKeyDer = recipient.encryptionPublicKeyDer,
                plaintext = eventBytes,
                associatedData = associatedData,
            )
            val envelope = envelopeSigner.sign(MeshWireCodec.createEnvelope(
                messageId = messageId,
                senderNodeId = senderNodeId,
                recipientNodeId = recipient.nodeId,
                createdAtUnixMs = now,
                expiresAtUnixMs = now + REVOCATION_TTL_MS,
                hopLimit = 8,
                priority = PriorityClass.PRIORITY_CLASS_P0,
                payloadHash = eventHash,
                simulated = false,
                scenarioSeed = "",
                protectedPayload = protected,
            ))
            MeshEnvelopeEntity(
                messageId = messageId,
                wireBytes = MeshWireCodec.encode(envelope),
                priority = PriorityClass.PRIORITY_CLASS_P0.number,
                expiresAtUnixMs = envelope.expiresAtUnixMs,
                state = QueueState.PENDING.name,
                attemptCount = 0,
                nextAttemptAtUnixMs = now,
            )
        }
        database.withTransaction {
            database.operationLogDao().appendIfAbsent(
                OperationEntity(
                    eventId = event.eventId,
                    missionId = "security-${receipt.nodeId}",
                    eventType = EVENT_TYPE,
                    payloadBytes = eventBytes,
                    createdAtUnixMs = receipt.revokedAtUnixMs,
                ),
            )
            envelopes.forEach { database.outboxDao().enqueue(it) }
        }
        return envelopes.size
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    companion object {
        const val EVENT_TYPE = "CREDENTIAL_REVOKED"
        private const val REVOCATION_TTL_MS = 7 * 24 * 60 * 60 * 1_000L
    }
}
