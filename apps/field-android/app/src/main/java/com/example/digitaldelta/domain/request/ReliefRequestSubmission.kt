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
    val participantNodeIds: Set<String> = emptySet(),
    val note: String = "",
)

data class QueueReceipt(val requestId: String, val messageId: String)

class InvalidRequestLocationException : IllegalArgumentException("Request endpoint is not in the offline graph")

interface ReliefRequestSubmission {
    suspend fun submit(draft: ReliefRequestDraft): QueueReceipt
}

interface RequestPersistence {
    suspend fun persist(operation: OperationEntity, envelope: MeshEnvelopeEntity)
    suspend fun persistAll(operation: OperationEntity, envelopes: List<MeshEnvelopeEntity>) {
        require(envelopes.size == 1) { "Persistence must support atomic fan-out" }
        persist(operation, envelopes.single())
    }
}

class RoomRequestPersistence(private val database: DeltaDatabase, private val applyLocalProjection: Boolean = false, private val afterCommit: () -> Unit = {}) : RequestPersistence {
    override suspend fun persist(operation: OperationEntity, envelope: MeshEnvelopeEntity) {
        persistAll(operation, listOf(envelope))
    }
    override suspend fun persistAll(operation: OperationEntity, envelopes: List<MeshEnvelopeEntity>) {
        database.withTransaction {
            if (applyLocalProjection) {
                val origin = MeshWireCodec.decode(envelopes.first().wireBytes)
                com.example.digitaldelta.domain.sync.ReceivedEventApplication(database).apply(DomainEvent.parseFrom(operation.payloadBytes), origin, origin.senderNodeId)
            } else database.operationLogDao().append(operation)
            envelopes.forEach { check(database.outboxDao().enqueue(it) > 0) { "duplicate mesh message id" } }
        }
        // Publication scheduling failure cannot roll back or fail a field request.
        runCatching { afterCommit() }
    }
}

class DefaultReliefRequestSubmission(
    private val persistence: RequestPersistence,
    private val payloadProtector: MeshPayloadProtector,
    private val nowUnixMs: () -> Long = System::currentTimeMillis,
    private val nextId: () -> String = { UUID.randomUUID().toString() },
    private val envelopeSigner: com.example.digitaldelta.domain.mesh.EnvelopeSigner = com.example.digitaldelta.domain.mesh.EnvelopeSigner { it },
    private val additionalParticipants: suspend (ReliefRequestDraft) -> Set<String> = { emptySet() },
    private val allowedLocationIds: Set<String>? = null,
) : ReliefRequestSubmission {
    override suspend fun submit(draft: ReliefRequestDraft): QueueReceipt {
        if (allowedLocationIds != null &&
            (draft.originNodeId !in allowedLocationIds || draft.destinationNodeId !in allowedLocationIds)) {
            throw InvalidRequestLocationException()
        }
        require(draft.cargo.isNotEmpty()) { "at least one cargo item is required" }
        require(draft.cargo.all { it.quantity > 0 }) { "cargo quantities must be positive" }
        require(draft.note.length <= 1000) { "Note is too long" }
        val now = nowUnixMs()
        val requestId = nextId()
        val eventId = nextId()
        val messageId = nextId()
        val participants = (draft.participantNodeIds + additionalParticipants(draft) + setOf(draft.requesterNodeId, draft.originNodeId, draft.destinationNodeId)).sorted()
        require(participants.size in 2..32)

        val request = ReliefRequestCreated.newBuilder()
            .setRequestId(requestId)
            .setRequesterNodeId(draft.requesterNodeId)
            .setOriginNodeId(draft.originNodeId)
            .setDestinationNodeId(draft.destinationNodeId)
            .setCreatedAtUnixMs(now)
            .addAllParticipantNodeIds(participants)
            .setNote(draft.note)
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
        val expiresAt = now + ttlMillis(draft.priority)
        val envelopes = (participants - draft.requesterNodeId).mapIndexed { index, recipient ->
        val peerMessageId = if (index == 0) messageId else nextId()
        val associatedData = "$peerMessageId|${draft.requesterNodeId}|$recipient|$now".encodeToByteArray()
        val protectedPayload = payloadProtector.protect(recipient, eventBytes, associatedData)
        val envelope = envelopeSigner.sign(MeshWireCodec.createEnvelope(
            messageId = peerMessageId,
            senderNodeId = draft.requesterNodeId,
            recipientNodeId = recipient,
            createdAtUnixMs = now,
            expiresAtUnixMs = expiresAt,
            hopLimit = 8,
            priority = draft.priority,
            payloadHash = payloadHash,
            simulated = draft.simulated,
            scenarioSeed = draft.scenarioSeed,
            protectedPayload = protectedPayload,
        ))
        MeshEnvelopeEntity(peerMessageId, MeshWireCodec.encode(envelope), draft.priority.number, expiresAt,
            QueueState.PENDING.name, 0, now)
        }

        persistence.persistAll(
            operation = OperationEntity(
                eventId = eventId,
                missionId = requestId,
                eventType = "RELIEF_REQUEST_CREATED",
                payloadBytes = eventBytes,
                createdAtUnixMs = now,
            ),
            envelopes = envelopes,
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
