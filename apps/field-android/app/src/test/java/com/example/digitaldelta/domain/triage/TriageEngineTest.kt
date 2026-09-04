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

    @Test
    fun `stale route estimate requests refresh and cannot authorize preemption`() {
        val result = engine.evaluate(
            priority = CargoPriority.P0,
            elapsedMinutes = 35,
            estimate = RouteEtaEstimate(
                etaMinutes = 200,
                observedAtUnixMs = 1_000,
            ),
            nowUnixMs = 1_000 + TriageEngine.MAX_ROUTE_ETA_AGE_MS + 1,
        )

        assertTrue(result.routeEstimateStale)
        assertEquals(TriageAction.REFRESH_ROUTE_ESTIMATE, result.action)
        assertFalse(result.willBreachSla)
    }

    @Test
    fun `simultaneous P0 requests use remaining SLA then stable id and preserve the queue`() {
        val result = engine.arbitrate(
            candidates = listOf(
                UrgentCargoCandidate("cargo-p0-b", CargoPriority.P0, elapsedMinutes = 80),
                UrgentCargoCandidate("cargo-p0-c", CargoPriority.P0, elapsedMinutes = 80),
                UrgentCargoCandidate("cargo-p0-a", CargoPriority.P0, elapsedMinutes = 20),
            ),
        )

        assertEquals("cargo-p0-b", result.selected.cargoId)
        assertEquals(listOf("cargo-p0-c", "cargo-p0-a"), result.queued.map { it.cargoId })
        assertTrue(result.queued.all { it.priority == CargoPriority.P0 })
    }

    @Test
    fun `P0 remains ahead of P1 without changing either priority`() {
        val result = engine.arbitrate(
            candidates = listOf(
                UrgentCargoCandidate("new-p0", CargoPriority.P0, elapsedMinutes = 10),
                UrgentCargoCandidate("old-p1", CargoPriority.P1, elapsedMinutes = 355),
            ),
        )

        assertEquals("new-p0", result.selected.cargoId)
        assertEquals(CargoPriority.P0, result.selected.priority)
        assertEquals(CargoPriority.P1, result.queued.single().priority)
    }
}
