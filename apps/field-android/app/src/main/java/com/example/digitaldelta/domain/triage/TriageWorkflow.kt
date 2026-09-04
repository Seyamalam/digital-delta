package com.example.digitaldelta.domain.triage

import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.data.local.OperationEntity
import com.example.digitaldelta.proto.v1.DomainEvent
import com.example.digitaldelta.proto.v1.PreemptionConfirmed
import java.util.UUID

sealed interface TriageWorkflowSnapshot {
    val decision: TriageDecision

    data class Protected(override val decision: TriageDecision) : TriageWorkflowSnapshot
    data class Warning(override val decision: TriageDecision) : TriageWorkflowSnapshot
    data class RouteRefreshRequired(
        override val decision: TriageDecision,
    ) : TriageWorkflowSnapshot
    data class Proposed(
        override val decision: TriageDecision,
        val proposal: PreemptionProposal,
    ) : TriageWorkflowSnapshot

    data class Confirming(
        override val decision: TriageDecision,
        val proposal: PreemptionProposal,
    ) : TriageWorkflowSnapshot

    data class Confirmed(
        override val decision: TriageDecision,
        val proposal: PreemptionProposal,
        val eventId: String,
        val confirmedAtUnixMs: Long,
    ) : TriageWorkflowSnapshot
}

interface TriageWorkflow {
    fun evaluate(routeEtaMinutes: Int): TriageWorkflowSnapshot
    fun evaluate(
        routeEtaMinutes: Int,
        observedAtUnixMs: Long,
        nowUnixMs: Long,
    ): TriageWorkflowSnapshot = evaluate(routeEtaMinutes)
    suspend fun confirm(
        proposal: TriageWorkflowSnapshot.Proposed,
        confirmerIdentityId: String,
    ): TriageWorkflowSnapshot.Confirmed
}

fun interface PreemptionPersistence {
    suspend fun record(
        proposal: TriageWorkflowSnapshot.Proposed,
        confirmerIdentityId: String,
    ): TriageWorkflowSnapshot.Confirmed
}

class DefaultTriageWorkflow(
    private val engine: TriageEngine = TriageEngine(),
    private val persistence: PreemptionPersistence? = null,
    private val nowUnixMs: () -> Long = System::currentTimeMillis,
) : TriageWorkflow {
    override fun evaluate(routeEtaMinutes: Int): TriageWorkflowSnapshot {
        val now = nowUnixMs()
        return evaluate(routeEtaMinutes, observedAtUnixMs = now, nowUnixMs = now)
    }

    override fun evaluate(
        routeEtaMinutes: Int,
        observedAtUnixMs: Long,
        nowUnixMs: Long,
    ): TriageWorkflowSnapshot {
        val decision = engine.evaluate(
            priority = CargoPriority.P0,
            elapsedMinutes = ELAPSED_MINUTES,
            estimate = RouteEtaEstimate(routeEtaMinutes, observedAtUnixMs),
            nowUnixMs = nowUnixMs,
        )
        return snapshotFor(decision)
    }

    private fun snapshotFor(decision: TriageDecision): TriageWorkflowSnapshot {
        return when (decision.action) {
            TriageAction.CONTINUE -> TriageWorkflowSnapshot.Protected(decision)
            TriageAction.WARN -> TriageWorkflowSnapshot.Warning(decision)
            TriageAction.REFRESH_ROUTE_ESTIMATE ->
                TriageWorkflowSnapshot.RouteRefreshRequired(decision)
            TriageAction.PREEMPT_LOWER_PRIORITY -> TriageWorkflowSnapshot.Proposed(
                decision = decision,
                proposal = engine.proposePreemption(
                    urgentCargoId = urgentArbitration.selected.cargoId,
                    urgentPriority = urgentArbitration.selected.priority,
                    lowerPriorityCargoId = LOWER_PRIORITY_CARGO_ID,
                    lowerPriority = CargoPriority.P2,
                    candidates = listOf(
                        DropWaypoint("N5", safe = false, handlingMinutes = 2),
                        DropWaypoint("N3", safe = true, handlingMinutes = 7),
                        DropWaypoint("N4", safe = true, handlingMinutes = 12),
                    ),
                ).copy(
                    estimatedMinutesGained = ESTIMATED_MINUTES_GAINED,
                    queuedUrgentCargoIds = urgentArbitration.queued.map(UrgentCargoCandidate::cargoId),
                ),
            )
        }
    }

    override suspend fun confirm(
        proposal: TriageWorkflowSnapshot.Proposed,
        confirmerIdentityId: String,
    ): TriageWorkflowSnapshot.Confirmed {
        require(confirmerIdentityId.isNotBlank())
        require(proposal.proposal.requiresHumanConfirmation)
        val ageMs = (nowUnixMs() - proposal.decision.routeEstimateObservedAtUnixMs).coerceAtLeast(0)
        if (proposal.decision.routeEstimateObservedAtUnixMs <= 0 || ageMs > TriageEngine.MAX_ROUTE_ETA_AGE_MS) {
            throw StaleRouteEstimateException(
                proposal.decision.copy(
                    willBreachSla = false,
                    action = TriageAction.REFRESH_ROUTE_ESTIMATE,
                    routeEstimateStale = true,
                    routeEstimateAgeMs = ageMs,
                ),
            )
        }
        return requireNotNull(persistence) { "preemption persistence is not configured" }
            .record(proposal, confirmerIdentityId)
    }

    private val urgentArbitration: UrgentCargoArbitration
        get() = engine.arbitrate(
            listOf(
                UrgentCargoCandidate(URGENT_CARGO_ID, CargoPriority.P0, ELAPSED_MINUTES),
                UrgentCargoCandidate(QUEUED_URGENT_CARGO_ID, CargoPriority.P0, elapsedMinutes = 20),
            ),
        )

    companion object {
        private const val ELAPSED_MINUTES = 35
        private const val URGENT_CARGO_ID = "cargo-medicine-p0"
        private const val QUEUED_URGENT_CARGO_ID = "cargo-blood-p0"
        private const val LOWER_PRIORITY_CARGO_ID = "cargo-tarpaulin-p2"
        private const val ESTIMATED_MINUTES_GAINED = 25
    }
}

