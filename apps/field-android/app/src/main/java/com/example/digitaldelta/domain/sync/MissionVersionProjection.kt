package com.example.digitaldelta.domain.sync

import com.example.digitaldelta.proto.v1.DomainEvent

/** Materialize an immutable event set, shared by custody verification and its display. */
fun projectMissionVersion(events: List<DomainEvent>): Map<MissionField, String> {
    val creation = events.single { it.hasReliefRequestCreated() }
    val request = creation.reliefRequestCreated
    val initial = mapOf(MissionField.DESTINATION to request.destinationNodeId,
        MissionField.PRIORITY to request.cargoList.minOf { it.priorityValue }.toString(),
        MissionField.MEDICAL_QUANTITY to request.cargoList.filter { it.itemCode in setOf("medicine", "ors", "blood") }.sumOf { it.quantity.toLong() }.toString())
    val revisions = initial.map { (field, value) -> FieldRevision(creation.eventId, request.requestId, field, value,
        VectorClock(mapOf(request.requesterNodeId to 1)), creation.occurredAtUnixMs) }.toMutableList()
    for (event in events) {
        if (event.hasMissionFieldUpdated()) {
            val update = event.missionFieldUpdated
            revisions += FieldRevision(event.eventId, request.requestId, MissionField.valueOf(update.fieldCode), update.value.toStringUtf8(), update.vectorClock.toDomainClock(), event.occurredAtUnixMs)
        } else if (event.hasConflictResolved()) {
            val resolution = event.conflictResolved
            revisions += FieldRevision(event.eventId, request.requestId, MissionField.valueOf(resolution.fieldCode), resolution.selectedValue.toStringUtf8(), resolution.vectorClock.toDomainClock(), event.occurredAtUnixMs)
        }
    }
    return revisions.groupBy { it.field }.mapValues { (_, values) ->
        val projected = projectRevisions(values)
        require(projected.conflicts.none { it.active }) { "Mission version has an unresolved conflict" }
        projected.revision.value
    }
}
