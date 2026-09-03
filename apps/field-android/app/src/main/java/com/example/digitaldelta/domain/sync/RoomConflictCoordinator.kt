package com.example.digitaldelta.domain.sync

import androidx.room.withTransaction
import com.example.digitaldelta.data.local.ConflictEntity
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.data.local.MissionProjectionEntity
import com.example.digitaldelta.data.local.OperationEntity
import com.example.digitaldelta.proto.v1.ConflictRaised
import com.example.digitaldelta.proto.v1.ConflictResolved
import com.example.digitaldelta.proto.v1.DomainEvent
import com.example.digitaldelta.proto.v1.MissionFieldUpdated
import com.example.digitaldelta.proto.v1.VectorClockEntry
import com.google.protobuf.ByteString
import java.security.MessageDigest

enum class ConflictSide { LEFT, RIGHT }

sealed interface MissionConflictSnapshot {
    data object Idle : MissionConflictSnapshot

    data class Open(
        val conflictId: String,
        val missionId: String,
        val field: MissionField,
        val leftValue: String,
        val rightValue: String,
        val leftClock: VectorClock,
        val rightClock: VectorClock,
    ) : MissionConflictSnapshot

    data class Resolved(
        val conflictId: String,
        val missionId: String,
        val field: MissionField,
        val selectedValue: String,
        val resolverIdentityId: String,
        val convergenceHash: String,
    ) : MissionConflictSnapshot
}

interface ConflictCoordinator {
    suspend fun snapshot(): MissionConflictSnapshot
    suspend fun simulateDestinationConflict(): MissionConflictSnapshot
    suspend fun resolve(
        conflictId: String,
        selectedSide: ConflictSide,
        resolverIdentityId: String,
    ): MissionConflictSnapshot
}

