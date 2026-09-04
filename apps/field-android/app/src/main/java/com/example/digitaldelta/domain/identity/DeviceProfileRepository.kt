package com.example.digitaldelta.domain.identity

import androidx.datastore.core.DataStore
import com.example.digitaldelta.proto.v1.IdentityRole
import com.example.digitaldelta.settings.v1.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class LocalDeviceProfile(
    val code: String,
    val nodeId: String,
    val identityId: String,
    val displayName: String,
    val role: IdentityRole,
)

object DeviceProfiles {
    const val CLINIC = "CLINIC_N4"
    const val HOSPITAL = "HOSPITAL_N6"
    const val RELAY = "RELAY_R1"

    val all: List<LocalDeviceProfile> = listOf(
        LocalDeviceProfile(CLINIC, "N4", "clinic-sylhet-01", "Companyganj Outpost", IdentityRole.IDENTITY_ROLE_CLINIC),
        LocalDeviceProfile(HOSPITAL, "N6", "hospital-habiganj-01", "Habiganj Medical", IdentityRole.IDENTITY_ROLE_HOSPITAL),
        LocalDeviceProfile(RELAY, "RLY-01", "driver-relay-01", "Sunamganj Relay", IdentityRole.IDENTITY_ROLE_DRIVER),
    )

    fun require(code: String): LocalDeviceProfile = all.firstOrNull { it.code == code }
        ?: throw IllegalArgumentException("unsupported device profile")

    fun resolve(code: String): LocalDeviceProfile = all.firstOrNull { it.code == code } ?: all.first()
}

interface DeviceProfileRepository {
    val profile: Flow<LocalDeviceProfile>
    suspend fun select(code: String): LocalDeviceProfile
}

class ProtoDeviceProfileRepository(
    private val dataStore: DataStore<UserSettings>,
) : DeviceProfileRepository {
    override val profile: Flow<LocalDeviceProfile> = dataStore.data.map { settings ->
        DeviceProfiles.resolve(settings.localDeviceProfileCode)
    }

    override suspend fun select(code: String): LocalDeviceProfile {
        val selected = DeviceProfiles.require(code)
        dataStore.updateData { settings ->
            settings.toBuilder().setLocalDeviceProfileCode(selected.code).build()
        }
        return selected
    }
}
