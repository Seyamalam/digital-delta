package com.example.digitaldelta.domain.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshPolicyTest {
    private val policy = MeshPolicy()

    @Test
    fun `low battery reduces normal broadcast frequency by sixty percent`() {
        assertEquals(10_000L, policy.broadcastIntervalMillis(batteryPercent = 80, urgent = false))
        assertEquals(25_000L, policy.broadcastIntervalMillis(batteryPercent = 29, urgent = false))
        assertEquals(5_000L, policy.broadcastIntervalMillis(batteryPercent = 15, urgent = true))
    }

    @Test
    fun `expired over limit and duplicate envelopes are rejected before relay`() {
        val seen = SeenMessageIndex()
        val valid = RelayEnvelope("msg-1", createdAtMillis = 1_000, expiresAtMillis = 9_000, hopCount = 1, hopLimit = 4)

        assertEquals(RelayDecision.FORWARD, policy.evaluate(valid, nowMillis = 2_000, seen))
        seen.record(valid.messageId)
        assertEquals(RelayDecision.REJECT_DUPLICATE, policy.evaluate(valid, 2_100, seen))
        assertEquals(RelayDecision.REJECT_EXPIRED, policy.evaluate(valid.copy(messageId = "msg-2"), 9_001, seen))
        assertEquals(
            RelayDecision.REJECT_HOP_LIMIT,
            policy.evaluate(valid.copy(messageId = "msg-3", hopCount = 4), 2_000, seen),
        )
        assertTrue(seen.contains("msg-1"))
        assertFalse(seen.contains("missing"))
    }
}
