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
        assertEquals(12_500L, policy.broadcastIntervalMillis(batteryPercent = 15, urgent = true))
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

    @Test
    fun `role selection uses battery queue peer proximity and link telemetry`() {
        val urgent = policy.selectRelayRole(
            RelayRoleInput(22, 3, 1, 500, 180, urgentPending = true),
        )
        assertEquals(RelayRole.RELAY_URGENT, urgent.role)
        assertEquals(RelayLinkQuality.GOOD, urgent.linkQuality)
        assertTrue(urgent.proximityRecent)

        val conserving = policy.selectRelayRole(
            RelayRoleInput(9, 2, 1, 180_000, 4_200, urgentPending = false),
        )
        assertEquals(RelayRole.RELAY_CONSERVE, conserving.role)
        assertEquals(RelayLinkQuality.DEGRADED, conserving.linkQuality)
        assertFalse(conserving.proximityRecent)

        val clientOnly = policy.selectRelayRole(
            RelayRoleInput(84, 0, 0, null, null, urgentPending = false),
        )
        assertEquals(RelayRole.CLIENT_ONLY, clientOnly.role)
        assertEquals(RelayLinkQuality.UNKNOWN, clientOnly.linkQuality)
    }
}
