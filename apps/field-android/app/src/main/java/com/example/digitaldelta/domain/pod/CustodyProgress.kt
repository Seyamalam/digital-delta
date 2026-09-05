package com.example.digitaldelta.domain.pod

import com.example.digitaldelta.data.local.OperationEntity
import com.example.digitaldelta.domain.sync.MissingEventDependency
import com.example.digitaldelta.proto.v1.DomainEvent
import com.example.digitaldelta.proto.v1.MissionCustodySnapshot

val custodyGenesis: ByteArray get() = sha256("digital-delta-custody-genesis-v1".encodeToByteArray())

/** Hash links, not disconnected phone clocks, determine custody order. */
fun orderedCustodyEvents(history: List<OperationEntity>): List<DomainEvent> {
    val pending = history.filter { it.eventType == "CUSTODY_TRANSFER" }.map { DomainEvent.parseFrom(it.payloadBytes) }.toMutableList()
    val ordered = mutableListOf<DomainEvent>()
    var head = custodyGenesis
    while (pending.isNotEmpty()) {
        val next = pending.filter { it.custodyTransfer.previousReceiptSha256.toByteArray().contentEquals(head) }
        if (next.isEmpty()) throw MissingEventDependency("Custody predecessor unavailable")
        require(next.size == 1) { "Competing custody branches require review" }
        ordered += next.single()
        pending.remove(next.single())
        head = sha256(next.single().custodyTransfer.toByteArray())
    }
    return ordered
}

fun custodyNeedsReconciliation(history: List<OperationEntity>): Boolean = unreconciledCustodyChanges(history).isNotEmpty()

fun unreconciledCustodyChanges(history: List<OperationEntity>): List<DomainEvent> {
    val first = orderedCustodyEvents(history).firstOrNull() ?: return emptyList()
    val snapshot = MissionCustodySnapshot.parseFrom(first.custodyTransfer.missionSnapshot)
    val acknowledged = history.filter { it.eventType == "CUSTODY_RECONCILED" }.map { DomainEvent.parseFrom(it.payloadBytes).custodyReconciled }
        .filter { it.receiptEventId == first.eventId && it.outcomeCode == "RETAIN_SIGNED_CUSTODY" }.flatMap { it.reviewedEventIdsList }.toSet()
    return history.filter { it.eventType in setOf("RELIEF_REQUEST_CREATED", "MISSION_FIELD_UPDATED", "CONFLICT_RESOLVED") && it.eventId !in snapshot.eventIdsList && it.eventId !in acknowledged }
        .map { DomainEvent.parseFrom(it.payloadBytes) }
}
