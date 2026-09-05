package com.example.digitaldelta.domain.triage

import kotlin.math.roundToInt

enum class CargoPriority(val slaMinutes: Int) {
    P0(120),
    P1(360),
    P2(1_440),
    P3(4_320),
}

enum class TriageAction {
    CONTINUE,
    WARN,
    PREEMPT_LOWER_PRIORITY,
    REFRESH_ROUTE_ESTIMATE,
}

data class RouteEtaEstimate(
    val etaMinutes: Int,
    val observedAtUnixMs: Long,
) {
    init {
        require(etaMinutes >= 0)
        require(observedAtUnixMs >= 0)
    }
}

data class TriageDecision(
    val priority: CargoPriority,
    val remainingSlaMinutes: Int,
    val baselineArrivalMinutes: Int,
    val slowedEtaMinutes: Int,
    val slowedArrivalMinutes: Int,
    val willBreachSla: Boolean,
    val action: TriageAction,
    val routeEstimateStale: Boolean = false,
    val routeEstimateAgeMs: Long = 0,
    val routeEstimateObservedAtUnixMs: Long = 0,
    val policyVersion: String = "triage-v1",
)

data class UrgentCargoCandidate(
    val cargoId: String,
    val priority: CargoPriority,
    val elapsedMinutes: Int,
) {
    init {
        require(cargoId.isNotBlank())
        require(priority == CargoPriority.P0 || priority == CargoPriority.P1) {
            "Only P0/P1 cargo participates in urgent arbitration"
        }
        require(elapsedMinutes >= 0)
    }

    val remainingSlaMinutes: Int
        get() = (priority.slaMinutes - elapsedMinutes).coerceAtLeast(0)
}

data class UrgentCargoArbitration(
    val selected: UrgentCargoCandidate,
    val queued: List<UrgentCargoCandidate>,
    val policyVersion: String = "triage-v2",
)

data class DropWaypoint(
    val nodeId: String,
    val safe: Boolean,
    val handlingMinutes: Int,
)

data class PreemptionProposal(
    val urgentCargoId: String,
    val urgentPriority: CargoPriority,
    val lowerPriorityCargoId: String,
    val lowerPriority: CargoPriority,
    val waypointId: String,
    val estimatedMinutesGained: Int = 0,
    val queuedUrgentCargoIds: List<String> = emptyList(),
    val requiresHumanConfirmation: Boolean = true,
)

class NoSafeWaypointException : IllegalStateException("No safe drop waypoint is available")
class InvalidPreemptionTransitionException : IllegalArgumentException(
    "Only P0/P1 cargo may preempt P2/P3 cargo",
)
class StaleRouteEstimateException(
    val staleDecision: TriageDecision,
) : IllegalStateException("Route ETA is stale; recompute before confirming preemption")

class TriageEngine {
    fun evaluate(
        priority: CargoPriority,
        elapsedMinutes: Int,
        etaMinutes: Int,
        slowdownFactor: Double = 1.30,
    ): TriageDecision {
        require(elapsedMinutes >= 0)
        require(etaMinutes >= 0)
        require(slowdownFactor >= 1.0)

        val slowedEta = (etaMinutes * slowdownFactor).roundToInt()
        val slowedArrival = elapsedMinutes + slowedEta
        val breach = slowedArrival > priority.slaMinutes
        val action = when {
            !breach -> TriageAction.CONTINUE
            priority == CargoPriority.P0 || priority == CargoPriority.P1 ->
                TriageAction.PREEMPT_LOWER_PRIORITY
            else -> TriageAction.WARN
        }

        return TriageDecision(
            priority = priority,
            remainingSlaMinutes = (priority.slaMinutes - elapsedMinutes).coerceAtLeast(0),
            baselineArrivalMinutes = elapsedMinutes + etaMinutes,
            slowedEtaMinutes = slowedEta,
            slowedArrivalMinutes = slowedArrival,
            willBreachSla = breach,
            action = action,
        )
    }

    fun evaluate(
        priority: CargoPriority,
        elapsedMinutes: Int,
        estimate: RouteEtaEstimate,
        nowUnixMs: Long,
        slowdownFactor: Double = 1.30,
        maxEstimateAgeMs: Long = MAX_ROUTE_ETA_AGE_MS,
    ): TriageDecision {
        require(nowUnixMs >= 0)
        require(maxEstimateAgeMs >= 0)
        require(slowdownFactor >= 1.0)
        val ageMs = (nowUnixMs - estimate.observedAtUnixMs).coerceAtLeast(0)
        if (estimate.observedAtUnixMs > nowUnixMs || ageMs >= maxEstimateAgeMs) {
            require(elapsedMinutes >= 0)
            val slowedEta = (estimate.etaMinutes * slowdownFactor).roundToInt()
            return TriageDecision(
                priority = priority,
                remainingSlaMinutes = (priority.slaMinutes - elapsedMinutes).coerceAtLeast(0),
                baselineArrivalMinutes = elapsedMinutes + estimate.etaMinutes,
                slowedEtaMinutes = slowedEta,
                slowedArrivalMinutes = elapsedMinutes + slowedEta,
                willBreachSla = false,
                action = TriageAction.REFRESH_ROUTE_ESTIMATE,
                routeEstimateStale = true,
                routeEstimateAgeMs = ageMs,
                routeEstimateObservedAtUnixMs = estimate.observedAtUnixMs,
                policyVersion = "triage-v2",
            )
        }
        return evaluate(
            priority = priority,
            elapsedMinutes = elapsedMinutes,
            etaMinutes = estimate.etaMinutes,
            slowdownFactor = slowdownFactor,
        ).copy(
            routeEstimateAgeMs = ageMs,
            routeEstimateObservedAtUnixMs = estimate.observedAtUnixMs,
            policyVersion = "triage-v2",
        )
    }

    fun arbitrate(candidates: List<UrgentCargoCandidate>): UrgentCargoArbitration {
        require(candidates.isNotEmpty()) { "At least one urgent cargo candidate is required" }
        require(candidates.map(UrgentCargoCandidate::cargoId).distinct().size == candidates.size) {
            "Urgent cargo IDs must be unique"
        }
        val ordered = candidates.sortedWith(
            compareBy<UrgentCargoCandidate>(
                { it.priority.ordinal },
                { it.remainingSlaMinutes },
                { it.cargoId },
            ),
        )
        return UrgentCargoArbitration(
            selected = ordered.first(),
            queued = ordered.drop(1),
        )
    }

    fun proposePreemption(
        urgentCargoId: String,
        urgentPriority: CargoPriority,
        lowerPriorityCargoId: String,
        lowerPriority: CargoPriority,
        candidates: List<DropWaypoint>,
    ): PreemptionProposal {
        if (urgentPriority !in setOf(CargoPriority.P0, CargoPriority.P1) ||
            lowerPriority !in setOf(CargoPriority.P2, CargoPriority.P3)
        ) {
            throw InvalidPreemptionTransitionException()
        }
        val waypoint = candidates
            .asSequence()
            .filter(DropWaypoint::safe)
            .minWithOrNull(compareBy(DropWaypoint::handlingMinutes, DropWaypoint::nodeId))
            ?: throw NoSafeWaypointException()

        return PreemptionProposal(
            urgentCargoId = urgentCargoId,
            urgentPriority = urgentPriority,
            lowerPriorityCargoId = lowerPriorityCargoId,
            lowerPriority = lowerPriority,
            waypointId = waypoint.nodeId,
        )
    }

    companion object {
        const val MAX_ROUTE_ETA_AGE_MS: Long = 5 * 60 * 1_000
    }
}
