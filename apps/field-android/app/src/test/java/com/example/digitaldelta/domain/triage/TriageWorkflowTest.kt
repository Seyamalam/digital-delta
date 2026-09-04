package com.example.digitaldelta.domain.triage

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriageWorkflowTest {
    private val workflow = DefaultTriageWorkflow()

    @Test
    fun `initial truck route remains within P0 SLA at thirty percent slowdown`() {
        val result = workflow.evaluate(routeEtaMinutes = 65) as TriageWorkflowSnapshot.Protected

        assertEquals(100, result.decision.baselineArrivalMinutes)
        assertEquals(120, result.decision.slowedArrivalMinutes)
        assertFalse(result.decision.willBreachSla)
    }

    @Test
    fun `boat fallback predicts breach and proposes safe P2 deposit`() {
        val result = workflow.evaluate(routeEtaMinutes = 200) as TriageWorkflowSnapshot.Proposed

        assertEquals(260, result.decision.slowedEtaMinutes)
        assertEquals(295, result.decision.slowedArrivalMinutes)
        assertTrue(result.decision.willBreachSla)
        assertEquals("cargo-tarpaulin-p2", result.proposal.lowerPriorityCargoId)
        assertEquals("N3", result.proposal.waypointId)
        assertEquals(25, result.proposal.estimatedMinutesGained)
        assertEquals(listOf("cargo-blood-p0"), result.proposal.queuedUrgentCargoIds)
        assertTrue(result.proposal.requiresHumanConfirmation)
    }

    @Test
    fun `stale route input produces refresh state instead of a preemption proposal`() {
        val result = workflow.evaluate(
            routeEtaMinutes = 200,
            observedAtUnixMs = 10_000,
            nowUnixMs = 10_000 + TriageEngine.MAX_ROUTE_ETA_AGE_MS + 1,
        )

        assertTrue(result is TriageWorkflowSnapshot.RouteRefreshRequired)
        assertEquals(TriageAction.REFRESH_ROUTE_ESTIMATE, result.decision.action)
    }

    @Test
    fun `proposal that becomes stale cannot be confirmed or persisted`() = runTest {
        var now = 20_000L
        var persisted = false
        val staleAwareWorkflow = DefaultTriageWorkflow(
            persistence = PreemptionPersistence { proposal, _ ->
                persisted = true
                TriageWorkflowSnapshot.Confirmed(
                    decision = proposal.decision,
                    proposal = proposal.proposal,
                    eventId = "must-not-write",
                    confirmedAtUnixMs = now,
                )
            },
            nowUnixMs = { now },
        )
        val proposal = staleAwareWorkflow.evaluate(200) as TriageWorkflowSnapshot.Proposed
        now += TriageEngine.MAX_ROUTE_ETA_AGE_MS + 1

        val error = runCatching {
            staleAwareWorkflow.confirm(proposal, "coordinator-1")
        }.exceptionOrNull()

        assertTrue(error is StaleRouteEstimateException)
        assertFalse(persisted)
    }
}
