package com.example.digitaldelta.domain.mesh

import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyPermissionPolicyTest {
    @Test
    fun `runtime permissions follow Android radio privacy generations`() {
        assertEquals(
            setOf(NearbyPermissionPolicy.COARSE_LOCATION),
            NearbyPermissionPolicy.requiredRuntimePermissions(sdkInt = 28),
        )
        assertEquals(
            setOf(NearbyPermissionPolicy.FINE_LOCATION),
            NearbyPermissionPolicy.requiredRuntimePermissions(sdkInt = 30),
        )
        assertEquals(
            setOf(
                NearbyPermissionPolicy.COARSE_LOCATION,
                NearbyPermissionPolicy.FINE_LOCATION,
                NearbyPermissionPolicy.BLUETOOTH_ADVERTISE,
                NearbyPermissionPolicy.BLUETOOTH_CONNECT,
                NearbyPermissionPolicy.BLUETOOTH_SCAN,
            ),
            NearbyPermissionPolicy.requiredRuntimePermissions(sdkInt = 31),
        )
        assertEquals(
            setOf(
                NearbyPermissionPolicy.BLUETOOTH_ADVERTISE,
                NearbyPermissionPolicy.BLUETOOTH_CONNECT,
                NearbyPermissionPolicy.BLUETOOTH_SCAN,
                NearbyPermissionPolicy.NEARBY_WIFI_DEVICES,
                NearbyPermissionPolicy.POST_NOTIFICATIONS,
            ),
            NearbyPermissionPolicy.requiredRuntimePermissions(sdkInt = 33),
        )
        assertEquals(
            setOf(
                NearbyPermissionPolicy.BLUETOOTH_ADVERTISE,
                NearbyPermissionPolicy.BLUETOOTH_CONNECT,
                NearbyPermissionPolicy.BLUETOOTH_SCAN,
                NearbyPermissionPolicy.NEARBY_WIFI_DEVICES,
                NearbyPermissionPolicy.POST_NOTIFICATIONS,
                NearbyPermissionPolicy.ACCESS_LOCAL_NETWORK,
            ),
            NearbyPermissionPolicy.requiredRuntimePermissions(sdkInt = 37),
        )
    }
}
