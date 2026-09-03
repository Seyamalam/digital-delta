package com.example.digitaldelta.domain.identity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizationPolicyTest {
    private val policy = AuthorizationPolicy()

    @Test
    fun `coordinator can confirm preemption while relay cannot`() {
        assertTrue(policy.isAllowed(Role.COORDINATOR, Permission.CONFIRM_PREEMPTION))
        assertFalse(policy.isAllowed(Role.RELAY, Permission.CONFIRM_PREEMPTION))
    }

    @Test
    fun `expired or revoked offline credential is rejected before role evaluation`() {
        val now = 1_788_374_217_000L

        assertFalse(
            policy.authorize(
                OfflineCredential("volunteer-1", Role.OPERATOR, expiresAtMillis = now - 1),
                Permission.OFFER_CUSTODY,
                nowMillis = now,
            ).allowed,
        )
        assertFalse(
            policy.authorize(
                OfflineCredential("volunteer-1", Role.OPERATOR, expiresAtMillis = now + 60_000, revoked = true),
                Permission.OFFER_CUSTODY,
                nowMillis = now,
            ).allowed,
        )
    }
}
