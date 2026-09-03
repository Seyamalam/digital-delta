package com.example.digitaldelta.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.room.Room
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.data.local.NonceDao
import com.example.digitaldelta.data.local.OperationLogDao
import com.example.digitaldelta.data.local.OutboxDao
import com.example.digitaldelta.data.settings.ProtoUserSettingsRepository
import com.example.digitaldelta.data.settings.UserSettingsRepository
import com.example.digitaldelta.data.settings.userSettingsDataStore
import com.example.digitaldelta.settings.v1.UserSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DeltaDatabase =
        Room.databaseBuilder(context, DeltaDatabase::class.java, "digital-delta.db").build()

    @Provides
    fun provideOutboxDao(database: DeltaDatabase): OutboxDao = database.outboxDao()

    @Provides
    fun provideNonceDao(database: DeltaDatabase): NonceDao = database.nonceDao()

    @Provides
    fun provideOperationLogDao(database: DeltaDatabase): OperationLogDao = database.operationLogDao()

    @Provides
    @Singleton
    fun provideUserSettingsDataStore(@ApplicationContext context: Context): DataStore<UserSettings> =
        context.userSettingsDataStore

    @Provides
    @Singleton
    fun provideSettingsRepository(dataStore: DataStore<UserSettings>): UserSettingsRepository =
        ProtoUserSettingsRepository(dataStore)
}
