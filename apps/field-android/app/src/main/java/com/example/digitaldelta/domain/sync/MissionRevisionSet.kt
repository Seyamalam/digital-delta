package com.example.digitaldelta.domain.sync

/** Immutable revisions are retained; a merged display clock is never a source revision. */
data class RevisionPair(val left: FieldRevision, val right: FieldRevision, val active: Boolean)
data class RevisionProjection(val revision: FieldRevision, val conflicts: List<RevisionPair>)

fun projectRevisions(revisions: List<FieldRevision>): RevisionProjection {
    require(revisions.isNotEmpty())
    val all = revisions.distinctBy { it.eventId }.sortedBy { it.eventId }
    require(all.all { it.missionId == all.first().missionId && it.field == all.first().field })
    val frontier = all.filter { revision -> all.none { it.clock.compare(revision.clock) == ClockRelation.AFTER } }
    val ids = frontier.map { it.eventId }.toSet()
    val pairs = if (!all.first().field.requiresHumanReviewWhenConcurrent) emptyList() else all.flatMapIndexed { index, left ->
        all.drop(index + 1).filter { right -> left.value != right.value && left.clock.compare(right.clock) in setOf(ClockRelation.CONCURRENT, ClockRelation.EQUAL) }
            .map { right -> RevisionPair(left, right, left.eventId in ids && right.eventId in ids) }
    }
    val winner = if (pairs.any { it.active }) frontier.minBy { it.eventId }
        else frontier.maxWith(compareBy(FieldRevision::occurredAtUnixMs, FieldRevision::eventId))
    return RevisionProjection(winner.copy(clock = all.fold(VectorClock(emptyMap())) { clock, revision -> clock.merge(revision.clock) }), pairs)
}

fun VectorClock.toProto(): com.example.digitaldelta.proto.v1.VectorClock =
    com.example.digitaldelta.proto.v1.VectorClock.newBuilder().addAllEntries(counters.toSortedMap().map { (id, counter) ->
        com.example.digitaldelta.proto.v1.VectorClockEntry.newBuilder().setReplicaId(id).setCounter(counter).build()
    }).build()

fun com.example.digitaldelta.proto.v1.VectorClock.toDomainClock() = VectorClock(entriesList.associate { it.replicaId to it.counter })