class RoomConflictCoordinator(
    private val database: DeltaDatabase,
    private val mergeEngine: MissionMergeEngine = MissionMergeEngine(),
    private val nowUnixMs: () -> Long = System::currentTimeMillis,
) : ConflictCoordinator {
    override suspend fun snapshot(): MissionConflictSnapshot {
        val conflict = database.conflictDao().latest() ?: return MissionConflictSnapshot.Idle
        return conflict.toSnapshot(database)
    }

    override suspend fun simulateDestinationConflict(): MissionConflictSnapshot {
        database.withTransaction {
            val previous = database.conflictDao().latestForMission(DEMO_MISSION_ID)
            if (previous?.state == STATE_OPEN) return@withTransaction
            val now = nowUnixMs()
            val generation = database.conflictDao().countForMission(DEMO_MISSION_ID) + 1
            val generationCode = generation.toString().padStart(3, '0')
            val leftEventId = "m2-demo-$generationCode-phone-a-destination"
            val rightEventId = "m2-demo-$generationCode-phone-b-destination"
            val conflictId = "conflict-$DEMO_MISSION_ID-destination-$generationCode"
            val left = FieldRevision(
                eventId = leftEventId,
                missionId = DEMO_MISSION_ID,
                field = MissionField.DESTINATION,
                value = "N3",
                clock = VectorClock(mapOf("phone-a" to 2L, "phone-b" to 1L)),
                occurredAtUnixMs = now,
            )
            val right = FieldRevision(
                eventId = rightEventId,
                missionId = DEMO_MISSION_ID,
                field = MissionField.DESTINATION,
                value = "N6",
                clock = VectorClock(mapOf("phone-a" to 1L, "phone-b" to 2L)),
                occurredAtUnixMs = now + 1,
            )

            appendRevision(left)
            appendRevision(right)
            val initial = mergeEngine.merge(null, left) as MergeDecision.Applied
            upsertProjection(initial.revision)
            val conflict = mergeEngine.merge(initial.revision, right) as MergeDecision.NeedsReview
            database.conflictDao().insert(conflict.toEntity(conflictId, now + 2))
            appendConflictRaised(conflictId, conflict, now + 2)
            refreshConvergenceHash(DEMO_MISSION_ID)
        }
        return snapshot()
    }

    override suspend fun resolve(
        conflictId: String,
        selectedSide: ConflictSide,
        resolverIdentityId: String,
    ): MissionConflictSnapshot {
        require(resolverIdentityId.isNotBlank())
        database.withTransaction {
            val conflict = requireNotNull(database.conflictDao().find(conflictId)) { "unknown conflict" }
            if (conflict.state == STATE_RESOLVED) return@withTransaction
            val selectedValue = when (selectedSide) {
                ConflictSide.LEFT -> conflict.leftValue
                ConflictSide.RIGHT -> conflict.rightValue
            }
            val now = nowUnixMs()
            val resolutionEventId = "resolve-$conflictId"
            upsertProjection(
                FieldRevision(
                    eventId = resolutionEventId,
                    missionId = conflict.missionId,
                    field = MissionField.valueOf(conflict.fieldCode),
                    value = selectedValue,
                    clock = decodeClock(conflict.mergedClockBytes),
                    occurredAtUnixMs = now,
                ),
            )
            check(
                database.conflictDao().resolve(
                    conflictId = conflictId,
                    selectedValue = selectedValue,
                    resolverIdentityId = resolverIdentityId,
                    reasonCode = RESOLUTION_REASON,
                    resolvedAtUnixMs = now,
                ) == 1,
            )
            appendResolution(conflict, selectedValue, resolverIdentityId, now)
            refreshConvergenceHash(conflict.missionId)
        }
        return snapshot()
    }

    private suspend fun appendRevision(revision: FieldRevision) {
        val update = MissionFieldUpdated.newBuilder()
            .setMissionId(revision.missionId)
            .setFieldCode(revision.field.name)
            .setValue(ByteString.copyFromUtf8(revision.value))
            .setVectorClock(revision.clock.toProto())
            .build()
        database.operationLogDao().append(
            OperationEntity(
                eventId = revision.eventId,
                missionId = revision.missionId,
                eventType = "MISSION_FIELD_UPDATED",
                payloadBytes = event(revision.eventId, revision.occurredAtUnixMs)
                    .setMissionFieldUpdated(update)
                    .build()
                    .toByteArray(),
                createdAtUnixMs = revision.occurredAtUnixMs,
            ),
        )
    }

    private suspend fun appendConflictRaised(
        conflictId: String,
        decision: MergeDecision.NeedsReview,
        occurredAtUnixMs: Long,
    ) {
        val raised = ConflictRaised.newBuilder()
            .setConflictId(conflictId)
            .setMissionId(decision.left.missionId)
            .setFieldCode(decision.left.field.name)
            .setLeftValue(ByteString.copyFromUtf8(decision.left.value))
            .setRightValue(ByteString.copyFromUtf8(decision.right.value))
            .setLeftClock(decision.left.clock.toProto())
            .setRightClock(decision.right.clock.toProto())
            .setRequiresHumanResolution(true)
            .build()
        val eventId = "raised-$conflictId"
        database.operationLogDao().append(
            OperationEntity(
                eventId = eventId,
                missionId = decision.left.missionId,
                eventType = "CONFLICT_RAISED",
                payloadBytes = event(eventId, occurredAtUnixMs).setConflictRaised(raised).build().toByteArray(),
                createdAtUnixMs = occurredAtUnixMs,
            ),
        )
    }

    private suspend fun appendResolution(
        conflict: ConflictEntity,
        selectedValue: String,
        resolverIdentityId: String,
        occurredAtUnixMs: Long,
    ) {
        val resolution = ConflictResolved.newBuilder()
            .setConflictId(conflict.conflictId)
            .setSelectedValue(ByteString.copyFromUtf8(selectedValue))
            .setResolverIdentityId(resolverIdentityId)
            .setReasonCode(RESOLUTION_REASON)
            .build()
        val eventId = "resolve-${conflict.conflictId}"
        database.operationLogDao().append(
            OperationEntity(
                eventId = eventId,
                missionId = conflict.missionId,
                eventType = "CONFLICT_RESOLVED",
                payloadBytes = event(eventId, occurredAtUnixMs).setConflictResolved(resolution).build().toByteArray(),
                createdAtUnixMs = occurredAtUnixMs,
            ),
        )
    }

    private fun event(eventId: String, occurredAtUnixMs: Long): DomainEvent.Builder = DomainEvent.newBuilder()
        .setEventId(eventId)
        .setSchemaVersion(1)
        .setActorIdentityId("conflict-demo")
        .setOccurredAtUnixMs(occurredAtUnixMs)
        .setSimulated(true)
        .setScenarioSeed(SCENARIO_SEED)

    private suspend fun upsertProjection(revision: FieldRevision) {
        database.missionProjectionDao().upsert(
            MissionProjectionEntity(
                missionId = revision.missionId,
                fieldCode = revision.field.name,
                value = revision.value,
                vectorClockBytes = revision.clock.toProto().toByteArray(),
                sourceEventId = revision.eventId,
                updatedAtUnixMs = revision.occurredAtUnixMs,
                convergenceHash = "",
            ),
        )
    }

    private suspend fun refreshConvergenceHash(missionId: String) {
        val canonical = buildString {
            append(POLICY_VERSION)
            database.missionProjectionDao().forMission(missionId).forEach { projection ->
                append('\n')
                append(projection.fieldCode)
                append(':')
                append(projection.value)
                append(':')
                append(decodeClock(projection.vectorClockBytes).convergenceHash())
            }
        }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        database.missionProjectionDao().updateConvergenceHash(missionId, hash)
    }

    private fun MergeDecision.NeedsReview.toEntity(
        conflictId: String,
        createdAtUnixMs: Long,
    ): ConflictEntity = ConflictEntity(
        conflictId = conflictId,
        missionId = left.missionId,
        fieldCode = left.field.name,
        leftEventId = left.eventId,
        leftValue = left.value,
        leftClockBytes = left.clock.toProto().toByteArray(),
        rightEventId = right.eventId,
        rightValue = right.value,
        rightClockBytes = right.clock.toProto().toByteArray(),
        mergedClockBytes = mergedClock.toProto().toByteArray(),
        state = STATE_OPEN,
        selectedValue = null,
        resolverIdentityId = null,
        reasonCode = null,
        createdAtUnixMs = createdAtUnixMs,
        resolvedAtUnixMs = null,
    )

    companion object {
        private const val DEMO_MISSION_ID = "mission-sylhet-01"
        private const val SCENARIO_SEED = "m2-conflict-demo-v1"
        private const val POLICY_VERSION = "m2-selective-crdt-v1"
        private const val STATE_OPEN = "OPEN"
        private const val STATE_RESOLVED = "RESOLVED"
        private const val RESOLUTION_REASON = "HUMAN_SAFETY_SELECTION"
    }
}