class RoomTriageWorkflow(
    database: DeltaDatabase,
    nowUnixMs: () -> Long = System::currentTimeMillis,
    eventId: () -> String = { "preemption-${UUID.randomUUID()}" },
) : TriageWorkflow by DefaultTriageWorkflow(
    persistence = RoomPreemptionPersistence(database, nowUnixMs, eventId),
    nowUnixMs = nowUnixMs,
)

private class RoomPreemptionPersistence(
    private val database: DeltaDatabase,
    private val nowUnixMs: () -> Long,
    private val eventId: () -> String,
) : PreemptionPersistence {
    override suspend fun record(
        proposal: TriageWorkflowSnapshot.Proposed,
        confirmerIdentityId: String,
    ): TriageWorkflowSnapshot.Confirmed {
        val id = eventId()
        val now = nowUnixMs()
        val confirmed = PreemptionConfirmed.newBuilder()
            .setMissionId(MISSION_ID)
            .setUrgentCargoId(proposal.proposal.urgentCargoId)
            .setDepositedCargoId(proposal.proposal.lowerPriorityCargoId)
            .setWaypointNodeId(proposal.proposal.waypointId)
            .setConfirmerIdentityId(confirmerIdentityId)
            .setPolicyVersion(proposal.decision.policyVersion)
            .setReasonCode(REASON_CODE)
            .setEstimatedMinutesGained(proposal.proposal.estimatedMinutesGained)
            .build()
        val event = DomainEvent.newBuilder()
            .setEventId(id)
            .setSchemaVersion(1)
            .setActorIdentityId(confirmerIdentityId)
            .setOccurredAtUnixMs(now)
            .setSimulated(true)
            .setScenarioSeed(SCENARIO_SEED)
            .setPreemptionConfirmed(confirmed)
            .build()
        database.operationLogDao().append(
            OperationEntity(
                eventId = id,
                missionId = MISSION_ID,
                eventType = "PREEMPTION_CONFIRMED",
                payloadBytes = event.toByteArray(),
                createdAtUnixMs = now,
            ),
        )
        return TriageWorkflowSnapshot.Confirmed(
            decision = proposal.decision,
            proposal = proposal.proposal,
            eventId = id,
            confirmedAtUnixMs = now,
        )
    }

    companion object {
        private const val MISSION_ID = "mission-sylhet-01"
        private const val SCENARIO_SEED = "m6-preemption-demo-v1"
        private const val REASON_CODE = "SLA_BREACH_30_PERCENT"
    }
}
