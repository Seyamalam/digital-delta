package com.example.digitaldelta.domain.sync

import androidx.room.withTransaction
import com.example.digitaldelta.data.local.*
import com.example.digitaldelta.proto.v1.DomainEvent
import com.example.digitaldelta.proto.v1.IdentityRole
import com.example.digitaldelta.proto.v1.Envelope
import java.security.MessageDigest

/** Valid event whose causal prerequisite has not reached this replica yet. */
class MissingEventDependency(message: String) : IllegalStateException(message)

/** Applies authenticated events to Room atomically; transport receipt alone never means acceptance. */
class ReceivedEventApplication(private val database: DeltaDatabase) {
    suspend fun apply(event: DomainEvent, envelope: Envelope, localNodeId: String): Boolean {
        require(event.schemaVersion == 1 && event.eventId.isNotBlank())
        require(event.simulated == envelope.simulated && event.scenarioSeed == envelope.scenarioSeed) { "Event provenance differs from signed envelope" }
        val claims = envelope.senderCredential.claims
        require(event.actorIdentityId == claims.identityId) { "Event actor does not match origin credential" }
        val role = claims.role
        require(role in setOf(IdentityRole.IDENTITY_ROLE_COORDINATOR, IdentityRole.IDENTITY_ROLE_CLINIC, IdentityRole.IDENTITY_ROLE_HOSPITAL))
        val missionId = when {
            event.hasReliefRequestCreated() -> event.reliefRequestCreated.requestId
            event.hasMissionFieldUpdated() -> event.missionFieldUpdated.missionId
            event.hasConflictResolved() -> event.conflictResolved.missionId
            else -> return false
        }
        require(missionId.isNotBlank())
        database.withTransaction {
            val previous = database.operationLogDao().find(event.eventId)
            if (previous != null) {
                require(previous.payloadBytes.contentEquals(event.toByteArray())) { "Event ID reused with different contents" }
                return@withTransaction
            }
            if (event.hasReliefRequestCreated()) {
                val request = event.reliefRequestCreated
                val receiver = database.recipientKeyDao().findByNodeId(localNodeId)
                require(request.requesterNodeId == envelope.senderNodeId)
                require(localNodeId in setOf(request.requesterNodeId, request.destinationNodeId) || receiver?.roleCode == IdentityRole.IDENTITY_ROLE_COORDINATOR.name)
                require(request.cargoCount in 1..100 && request.cargoList.all { it.quantity in 1..100000 && it.priorityValue in 1..4 && it.itemCode.isNotBlank() })
                require(database.operationLogDao().forMission(missionId).none { it.eventType == "RELIEF_REQUEST_CREATED" }) { "Request ID reused" }
                val clock = VectorClock(mapOf(envelope.senderNodeId to 1))
                store(FieldRevision(event.eventId, missionId, MissionField.DESTINATION, request.destinationNodeId, clock, event.occurredAtUnixMs))
                store(FieldRevision(event.eventId, missionId, MissionField.PRIORITY, request.cargoList.minOf { it.priorityValue }.toString(), clock, event.occurredAtUnixMs))
                val medicalQuantity = request.cargoList.filter { it.itemCode in setOf("medicine", "ors", "blood") }.sumOf { it.quantity.toLong() }
                store(FieldRevision(event.eventId, missionId, MissionField.MEDICAL_QUANTITY, medicalQuantity.toString(), clock, event.occurredAtUnixMs))
            } else {
                val resolution = event.conflictResolved.takeIf { event.hasConflictResolved() }
                val conflict = resolution?.let {
                    require(role == IdentityRole.IDENTITY_ROLE_COORDINATOR && it.resolverIdentityId == event.actorIdentityId)
                    database.conflictDao().find(it.conflictId) ?: throw MissingEventDependency("Conflicting revisions have not arrived")
                }
                val update = if (resolution != null && conflict != null) {
                    require(conflict.missionId == missionId && resolution.selectedValue.isValidUtf8 && resolution.reasonCode.isNotBlank())
                    require(resolution.selectedValue.toStringUtf8() in setOf(conflict.leftValue, conflict.rightValue))
                    require(decode(resolution.vectorClock.toByteArray()).compare(decode(conflict.mergedClockBytes)) == ClockRelation.AFTER)
                    com.example.digitaldelta.proto.v1.MissionFieldUpdated.newBuilder().setMissionId(missionId)
                        .setFieldCode(conflict.fieldCode).setValue(resolution.selectedValue).setVectorClock(resolution.vectorClock).build()
                } else event.missionFieldUpdated
                val creation = database.operationLogDao().forMission(missionId)
                    .firstOrNull { it.eventType == "RELIEF_REQUEST_CREATED" }
                    ?.let { DomainEvent.parseFrom(it.payloadBytes) }
                if (creation == null) throw MissingEventDependency("Mission creation has not arrived")
                require(role == IdentityRole.IDENTITY_ROLE_COORDINATOR || creation.actorIdentityId == event.actorIdentityId ||
                    (role == IdentityRole.IDENTITY_ROLE_HOSPITAL && creation.reliefRequestCreated.destinationNodeId == envelope.senderNodeId)) {
                    "Only mission participants or a coordinator may change a mission"
                }
                require(update.value.size() in 1..4096 && update.vectorClock.entriesCount in 1..100)
                val entries = update.vectorClock.entriesList
                require(entries.map { it.replicaId }.distinct().size == entries.size)
                val clock = VectorClock(entries.associate { it.replicaId to it.counter })
                require((clock.counters[envelope.senderNodeId] ?: 0) > 0)
                val field = MissionField.valueOf(update.fieldCode)
                require(update.value.isValidUtf8)
                val value = update.value.toStringUtf8()
                when (field) {
                    MissionField.PRIORITY -> require(value.toIntOrNull() in 1..4)
                    MissionField.MEDICAL_QUANTITY -> require(value.toLongOrNull()?.let { it in 0..10_000_000 } == true)
                    MissionField.DESTINATION -> require(value.matches(Regex("[A-Za-z0-9_-]{1,128}")))
                    MissionField.DESCRIPTION -> require(value.isNotBlank())
                }
                val incoming = FieldRevision(event.eventId, missionId, field, value, clock, event.occurredAtUnixMs)
                val existing = database.missionProjectionDao().find(missionId, field.name)?.let { row ->
                    FieldRevision(row.sourceEventId, row.missionId, field, row.value, decode(row.vectorClockBytes), row.updatedAtUnixMs)
                }
                when (val merged = MissionMergeEngine().merge(existing, incoming)) {
                    is MergeDecision.Applied -> store(merged.revision)
                    is MergeDecision.NeedsReview -> {
                        val id = "conflict-" + digest("${merged.left.eventId}|${merged.right.eventId}".toByteArray())
                        if (database.conflictDao().find(id) == null) database.conflictDao().insert(ConflictEntity(
                            id, missionId, field.name, merged.left.eventId, merged.left.value, encode(merged.left.clock),
                            merged.right.eventId, merged.right.value, encode(merged.right.clock), encode(merged.mergedClock),
                            "OPEN", null, null, null, maxOf(merged.left.occurredAtUnixMs, merged.right.occurredAtUnixMs), null,
                        ))
                        // Both replicas retain the same provisional value, with an explicit unresolved conflict.
                        store(merged.left.copy(clock = merged.mergedClock))
                    }
                }
                if (conflict != null) database.conflictDao().resolve(
                    conflict.conflictId, resolution.selectedValue.toStringUtf8(), event.actorIdentityId,
                    resolution.reasonCode, event.occurredAtUnixMs,
                )
            }
            database.operationLogDao().append(OperationEntity(event.eventId, missionId, event.bodyCase.name, event.toByteArray(), event.occurredAtUnixMs))
            val canonical = database.missionProjectionDao().forMission(missionId).joinToString("\n") { "${it.fieldCode}:${it.value}:${decode(it.vectorClockBytes).convergenceHash()}" }
            database.missionProjectionDao().updateConvergenceHash(missionId, digest(canonical.toByteArray()))
        }
        return true
    }

    private suspend fun store(value: FieldRevision) = database.missionProjectionDao().upsert(MissionProjectionEntity(
        value.missionId, value.field.name, value.value, encode(value.clock), value.eventId, value.occurredAtUnixMs, "",
    ))
    private fun decode(bytes: ByteArray) = VectorClock(com.example.digitaldelta.proto.v1.VectorClock.parseFrom(bytes).entriesList.associate { it.replicaId to it.counter })
    private fun encode(clock: VectorClock): ByteArray = com.example.digitaldelta.proto.v1.VectorClock.newBuilder().addAllEntries(
        clock.counters.toSortedMap().map { (id, counter) -> com.example.digitaldelta.proto.v1.VectorClockEntry.newBuilder().setReplicaId(id).setCounter(counter).build() },
    ).build().toByteArray()
    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
