package com.example.digitaldelta.domain.identity

import com.example.digitaldelta.proto.v1.IdentityRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceProfilesTest {
    @Test
    fun `fair profiles have unique node and identity ids with explicit roles`() {
        assertEquals(4, DeviceProfiles.all.map { it.nodeId }.toSet().size)
        assertEquals(4, DeviceProfiles.all.map { it.identityId }.toSet().size)
        assertEquals(IdentityRole.IDENTITY_ROLE_COORDINATOR, DeviceProfiles.require(DeviceProfiles.COORDINATOR).role)
        assertEquals("N4", DeviceProfiles.require(DeviceProfiles.CLINIC).nodeId)
        assertEquals(IdentityRole.IDENTITY_ROLE_HOSPITAL, DeviceProfiles.require(DeviceProfiles.HOSPITAL).role)
        assertEquals(IdentityRole.IDENTITY_ROLE_DRIVER, DeviceProfiles.require(DeviceProfiles.RELAY).role)
    }

    @Test
    fun `unknown persisted value migrates safely while explicit selection rejects it`() {
        assertEquals(DeviceProfiles.CLINIC, DeviceProfiles.resolve("old-or-empty").code)
        assertThrows(IllegalArgumentException::class.java) { DeviceProfiles.require("attacker-node") }
    }
}
