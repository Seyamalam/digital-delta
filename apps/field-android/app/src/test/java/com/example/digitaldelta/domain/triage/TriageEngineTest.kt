package com.example.digitaldelta.domain.triage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class TriageEngineTest {
    private val engine = TriageEngine()

    @Test
    fun `P0 route breaches SLA after thirty percent slowdown`() {
        val result = engine.evaluate(
            priority = CargoPriority.P0,
            elapsedMinutes = 35,
            etaMinutes = 75,
        )

        assertEquals(98, result.slowedEtaMinutes)
        assertEquals(85, result.remainingSlaMinutes)
        assertTrue(result.willBreachSla)
        assertEquals(TriageAction.PREEMPT_LOWER_PRIORITY, result.action)
    }

    @Test
    fun `P2 delivery inside SLA does not preempt`() {
        val result = engine.evaluate(
            priority = CargoPriority.P2,
            elapsedMinutes = 120,
            etaMinutes = 240,
        )

        assertFalse(result.willBreachSla)
        assertEquals(TriageAction.CONTINUE, result.action)
    }

    @Test
    fun `lower priority cargo may be deposited only at a safe waypoint`() {
        val proposal = engine.proposePreemption(
            urgentCargoId = "cargo-p0",
            urgentPriority = CargoPriority.P0,
            lowerPriorityCargoId = "cargo-p2",
            lowerPriority = CargoPriority.P2,
            candidates = listOf(
                DropWaypoint("N5", safe = false, handlingMinutes = 2),
                DropWaypoint("N4", safe = true, handlingMinutes = 12),
                DropWaypoint("N3", safe = true, handlingMinutes = 7),
            ),
        )

        assertEquals("N3", proposal.waypointId)
        assertTrue(proposal.requiresHumanConfirmation)
    }

    @Test
    fun `equal priority cargo cannot be preempted`() {
        assertThrows(InvalidPreemptionTransitionException::class.java) {
            engine.proposePreemption(
                urgentCargoId = "cargo-p0-a",
                urgentPriority = CargoPriority.P0,
                lowerPriorityCargoId = "cargo-p0-b",
                lowerPriority = CargoPriority.P0,
                candidates = listOf(DropWaypoint("N3", safe = true, handlingMinutes = 7)),
            )
        }
    }

    @Test
    fun `proposal fails when no safe waypoint exists`() {
        assertThrows(NoSafeWaypointException::class.java) {
            engine.proposePreemption(
                urgentCargoId = "cargo-p0",
                urgentPriority = CargoPriority.P0,
                lowerPriorityCargoId = "cargo-p2",
                lowerPriority = CargoPriority.P2,
                candidates = listOf(DropWaypoint("N5", safe = false, handlingMinutes = 2)),
            )
        }
    }
}
