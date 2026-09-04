package com.example.digitaldelta.domain.identity

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.digitaldelta.data.settings.UserSettingsSerializer
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceProfileRepositoryTest {
    @Test
    fun selectedProfileIsWrittenToLocalProtobufDataStore() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.filesDir, "profile-${UUID.randomUUID()}.pb")
        val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = ProtoDeviceProfileRepository(
                DataStoreFactory.create(
                    serializer = UserSettingsSerializer,
                    scope = storeScope,
                    produceFile = { file },
                ),
            )

            assertEquals(DeviceProfiles.CLINIC, repository.profile.first().code)
            repository.select(DeviceProfiles.RELAY)
            assertEquals("RLY-01", repository.profile.first().nodeId)
        } finally {
            storeScope.cancel()
            file.delete()
        }
    }
}
