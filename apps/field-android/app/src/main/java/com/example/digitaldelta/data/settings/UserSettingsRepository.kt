package com.example.digitaldelta.data.settings

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.example.digitaldelta.settings.v1.InterfaceLanguage
import com.example.digitaldelta.settings.v1.UserSettings
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class LanguagePreference {
    BANGLA,
    ENGLISH,
}

interface UserSettingsRepository {
    val language: Flow<LanguagePreference>
    val languageSelected: Flow<Boolean>
    suspend fun setLanguage(language: LanguagePreference)
}

class ProtoUserSettingsRepository(
    private val dataStore: DataStore<UserSettings>,
) : UserSettingsRepository {
    override val language: Flow<LanguagePreference> = dataStore.data
        .catch { emit(UserSettings.getDefaultInstance()) }
        .map { settings ->
            when (settings.interfaceLanguage) {
                InterfaceLanguage.INTERFACE_LANGUAGE_ENGLISH -> LanguagePreference.ENGLISH
                else -> LanguagePreference.BANGLA
            }
        }

    override val languageSelected: Flow<Boolean> = dataStore.data
        .catch { emit(UserSettings.getDefaultInstance()) }
        .map { settings ->
            settings.interfaceLanguage != InterfaceLanguage.INTERFACE_LANGUAGE_UNSPECIFIED
        }

    override suspend fun setLanguage(language: LanguagePreference) {
        dataStore.updateData { settings ->
            settings.toBuilder()
                .setInterfaceLanguage(
                    when (language) {
                        LanguagePreference.BANGLA -> InterfaceLanguage.INTERFACE_LANGUAGE_BANGLA
                        LanguagePreference.ENGLISH -> InterfaceLanguage.INTERFACE_LANGUAGE_ENGLISH
                    },
                )
                .build()
        }
    }
}

object UserSettingsSerializer : Serializer<UserSettings> {
    override val defaultValue: UserSettings = UserSettings.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): UserSettings = try {
        UserSettings.parseFrom(input)
    } catch (exception: InvalidProtocolBufferException) {
        throw CorruptionException("Cannot read user settings.", exception)
    }

    override suspend fun writeTo(t: UserSettings, output: OutputStream) {
        t.writeTo(output)
    }
}

val Context.userSettingsDataStore: DataStore<UserSettings> by dataStore(
    fileName = "user_settings.pb",
    serializer = UserSettingsSerializer,
)
