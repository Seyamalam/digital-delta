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
}

data class TriageDecision(
    val priority: CargoPriority,
    val baselineArrivalMinutes: Int,
    val slowedEtaMinutes: Int,
    val slowedArrivalMinutes: Int,
    val willBreachSla: Boolean,
    val action: TriageAction,
    val policyVersion: String = "triage-v1",
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
    val requiresHumanConfirmation: Boolean = true,
)

class NoSafeWaypointException : IllegalStateException("No safe drop waypoint is available")
class InvalidPreemptionTransitionException : IllegalArgumentException(
    "Only P0/P1 cargo may preempt P2/P3 cargo",
)

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
            baselineArrivalMinutes = elapsedMinutes + etaMinutes,
            slowedEtaMinutes = slowedEta,
            slowedArrivalMinutes = slowedArrival,
            willBreachSla = breach,
            action = action,
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
}
