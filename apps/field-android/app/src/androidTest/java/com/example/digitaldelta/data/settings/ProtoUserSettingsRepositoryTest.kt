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
class ProtoUserSettingsRepositoryTest {
    @Test
    fun languageSelectionIsRequiredUntilAChoiceIsPersisted() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.filesDir, "language-${UUID.randomUUID()}.pb")
        val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = ProtoUserSettingsRepository(
                DataStoreFactory.create(
                    serializer = UserSettingsSerializer,
                    scope = storeScope,
                    produceFile = { file },
                ),
            )

            assertFalse(repository.languageSelected.first())
            assertEquals(LanguagePreference.BANGLA, repository.language.first())

            repository.setLanguage(LanguagePreference.ENGLISH)

            assertTrue(repository.languageSelected.first())
            assertEquals(LanguagePreference.ENGLISH, repository.language.first())
        } finally {
            storeScope.cancel()
            file.delete()
        }
    }
}
