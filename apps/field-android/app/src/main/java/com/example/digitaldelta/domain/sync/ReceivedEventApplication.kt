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
            event.hasCustodyReconciled() -> event.custodyReconciled.missionId
            else -> return false
        }
        require(missionId.isNotBlank())
        database.withTransaction {
            val previous = database.operationLogDao().find(event.eventId)
            if (previous != null) {
                require(previous.payloadBytes.contentEquals(event.toByteArray())) { "Event ID reused with different contents" }
                return@withTransaction
            }
            if (event.hasCustodyReconciled()) {
                val decision = event.custodyReconciled
                require(role == IdentityRole.IDENTITY_ROLE_COORDINATOR)
                require(decision.outcomeCode == "RETAIN_SIGNED_CUSTODY" && decision.reason.length in 8..1000)
                require(decision.reviewedEventIdsCount in 1..256 && decision.reviewedEventIdsList == decision.reviewedEventIdsList.distinct().sorted())
                val receipt = database.operationLogDao().find(decision.receiptEventId) ?: throw MissingEventDependency("Receipt unavailable")
                require(receipt.missionId == missionId && receipt.eventType == "CUSTODY_TRANSFER")
                for (id in decision.reviewedEventIdsList) {
                    val reviewed = database.operationLogDao().find(id) ?: throw MissingEventDependency("Reviewed revision unavailable")
                    require(reviewed.missionId == missionId && reviewed.eventType in setOf("RELIEF_REQUEST_CREATED", "MISSION_FIELD_UPDATED", "CONFLICT_RESOLVED"))
                }
                database.operationLogDao().append(OperationEntity(event.eventId, missionId, event.bodyCase.name, event.toByteArray(), event.occurredAtUnixMs))
                return@withTransaction
            }
            if (event.hasReliefRequestCreated()) {
                val request = event.reliefRequestCreated
                val receiver = database.recipientKeyDao().findByNodeId(localNodeId)
                require(request.requesterNodeId == envelope.senderNodeId)
                require(request.participantNodeIdsCount <= 32)
                require(request.note.length <= 1000)
                require(localNodeId in request.participantNodeIdsList || localNodeId in setOf(request.requesterNodeId, request.originNodeId, request.destinationNodeId) || receiver?.roleCode == IdentityRole.IDENTITY_ROLE_COORDINATOR.name)
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
                    require(resolution.fieldCode.isEmpty() || resolution.fieldCode == conflict.fieldCode)
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
                    MissionField.CUSTODY_PATH -> {
                        require(role == IdentityRole.IDENTITY_ROLE_COORDINATOR)
                        val path = value.split(">")
                        val request = creation.reliefRequestCreated
                        require(path.size in 2..8 && path.distinct().size == path.size && path.first() == request.originNodeId)
                        val members = request.participantNodeIdsList.toSet() + setOf(request.originNodeId, request.destinationNodeId, request.requesterNodeId)
                        require(path.all { it in members }) { "Custody path requires authorized mission readers" }
                        for (node in path.drop(1).dropLast(1)) {
                            val credential = database.recipientKeyDao().findByNodeId(node) ?: throw MissingEventDependency("Driver credential unavailable")
                            require(credential.roleCode == IdentityRole.IDENTITY_ROLE_DRIVER.name || credential.roleCode == IdentityRole.IDENTITY_ROLE_COORDINATOR.name)
                        }
                    }
                }
                val incoming = FieldRevision(event.eventId, missionId, field, value, clock, event.occurredAtUnixMs)
                val history = database.operationLogDao().forMission(missionId).map { DomainEvent.parseFrom(it.payloadBytes) }
                val original = creation.reliefRequestCreated
                val initialValue = when (field) {
                    MissionField.DESTINATION -> original.destinationNodeId
                    MissionField.PRIORITY -> original.cargoList.minOf { it.priorityValue }.toString()
                    MissionField.MEDICAL_QUANTITY -> original.cargoList.filter { it.itemCode in setOf("medicine", "ors", "blood") }.sumOf { it.quantity.toLong() }.toString()
                    MissionField.DESCRIPTION -> null
                    MissionField.CUSTODY_PATH -> null
                }
                val revisions = mutableListOf(incoming)
                if (initialValue != null) revisions += FieldRevision(creation.eventId, missionId, field, initialValue, VectorClock(mapOf(original.requesterNodeId to 1)), creation.occurredAtUnixMs)
                for (past in history) {
                    if (past.hasMissionFieldUpdated() && past.missionFieldUpdated.fieldCode == field.name) {
                        val value = past.missionFieldUpdated
                        revisions += FieldRevision(past.eventId, missionId, field, value.value.toStringUtf8(), value.vectorClock.toDomainClock(), past.occurredAtUnixMs)
                    } else if (past.hasConflictResolved() && database.conflictDao().find(past.conflictResolved.conflictId)?.fieldCode == field.name) {
                        val value = past.conflictResolved
                        revisions += FieldRevision(past.eventId, missionId, field, value.selectedValue.toStringUtf8(), value.vectorClock.toDomainClock(), past.occurredAtUnixMs)
                    }
                }
                val projected = projectRevisions(revisions)
                for (pair in projected.conflicts) {
                        val id = "conflict-" + digest("${pair.left.eventId}|${pair.right.eventId}".toByteArray())
                        val mergedClock = pair.left.clock.merge(pair.right.clock)
                        if (database.conflictDao().find(id) == null) database.conflictDao().insert(ConflictEntity(
                            id, missionId, field.name, pair.left.eventId, pair.left.value, encode(pair.left.clock),
                            pair.right.eventId, pair.right.value, encode(pair.right.clock), encode(mergedClock),
                            "OPEN", null, null, null, maxOf(pair.left.occurredAtUnixMs, pair.right.occurredAtUnixMs), null,
                        ))
                        database.conflictDao().reconcileState(id, if (pair.active) "OPEN" else "SUPERSEDED")
                }
                store(projected.revision)
                // Concurrent human resolutions retain one deterministic audit annotation;
                // their competing values remain separate revisions requiring a new review.
                for ((id, choices) in (history + event).filter { it.hasConflictResolved() }.groupBy { it.conflictResolved.conflictId }) {
                    val choice = choices.minBy { it.eventId }
                    database.conflictDao().resolve(id, choice.conflictResolved.selectedValue.toStringUtf8(), choice.actorIdentityId,
                        choice.conflictResolved.reasonCode, choice.occurredAtUnixMs)
                }
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
    private fun decode(bytes: ByteArray) = com.example.digitaldelta.proto.v1.VectorClock.parseFrom(bytes).toDomainClock()
    private fun encode(clock: VectorClock): ByteArray = clock.toProto().toByteArray()
    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
