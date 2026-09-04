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

    @Test
    fun `wire roles map to least privilege field roles`() {
        assertTrue(policy.isAllowed(com.example.digitaldelta.proto.v1.IdentityRole.IDENTITY_ROLE_CLINIC.toAuthorizationRole(), Permission.CREATE_REQUEST))
        assertFalse(policy.isAllowed(com.example.digitaldelta.proto.v1.IdentityRole.IDENTITY_ROLE_CLINIC.toAuthorizationRole(), Permission.RESOLVE_CONFLICT))
        assertTrue(policy.isAllowed(com.example.digitaldelta.proto.v1.IdentityRole.IDENTITY_ROLE_DRIVER.toAuthorizationRole(), Permission.OFFER_CUSTODY))
        assertTrue(policy.isAllowed(com.example.digitaldelta.proto.v1.IdentityRole.IDENTITY_ROLE_COORDINATOR.toAuthorizationRole(), Permission.RESOLVE_CONFLICT))
    }
}
