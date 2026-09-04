package com.example.digitaldelta.domain.sync

enum class MissionField(val requiresHumanReviewWhenConcurrent: Boolean) {
    DESCRIPTION(false),
    DESTINATION(true),
    PRIORITY(true),
    MEDICAL_QUANTITY(true),
}

data class FieldRevision(
    val eventId: String,
    val missionId: String,
    val field: MissionField,
    val value: String,
    val clock: VectorClock,
    val occurredAtUnixMs: Long,
) {
    init {
        require(eventId.isNotBlank())
        require(missionId.isNotBlank())
        require(value.isNotBlank())
    }
}

sealed interface MergeDecision {
    data class Applied(
        val revision: FieldRevision,
        val concurrent: Boolean,
    ) : MergeDecision

    data class NeedsReview(
        val left: FieldRevision,
        val right: FieldRevision,
        val mergedClock: VectorClock,
    ) : MergeDecision
}

class MissionMergeEngine {
    fun merge(existing: FieldRevision?, incoming: FieldRevision): MergeDecision {
        if (existing == null) return MergeDecision.Applied(incoming, concurrent = false)
        require(existing.missionId == incoming.missionId) { "cannot merge revisions from different missions" }
        require(existing.field == incoming.field) { "cannot merge revisions from different fields" }

        return when (incoming.clock.compare(existing.clock)) {
            ClockRelation.AFTER -> MergeDecision.Applied(incoming, concurrent = false)
            ClockRelation.BEFORE -> MergeDecision.Applied(existing, concurrent = false)
            ClockRelation.EQUAL -> {
                if (
                    incoming.field.requiresHumanReviewWhenConcurrent &&
                    existing.value != incoming.value
                ) {
                    val ordered = listOf(existing, incoming).sortedBy(FieldRevision::eventId)
                    MergeDecision.NeedsReview(ordered[0], ordered[1], existing.clock)
                } else {
                    MergeDecision.Applied(
                        revision = deterministicWinner(existing, incoming),
                        concurrent = false,
                    )
                }
            }
            ClockRelation.CONCURRENT -> {
                val ordered = listOf(existing, incoming).sortedBy(FieldRevision::eventId)
                if (incoming.field.requiresHumanReviewWhenConcurrent) {
                    MergeDecision.NeedsReview(
                        left = ordered[0],
                        right = ordered[1],
                        mergedClock = existing.clock.merge(incoming.clock),
                    )
                } else {
                    val winner = deterministicWinner(existing, incoming)
                    MergeDecision.Applied(
                        revision = winner.copy(clock = existing.clock.merge(incoming.clock)),
                        concurrent = true,
                    )
                }
            }
        }
    }

    private fun deterministicWinner(left: FieldRevision, right: FieldRevision): FieldRevision =
        maxOf(left, right, compareBy(FieldRevision::occurredAtUnixMs, FieldRevision::eventId))
}

data class GrowOnlySet<T>(val values: Set<T> = emptySet()) {
    fun add(value: T): GrowOnlySet<T> = copy(values = values + value)
    fun merge(other: GrowOnlySet<T>): GrowOnlySet<T> = GrowOnlySet(values + other.values)
}

/**
 * An observed-remove set for assignments. Each add carries a globally unique
 * operation tag. Removing a value tombstones only the tags the replica has
 * observed, so a truly concurrent reassignment survives while late or duplicate
 * delivery cannot resurrect an assignment that was already removed.
 */
data class ObservedRemoveSet<T>(
    val additions: Map<T, Set<String>> = emptyMap(),
    val tombstones: Set<String> = emptySet(),
) {
    val values: Set<T>
        get() = additions
            .filterValues { tags -> tags.any { it !in tombstones } }
            .keys

    fun contains(value: T): Boolean = additions[value].orEmpty().any { it !in tombstones }

    fun add(value: T, operationTag: String): ObservedRemoveSet<T> {
        require(operationTag.isNotBlank()) { "operation tag is required" }
        val tags = additions[value].orEmpty() + operationTag
        return copy(additions = additions + (value to tags))
    }

    fun remove(value: T): ObservedRemoveSet<T> =
        copy(tombstones = tombstones + additions[value].orEmpty())

    fun merge(other: ObservedRemoveSet<T>): ObservedRemoveSet<T> {
        val keys = additions.keys + other.additions.keys
        return ObservedRemoveSet(
            additions = keys.associateWith { value ->
                additions[value].orEmpty() + other.additions[value].orEmpty()
            },
            tombstones = tombstones + other.tombstones,
        )
    }
}

data class PnCounter(
    val increments: Map<String, Long> = emptyMap(),
    val decrements: Map<String, Long> = emptyMap(),
) {
    val value: Long get() = increments.values.sum() - decrements.values.sum()

    fun increment(replicaId: String, amount: Long = 1): PnCounter {
        require(replicaId.isNotBlank())
        require(amount >= 0)
        return copy(increments = increments + (replicaId to ((increments[replicaId] ?: 0L) + amount)))
    }

    fun decrement(replicaId: String, amount: Long = 1): PnCounter {
        require(replicaId.isNotBlank())
        require(amount >= 0)
        return copy(decrements = decrements + (replicaId to ((decrements[replicaId] ?: 0L) + amount)))
    }

    fun merge(other: PnCounter): PnCounter = PnCounter(
        increments = mergeComponents(increments, other.increments),
        decrements = mergeComponents(decrements, other.decrements),
    )

    private fun mergeComponents(left: Map<String, Long>, right: Map<String, Long>): Map<String, Long> =
        (left.keys + right.keys).associateWith { replica ->
            maxOf(left[replica] ?: 0L, right[replica] ?: 0L)
        }
}
