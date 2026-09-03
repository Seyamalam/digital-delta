package com.example.digitaldelta.domain.mesh

object NearbyPermissionPolicy {
    const val COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
    const val FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    const val BLUETOOTH_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE"
    const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
    const val BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
    const val NEARBY_WIFI_DEVICES = "android.permission.NEARBY_WIFI_DEVICES"
    const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

    fun requiredRuntimePermissions(sdkInt: Int): Set<String> = buildSet {
        when {
            sdkInt <= 28 -> add(COARSE_LOCATION)
            sdkInt <= 30 -> add(FINE_LOCATION)
            sdkInt == 31 || sdkInt == 32 -> {
                add(FINE_LOCATION)
                add(BLUETOOTH_ADVERTISE)
                add(BLUETOOTH_CONNECT)
                add(BLUETOOTH_SCAN)
            }
            else -> {
                add(BLUETOOTH_ADVERTISE)
                add(BLUETOOTH_CONNECT)
                add(BLUETOOTH_SCAN)
                add(NEARBY_WIFI_DEVICES)
                add(POST_NOTIFICATIONS)
                if (sdkInt >= 37) add(ACCESS_LOCAL_NETWORK)
            }
        }
    }
}
