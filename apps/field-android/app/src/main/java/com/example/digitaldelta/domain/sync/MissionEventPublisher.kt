package com.example.digitaldelta.domain.sync

import androidx.room.withTransaction
import com.example.digitaldelta.data.local.*
import com.example.digitaldelta.domain.identity.*
import com.example.digitaldelta.domain.mesh.*
import com.example.digitaldelta.proto.v1.*
import com.google.protobuf.ByteString
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.util.UUID

/** One durable transaction for the local projection and independently encrypted peer copies. */
class MissionEventPublisher(
    private val database: DeltaDatabase,
    private val profiles: DeviceProfileRepository,
    private val protector: MeshPayloadProtector,
    private val security: AndroidEnvelopeSecurity,
    private val afterCommit: () -> Unit = {},
) {
    suspend fun edit(missionId: String, field: MissionField, value: String): String = database.withTransaction {
        val profile = profiles.profile.first()
        val creation = creation(missionId)
        val clock = clock(missionId).increment(profile.nodeId)
        val event = event(profile, creation).setMissionFieldUpdated(MissionFieldUpdated.newBuilder()
            .setMissionId(missionId).setFieldCode(field.name).setValue(ByteString.copyFromUtf8(value)).setVectorClock(clock.toProto())).build()
        publish(event, creation, profile)
        event.eventId
    }.also { runCatching { afterCommit() } }

    suspend fun resolve(conflictId: String, side: ConflictSide): String = database.withTransaction {
        val conflict = requireNotNull(database.conflictDao().find(conflictId))
        require(conflict.state == "OPEN") { "Conflict already resolved" }
        val profile = profiles.profile.first()
        val creation = creation(conflict.missionId)
        val event = event(profile, creation).setConflictResolved(ConflictResolved.newBuilder()
            .setConflictId(conflictId).setMissionId(conflict.missionId)
            .setSelectedValue(ByteString.copyFromUtf8(if (side == ConflictSide.LEFT) conflict.leftValue else conflict.rightValue))
            .setResolverIdentityId(profile.identityId).setReasonCode("HUMAN_SAFETY_SELECTION")
            .setVectorClock(clock(conflict.missionId).increment(profile.nodeId).toProto())).build()
        publish(event, creation, profile)
        event.eventId
    }.also { runCatching { afterCommit() } }

    private suspend fun publish(event: DomainEvent, creation: DomainEvent, profile: LocalDeviceProfile) {
        val request = creation.reliefRequestCreated
        val recipients = setOf(request.requesterNodeId, request.destinationNodeId) - profile.nodeId
        require(recipients.isNotEmpty()) { "Mission needs an independent peer" }
        val bytes = event.toByteArray()
        var signedOrigin: Envelope? = null
        for (recipient in recipients.sorted()) {
            val id = UUID.randomUUID().toString()
            val now = event.occurredAtUnixMs
            val protected = protector.protect(recipient, bytes, "$id|${profile.nodeId}|$recipient|$now".encodeToByteArray())
            val envelope = security.sign(MeshWireCodec.createEnvelope(id, profile.nodeId, recipient, now, now + 72 * 60 * 60_000L,
                8, PriorityClass.PRIORITY_CLASS_P1, MessageDigest.getInstance("SHA-256").digest(bytes), event.simulated, event.scenarioSeed, protected))
            require(security.verify(envelope, now)) { "Local authority changed" }
            signedOrigin = envelope
            check(database.outboxDao().enqueue(MeshEnvelopeEntity(id, envelope.toByteArray(), envelope.priorityValue,
                envelope.expiresAtUnixMs, QueueState.PENDING.name, 0, now)) > 0)
        }
        ReceivedEventApplication(database).apply(event, requireNotNull(signedOrigin), profile.nodeId)
    }

    private suspend fun creation(missionId: String): DomainEvent = database.operationLogDao().forMission(missionId)
        .firstOrNull { it.eventType == "RELIEF_REQUEST_CREATED" }?.let { DomainEvent.parseFrom(it.payloadBytes) }
        ?: throw MissingEventDependency("Mission creation missing")
    private suspend fun clock(missionId: String) = database.missionProjectionDao().forMission(missionId)
        .map { row -> VectorClock(com.example.digitaldelta.proto.v1.VectorClock.parseFrom(row.vectorClockBytes).entriesList.associate { it.replicaId to it.counter }) }
        .fold(VectorClock(emptyMap())) { result, next -> result.merge(next) }
    private fun event(profile: LocalDeviceProfile, creation: DomainEvent) = DomainEvent.newBuilder()
        .setEventId(UUID.randomUUID().toString()).setSchemaVersion(1).setActorIdentityId(profile.identityId)
        .setOccurredAtUnixMs(System.currentTimeMillis()).setSimulated(creation.simulated).setScenarioSeed(creation.scenarioSeed)
    private fun VectorClock.toProto() = com.example.digitaldelta.proto.v1.VectorClock.newBuilder().addAllEntries(
        counters.toSortedMap().map { (id, counter) -> VectorClockEntry.newBuilder().setReplicaId(id).setCounter(counter).build() }).build()
}
