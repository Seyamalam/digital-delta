package com.example.digitaldelta.domain.identity

import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.data.local.InboxApplicationEntity
import com.example.digitaldelta.domain.mesh.MeshWireCodec
import com.example.digitaldelta.domain.mesh.ProtectedPayload
import com.example.digitaldelta.proto.v1.DomainEvent
import java.security.MessageDigest
import kotlinx.coroutines.flow.first

data class InboxApplicationBatch(
    val applied: Int,
    val rejected: Int,
    val deferred: Int,
    val retry: Int,
)

class CredentialRevocationInboxProcessor(
    private val database: DeltaDatabase,
    private val deviceKeys: AndroidDeviceIdentityKeyStore,
    private val recipients: RecipientProvisioningRepository,
    private val trustAnchors: TrustAnchorRepository,
    private val propagator: CredentialRevocationPropagator,
    private val nowUnixMs: () -> Long = System::currentTimeMillis,
) {
    suspend fun process(localNodeId: String, limit: Int = 32): InboxApplicationBatch {
        var applied = 0
        var rejected = 0
        var deferred = 0
        var retry = 0
        database.meshInboxDao().pendingApplication(localNodeId, limit).forEach { inbox ->
            val previousAttempts = database.inboxApplicationDao().find(inbox.messageId)?.attemptCount ?: 0
            var decodedEventId: String? = null
            val result = runCatching {
                val envelope = MeshWireCodec.decode(inbox.wireBytes)
                val encrypted = envelope.encryptedPayload
                require(!encrypted.wrappedAes256Key.isEmpty) { "encrypted payload is required" }
                val associatedData = "${envelope.messageId}|${envelope.senderNodeId}|${envelope.recipientNodeId}|${envelope.createdAtUnixMs}"
                    .encodeToByteArray()
                val plaintext = deviceKeys.decrypt(
                    nodeId = localNodeId,
                    payload = ProtectedPayload(
                        recipientKeyId = encrypted.recipientKeyId,
                        ciphertext = encrypted.aes256GcmCiphertext.toByteArray(),
                        nonce = encrypted.nonce.toByteArray(),
                        associatedDataSha256 = encrypted.associatedDataSha256.toByteArray(),
                        wrappedAes256Key = encrypted.wrappedAes256Key.toByteArray(),
                        keyWrapAlgorithm = encrypted.keyWrapAlgorithm,
                        contentAlgorithm = encrypted.contentAlgorithm,
                    ),
                    associatedData = associatedData,
                )
                require(MessageDigest.isEqual(sha256(plaintext), envelope.payloadSha256.toByteArray())) {
                    "payload digest does not match envelope"
                }
                val event = DomainEvent.parseFrom(plaintext)
                decodedEventId = event.eventId.takeIf(String::isNotBlank)
                if (!event.hasCredentialRevoked()) {
                    require(com.example.digitaldelta.domain.mesh.AndroidEnvelopeSecurity(deviceKeys, database.recipientKeyDao(), trustAnchors).verify(envelope, nowUnixMs())) { "Origin signature rejected" }
                    val issuer = trustAnchors.trustedIssuer.first() ?: throw TransientApplicationException("Trust anchor unavailable")
                    recipients.accept(envelope.senderCredential.toByteArray(), issuer.publicKeyDer, nowUnixMs())
                    if (event.hasCustodyTransfer()) {
                        require(event.actorIdentityId == envelope.senderCredential.claims.identityId && event.simulated == envelope.simulated && event.scenarioSeed == envelope.scenarioSeed)
                        return@runCatching if (com.example.digitaldelta.domain.pod.OperationalProofOfDeliveryWorkflow(database, deviceKeys, recipients).importReceipt(event)) ApplicationResult.APPLIED else ApplicationResult.REJECTED
                    }
                    return@runCatching if (com.example.digitaldelta.domain.sync.ReceivedEventApplication(database).apply(event, envelope, localNodeId)) ApplicationResult.APPLIED else ApplicationResult.DEFERRED_UNSUPPORTED
                }
                val signed = event.credentialRevoked
                require(event.eventId == signed.claims.revocationId)
                require(event.actorIdentityId == signed.claims.issuerIdentityId)
                require(event.occurredAtUnixMs == signed.claims.revokedAtUnixMs)
                require(!event.simulated) { "security revocation cannot be simulated" }
                if (database.operationLogDao().find(event.eventId) != null) {
                    return@runCatching ApplicationResult.APPLIED_DUPLICATE
                }
                val trust = trustAnchors.trustedIssuer.first()
                    ?: throw TransientApplicationException("administrator trust key is not pinned")
                val receipt = recipients.acceptRevocation(
                    signed.toByteArray(),
                    trust.publicKeyDer,
                    nowUnixMs(),
                )
                propagator.propagate(
                    revocationBytes = signed.toByteArray(),
                    receipt = receipt,
                    senderNodeId = localNodeId,
                    excludedNodeIds = setOf(envelope.senderNodeId),
                )
                ApplicationResult.APPLIED
            }
            val outcome = result.getOrElse { error ->
                if (error is TransientApplicationException || error is com.example.digitaldelta.domain.sync.MissingEventDependency) ApplicationResult.RETRY else ApplicationResult.REJECTED
            }
            database.inboxApplicationDao().upsert(
                InboxApplicationEntity(
                    messageId = inbox.messageId,
                    state = outcome.state,
                    eventId = decodedEventId,
                    reasonCode = outcome.reason,
                    attemptCount = previousAttempts + 1,
                    updatedAtUnixMs = nowUnixMs(),
                ),
            )
            when (outcome) {
                ApplicationResult.APPLIED, ApplicationResult.APPLIED_DUPLICATE -> applied++
                ApplicationResult.REJECTED -> rejected++
                ApplicationResult.DEFERRED_UNSUPPORTED -> deferred++
                ApplicationResult.RETRY -> retry++
            }
        }
        return InboxApplicationBatch(applied, rejected, deferred, retry)
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private enum class ApplicationResult(val state: String, val reason: String?) {
        APPLIED("APPLIED", null),
        APPLIED_DUPLICATE("APPLIED", "DUPLICATE_EVENT"),
        REJECTED("REJECTED", "SECURITY_OR_FORMAT_REJECTED"),
        DEFERRED_UNSUPPORTED("DEFERRED", "UNSUPPORTED_EVENT"),
        RETRY("RETRY", "TRANSIENT_DEPENDENCY"),
    }

    private class TransientApplicationException(message: String) : IllegalStateException(message)
}
