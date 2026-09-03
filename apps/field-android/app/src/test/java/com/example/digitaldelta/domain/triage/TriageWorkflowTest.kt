package com.example.digitaldelta.domain.triage

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
        assertTrue(result.proposal.requiresHumanConfirmation)
    }
}
