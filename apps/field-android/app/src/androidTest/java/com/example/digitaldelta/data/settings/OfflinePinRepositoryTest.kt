package com.example.digitaldelta.data.settings

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflinePinRepositoryTest {
    @Test
    fun saltedPinSurvivesRepositoryRecreationAndLocksAfterFiveFailures() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.filesDir, "pin-${UUID.randomUUID()}.pb")
        val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = DataStoreFactory.create(
                serializer = UserSettingsSerializer,
                scope = storeScope,
                produceFile = { file },
            )
            val repository = ProtoOfflinePinRepository(store)
            repository.configure("284619")

            val stored = store.data.first()
            assertFalse(stored.offlinePinHash.toByteArray().contentEquals("284619".encodeToByteArray()))
            assertTrue(stored.offlinePinSalt.size() >= 16)
            assertTrue(ProtoOfflinePinRepository(store).snapshot(nowUnixMs = 1_000).configured)

            repeat(4) { attempt ->
                assertEquals(PinVerification.Rejected(4 - attempt), repository.verify("000000", 2_000L + attempt))
            }
            assertEquals(PinVerification.LockedOut(32_004), repository.verify("000000", 2_004))
            assertEquals(PinVerification.LockedOut(32_004), repository.verify("284619", 2_005))
            assertEquals(PinVerification.Accepted, repository.verify("284619", 32_005))
        } finally {
            storeScope.cancel()
            file.delete()
        }
    }
}
