package com.example.digitaldelta.domain.triage

import com.example.digitaldelta.data.local.CargoAssignmentEntity
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.domain.sync.VectorClock
import com.example.digitaldelta.proto.v1.VectorClockEntry
import java.security.MessageDigest

data class CargoAssignmentMutation(
    val missionId: String,
    val cargoId: String,
    val priority: CargoPriority,
    val assignedNodeId: String,
    val state: String,
    val sourceEventId: String,
    val assignedByIdentityId: String,
    val occurredAtUnixMs: Long,
) {
    init {
        require(missionId.isNotBlank())
        require(cargoId.isNotBlank())
        require(assignedNodeId.isNotBlank())
        require(state.isNotBlank())
        require(sourceEventId.isNotBlank())
        require(assignedByIdentityId.isNotBlank())
        require(occurredAtUnixMs >= 0)
    }
}

fun interface CargoAssignmentProjector {
    suspend fun apply(mutation: CargoAssignmentMutation): CargoAssignmentEntity
}

class RoomCargoAssignmentProjector(
    private val database: DeltaDatabase,
) : CargoAssignmentProjector {
    override suspend fun apply(mutation: CargoAssignmentMutation): CargoAssignmentEntity {
        val dao = database.cargoAssignmentDao()
        val previous = dao.find(mutation.missionId, mutation.cargoId)
        val nextClock = previous?.vectorClockBytes
            ?.let(::decodeClock)
            ?: VectorClock.EMPTY
            .increment(mutation.assignedByIdentityId)
        dao.upsert(
            CargoAssignmentEntity(
                missionId = mutation.missionId,
                cargoId = mutation.cargoId,
                priorityCode = mutation.priority.name,
                assignedNodeId = mutation.assignedNodeId,
                state = mutation.state,
                vectorClockBytes = nextClock.toProtoBytes(),
                sourceEventId = mutation.sourceEventId,
                assignedByIdentityId = mutation.assignedByIdentityId,
                updatedAtUnixMs = mutation.occurredAtUnixMs,
                convergenceHash = "",
            ),
        )
        val convergenceHash = convergenceHash(dao.forMission(mutation.missionId))
        dao.updateConvergenceHash(mutation.missionId, convergenceHash)
        return requireNotNull(dao.find(mutation.missionId, mutation.cargoId))
    }

    private fun convergenceHash(assignments: List<CargoAssignmentEntity>): String {
        val canonical = buildString {
            append(POLICY_VERSION)
            assignments.sortedBy(CargoAssignmentEntity::cargoId).forEach { assignment ->
                append('\n')
                append(assignment.cargoId)
                append(':')
                append(assignment.priorityCode)
                append(':')
                append(assignment.assignedNodeId)
                append(':')
                append(assignment.state)
                append(':')
                append(decodeClock(assignment.vectorClockBytes).convergenceHash())
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun VectorClock.toProtoBytes(): ByteArray =
        com.example.digitaldelta.proto.v1.VectorClock.newBuilder()
            .addAllEntries(
                counters.toSortedMap().map { (replicaId, counter) ->
                    VectorClockEntry.newBuilder()
                        .setReplicaId(replicaId)
                        .setCounter(counter)
                        .build()
                },
            )
            .build()
            .toByteArray()

    private fun decodeClock(bytes: ByteArray): VectorClock =
        com.example.digitaldelta.proto.v1.VectorClock.parseFrom(bytes).entriesList
            .associate { it.replicaId to it.counter }
            .let(::VectorClock)

    companion object {
        const val POLICY_VERSION = "cargo-assignment-v1"
        const val STATE_DEPOSITED_FOR_REASSIGNMENT = "DEPOSITED_FOR_REASSIGNMENT"
    }
}
