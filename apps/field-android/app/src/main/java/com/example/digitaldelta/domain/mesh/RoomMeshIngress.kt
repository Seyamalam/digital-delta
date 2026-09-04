package com.example.digitaldelta.domain.mesh

import androidx.room.withTransaction
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.data.local.MeshEnvelopeEntity
import com.example.digitaldelta.data.local.MeshInboxEntity
import com.example.digitaldelta.data.local.QueueState
import com.example.digitaldelta.data.local.SeenMessageEntity
import com.example.digitaldelta.proto.v1.Acknowledgement
import com.example.digitaldelta.proto.v1.AcknowledgementStatus
import com.example.digitaldelta.proto.v1.Envelope
import com.google.protobuf.InvalidProtocolBufferException

class RoomMeshIngress(
    private val database: DeltaDatabase,
    private val localNodeId: String,
    private val acknowledgementSigner: MeshAcknowledgementSigner,
    private val nowUnixMs: () -> Long = System::currentTimeMillis,
    private val localApplicationScheduler: suspend () -> Unit = {},
) {
    init {
        require(localNodeId.isNotBlank()) { "local node id is required" }
    }

    suspend fun receive(wireBytes: ByteArray): Acknowledgement {
        val recordedAt = nowUnixMs()
        if (wireBytes.isEmpty() || wireBytes.size > MAX_ENVELOPE_BYTES) {
            return rejected("", "INVALID_SIZE", recordedAt)
        }
        val envelope = try {
            MeshWireCodec.decode(wireBytes)
        } catch (_: InvalidProtocolBufferException) {
            return rejected("", "MALFORMED_PROTOBUF", recordedAt)
        }
        rejectionReason(envelope, recordedAt)?.let { reason ->
            return rejected(envelope.messageId, reason, recordedAt)
        }

        val acknowledgement = database.withTransaction {
            val claimed = database.seenMessageDao().claim(
                SeenMessageEntity(
                    messageId = envelope.messageId,
                    expiresAtUnixMs = envelope.expiresAtUnixMs,
                    firstSeenAtUnixMs = recordedAt,
                ),
            )
            if (claimed == -1L) {
                return@withTransaction rejected(envelope.messageId, "DUPLICATE", recordedAt)
            }
            check(
                database.meshInboxDao().insert(
                    MeshInboxEntity(
                        messageId = envelope.messageId,
                        wireBytes = wireBytes.copyOf(),
                        senderNodeId = envelope.senderNodeId,
                        recipientNodeId = envelope.recipientNodeId,
                        expiresAtUnixMs = envelope.expiresAtUnixMs,
                        hopCount = envelope.hopCount,
                        hopLimit = envelope.hopLimit,
                        receivedAtUnixMs = recordedAt,
                    ),
                ) > 0,
            ) { "newly claimed message must be inserted into inbox" }

            if (envelope.recipientNodeId != localNodeId) {
                val relayEnvelope = envelope.toBuilder()
                    .setHopCount(envelope.hopCount + 1)
                    .build()
                database.outboxDao().enqueue(
                    MeshEnvelopeEntity(
                        messageId = relayEnvelope.messageId,
                        wireBytes = MeshWireCodec.encode(relayEnvelope),
                        priority = relayEnvelope.priorityValue,
                        expiresAtUnixMs = relayEnvelope.expiresAtUnixMs,
                        state = QueueState.PENDING.name,
                        attemptCount = 0,
                        nextAttemptAtUnixMs = recordedAt,
                    ),
                )
            }
            acknowledgement(
                messageId = envelope.messageId,
                status = AcknowledgementStatus.ACKNOWLEDGEMENT_STATUS_DURABLY_STORED,
                reason = "",
                recordedAt = recordedAt,
            )
        }
        if (envelope.recipientNodeId == localNodeId) {
            runCatching { localApplicationScheduler() }
        }
        return acknowledgement
    }

    private fun rejectionReason(envelope: Envelope, now: Long): String? = when {
        envelope.messageId.isBlank() -> "INVALID_MESSAGE_ID"
        envelope.schemaVersion != SUPPORTED_SCHEMA_VERSION -> "UNSUPPORTED_SCHEMA"
        envelope.minimumReaderVersion > SUPPORTED_SCHEMA_VERSION -> "UNSUPPORTED_READER_VERSION"
        envelope.senderNodeId.isBlank() || envelope.recipientNodeId.isBlank() -> "INVALID_ROUTE_METADATA"
        envelope.expiresAtUnixMs <= now -> "EXPIRED"
        envelope.hopLimit == 0 || envelope.hopCount >= envelope.hopLimit -> "HOP_LIMIT_REACHED"
        envelope.payloadSha256.size() != SHA_256_BYTES -> "INVALID_PAYLOAD_HASH"
        else -> null
    }

    private fun rejected(messageId: String, reason: String, recordedAt: Long): Acknowledgement =
        acknowledgement(
            messageId = messageId,
            status = AcknowledgementStatus.ACKNOWLEDGEMENT_STATUS_REJECTED,
            reason = reason,
            recordedAt = recordedAt,
        )

    private fun acknowledgement(
        messageId: String,
        status: AcknowledgementStatus,
        reason: String,
        recordedAt: Long,
    ): Acknowledgement = acknowledgementSigner.sign(
        Acknowledgement.newBuilder()
            .setMessageId(messageId)
            .setNodeId(localNodeId)
            .setStatus(status)
            .setReasonCode(reason)
            .setRecordedAtUnixMs(recordedAt)
            .build(),
    )

    companion object {
        private const val SUPPORTED_SCHEMA_VERSION = 1
        private const val SHA_256_BYTES = 32
        private const val MAX_ENVELOPE_BYTES = 1_048_576
    }
}
