package com.example.digitaldelta.ui.main

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.digitaldelta.data.settings.ProtoOfflinePinRepository
import com.example.digitaldelta.data.settings.userSettingsDataStore
import com.example.digitaldelta.settings.v1.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/** Test APK only: isolate the journey PIN, then restore the pre-test PIN fields. */
class ProductionPinRule : ExternalResource() {
    private val store get() = ApplicationProvider.getApplicationContext<Context>().userSettingsDataStore
    private var saved: UserSettings? = null
    override fun before() = runBlocking {
        saved = store.data.first()
        ProtoOfflinePinRepository(store).configure("284619")
    }
    override fun after() {
        val previous = saved ?: return
        runBlocking { store.updateData { it.toBuilder().setOfflinePinSalt(previous.offlinePinSalt)
            .setOfflinePinHash(previous.offlinePinHash).setFailedPinAttempts(previous.failedPinAttempts)
            .setPinLockedUntilUnixMs(previous.pinLockedUntilUnixMs).build() } }
    }
}
