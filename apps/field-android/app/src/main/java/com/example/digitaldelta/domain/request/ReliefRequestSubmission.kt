package com.example.digitaldelta.domain.request

import androidx.room.withTransaction
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.data.local.MeshEnvelopeEntity
import com.example.digitaldelta.data.local.OperationEntity
import com.example.digitaldelta.data.local.QueueState
import com.example.digitaldelta.domain.mesh.MeshPayloadProtector
import com.example.digitaldelta.domain.mesh.MeshWireCodec
import com.example.digitaldelta.proto.v1.CargoItem
import com.example.digitaldelta.proto.v1.DomainEvent
import com.example.digitaldelta.proto.v1.PriorityClass
import com.example.digitaldelta.proto.v1.ReliefRequestCreated
import java.security.MessageDigest
import java.util.UUID

data class CargoDraft(
    val itemCode: String,
    val quantity: Int,
    val unitCode: String,
)

data class ReliefRequestDraft(
    val requesterNodeId: String,
    val originNodeId: String,
    val destinationNodeId: String,
    val cargo: List<CargoDraft>,
    val priority: PriorityClass,
    val simulated: Boolean,
    val scenarioSeed: String,
    val requesterIdentityId: String = requesterNodeId,
)

data class QueueReceipt(val requestId: String, val messageId: String)

interface ReliefRequestSubmission {
    suspend fun submit(draft: ReliefRequestDraft): QueueReceipt
}

interface RequestPersistence {
    suspend fun persist(operation: OperationEntity, envelope: MeshEnvelopeEntity)
}

class RoomRequestPersistence(private val database: DeltaDatabase) : RequestPersistence {
    override suspend fun persist(operation: OperationEntity, envelope: MeshEnvelopeEntity) {
        database.withTransaction {
            database.operationLogDao().append(operation)
            check(database.outboxDao().enqueue(envelope) > 0) { "duplicate mesh message id" }
        }
    }
}

class DefaultReliefRequestSubmission(
    private val persistence: RequestPersistence,
    private val payloadProtector: MeshPayloadProtector,
    private val nowUnixMs: () -> Long = System::currentTimeMillis,
    private val nextId: () -> String = { UUID.randomUUID().toString() },
    private val envelopeSigner: com.example.digitaldelta.domain.mesh.EnvelopeSigner = com.example.digitaldelta.domain.mesh.EnvelopeSigner { it },
) : ReliefRequestSubmission {
    override suspend fun submit(draft: ReliefRequestDraft): QueueReceipt {
        require(draft.cargo.isNotEmpty()) { "at least one cargo item is required" }
        require(draft.cargo.all { it.quantity > 0 }) { "cargo quantities must be positive" }
        val now = nowUnixMs()
        val requestId = nextId()
        val eventId = nextId()
        val messageId = nextId()

        val request = ReliefRequestCreated.newBuilder()
            .setRequestId(requestId)
            .setRequesterNodeId(draft.requesterNodeId)
            .setOriginNodeId(draft.originNodeId)
            .setDestinationNodeId(draft.destinationNodeId)
            .setCreatedAtUnixMs(now)
            .addAllCargo(
                draft.cargo.mapIndexed { index, item ->
                    CargoItem.newBuilder()
                        .setCargoId("$requestId-$index")
                        .setItemCode(item.itemCode)
                        .setQuantity(item.quantity)
                        .setUnitCode(item.unitCode)
                        .setPriority(draft.priority)
                        .build()
                },
            )
            .build()
        val event = DomainEvent.newBuilder()
            .setEventId(eventId)
            .setSchemaVersion(1)
            .setActorIdentityId(draft.requesterIdentityId)
            .setOccurredAtUnixMs(now)
            .setSimulated(draft.simulated)
            .setScenarioSeed(draft.scenarioSeed)
            .setReliefRequestCreated(request)
            .build()
        val eventBytes = event.toByteArray()
        val payloadHash = sha256(eventBytes)
        val associatedData = "$messageId|${draft.requesterNodeId}|${draft.destinationNodeId}|$now".encodeToByteArray()
        val protectedPayload = payloadProtector.protect(draft.destinationNodeId, eventBytes, associatedData)
        val expiresAt = now + ttlMillis(draft.priority)
        val envelope = envelopeSigner.sign(MeshWireCodec.createEnvelope(
            messageId = messageId,
            senderNodeId = draft.requesterNodeId,
            recipientNodeId = draft.destinationNodeId,
            createdAtUnixMs = now,
            expiresAtUnixMs = expiresAt,
            hopLimit = 8,
            priority = draft.priority,
            payloadHash = payloadHash,
            simulated = draft.simulated,
            scenarioSeed = draft.scenarioSeed,
            protectedPayload = protectedPayload,
        ))

        persistence.persist(
            operation = OperationEntity(
                eventId = eventId,
                missionId = requestId,
                eventType = "RELIEF_REQUEST_CREATED",
                payloadBytes = eventBytes,
                createdAtUnixMs = now,
            ),
            envelope = MeshEnvelopeEntity(
                messageId = messageId,
                wireBytes = MeshWireCodec.encode(envelope),
                priority = draft.priority.number,
                expiresAtUnixMs = expiresAt,
                state = QueueState.PENDING.name,
                attemptCount = 0,
                nextAttemptAtUnixMs = now,
            ),
        )
        return QueueReceipt(requestId, messageId)
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun ttlMillis(priority: PriorityClass): Long = when (priority) {
        PriorityClass.PRIORITY_CLASS_P0 -> 2 * 60 * 60 * 1_000L
        PriorityClass.PRIORITY_CLASS_P1 -> 6 * 60 * 60 * 1_000L
        PriorityClass.PRIORITY_CLASS_P2 -> 24 * 60 * 60 * 1_000L
        PriorityClass.PRIORITY_CLASS_P3 -> 72 * 60 * 60 * 1_000L
        else -> error("priority must be P0-P3")
    }
}