private suspend fun ConflictEntity.toSnapshot(database: DeltaDatabase): MissionConflictSnapshot =
    if (state == "OPEN") {
        MissionConflictSnapshot.Open(
            conflictId = conflictId,
            missionId = missionId,
            field = MissionField.valueOf(fieldCode),
            leftValue = leftValue,
            rightValue = rightValue,
            leftClock = decodeClock(leftClockBytes),
            rightClock = decodeClock(rightClockBytes),
        )
    } else {
        val projection = requireNotNull(database.missionProjectionDao().find(missionId, fieldCode))
        MissionConflictSnapshot.Resolved(
            conflictId = conflictId,
            missionId = missionId,
            field = MissionField.valueOf(fieldCode),
            selectedValue = requireNotNull(selectedValue),
            resolverIdentityId = requireNotNull(resolverIdentityId),
            convergenceHash = projection.convergenceHash,
        )
    }

private fun VectorClock.toProto(): com.example.digitaldelta.proto.v1.VectorClock =
    com.example.digitaldelta.proto.v1.VectorClock.newBuilder()
        .addAllEntries(
            counters.toSortedMap().map { (replicaId, counter) ->
                VectorClockEntry.newBuilder().setReplicaId(replicaId).setCounter(counter).build()
            },
        )
        .build()

private fun decodeClock(bytes: ByteArray): VectorClock =
    com.example.digitaldelta.proto.v1.VectorClock.parseFrom(bytes).entriesList
        .associate { it.replicaId to it.counter }
        .let(::VectorClock)
