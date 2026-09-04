package com.example.digitaldelta.domain.mesh

import androidx.room.withTransaction
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.data.local.MeshEnvelopeEntity
import com.example.digitaldelta.proto.v1.Acknowledgement
import com.example.digitaldelta.proto.v1.AcknowledgementStatus
import com.example.digitaldelta.proto.v1.PriorityClass

/** A radio-agnostic byte transport. Nearby Connections will implement this interface. */
interface PeerTransport {
    suspend fun send(peerId: String, wireBytes: ByteArray): Acknowledgement
}

data class DispatchReport(
    val attempted: Int,
    val acknowledged: Int,
    val retryScheduled: Int,
    val deadLettered: Int,
)

class MeshOutboxDispatcher(
    private val database: DeltaDatabase,
    private val transport: PeerTransport,
    private val acknowledgementVerifier: MeshAcknowledgementVerifier,
    private val nowUnixMs: () -> Long = System::currentTimeMillis,
) {
    suspend fun dispatch(peerId: String, limit: Int = 20): DispatchReport {
        require(peerId.isNotBlank()) { "peer id is required" }
        require(limit in 1..100) { "dispatch limit must be between 1 and 100" }
        val now = nowUnixMs()
        val expired = database.withTransaction {
            database.outboxDao().recoverInFlight(now)
            database.outboxDao().deadLetterExpired(now)
        }
        val pending = database.outboxDao().pending(now, limit)
        var attempted = 0
        var acknowledged = 0
        var retried = 0
        var deadLettered = expired

        for (item in pending) {
            if (database.outboxDao().markInFlight(item.messageId) == 0) continue
            attempted += 1
            val acknowledgement = runCatching { transport.send(peerId, item.wireBytes.copyOf()) }
                .getOrElse {
                    database.outboxDao().scheduleRetry(item.messageId, now + retryDelayMillis(item))
                    retried += 1
                    continue
                }
            if (acknowledgement.messageId != item.messageId ||
                !acknowledgementVerifier.verify(acknowledgement, peerId, nowUnixMs())
            ) {
                database.outboxDao().scheduleRetry(item.messageId, now + retryDelayMillis(item))
                retried += 1
                continue
            }
            when {
                acknowledgement.status == AcknowledgementStatus.ACKNOWLEDGEMENT_STATUS_DURABLY_STORED ||
                    acknowledgement.status == AcknowledgementStatus.ACKNOWLEDGEMENT_STATUS_APPLIED ||
                    (acknowledgement.status == AcknowledgementStatus.ACKNOWLEDGEMENT_STATUS_REJECTED &&
                        acknowledgement.reasonCode == "DUPLICATE") -> {
                    database.outboxDao().markAcknowledged(item.messageId, acknowledgement.recordedAtUnixMs)
                    acknowledged += 1
                }

                acknowledgement.status == AcknowledgementStatus.ACKNOWLEDGEMENT_STATUS_REJECTED -> {
                    database.outboxDao().markDeadLetter(item.messageId)
                    deadLettered += 1
                }

                else -> {
                    database.outboxDao().scheduleRetry(item.messageId, now + retryDelayMillis(item))
                    retried += 1
                }
            }
        }
        return DispatchReport(attempted, acknowledged, retried, deadLettered)
    }

    private fun retryDelayMillis(item: MeshEnvelopeEntity): Long {
        val priority = MeshWireCodec.decode(item.wireBytes).priority
        val base = if (priority == PriorityClass.PRIORITY_CLASS_P0) 2_000L else 5_000L
        return base * (1L shl item.attemptCount.coerceAtMost(5))
    }
}
