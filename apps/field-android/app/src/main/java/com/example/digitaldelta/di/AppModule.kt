package com.example.digitaldelta.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.room.Room
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.data.local.DeltaMigrations
import com.example.digitaldelta.data.local.NonceDao
import com.example.digitaldelta.data.local.OperationLogDao
import com.example.digitaldelta.data.local.OutboxDao
import com.example.digitaldelta.data.local.RecipientKeyDao
import com.example.digitaldelta.data.settings.ProtoUserSettingsRepository
import com.example.digitaldelta.data.settings.UserSettingsRepository
import com.example.digitaldelta.data.settings.userSettingsDataStore
import com.example.digitaldelta.domain.mesh.AndroidKeystorePayloadProtector
import com.example.digitaldelta.domain.mesh.MeshPayloadProtector
import com.example.digitaldelta.domain.mesh.RecipientKeyDirectory
import com.example.digitaldelta.domain.identity.AndroidDeviceIdentityKeyStore
import com.example.digitaldelta.domain.identity.DefaultIdentityProvisioningCoordinator
import com.example.digitaldelta.domain.identity.IdentityProvisioningCoordinator
import com.example.digitaldelta.domain.identity.ProtoTrustAnchorRepository
import com.example.digitaldelta.domain.identity.RecipientProvisioningRepository
import com.example.digitaldelta.domain.identity.RoomRecipientKeyDirectory
import com.example.digitaldelta.domain.identity.TrustAnchorRepository
import com.example.digitaldelta.domain.request.DefaultReliefRequestSubmission
import com.example.digitaldelta.domain.request.ReliefRequestSubmission
import com.example.digitaldelta.domain.request.RoomRequestPersistence
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
        Room.databaseBuilder(context, DeltaDatabase::class.java, "digital-delta.db")
            .addMigrations(DeltaMigrations.VERSION_1_TO_2)
            .build()

    @Provides
    fun provideOutboxDao(database: DeltaDatabase): OutboxDao = database.outboxDao()

    @Provides
    fun provideNonceDao(database: DeltaDatabase): NonceDao = database.nonceDao()

    @Provides
    fun provideOperationLogDao(database: DeltaDatabase): OperationLogDao = database.operationLogDao()

    @Provides
    fun provideRecipientKeyDao(database: DeltaDatabase): RecipientKeyDao = database.recipientKeyDao()

    @Provides
    @Singleton
    fun provideRecipientKeyDirectory(dao: RecipientKeyDao): RecipientKeyDirectory =
        RoomRecipientKeyDirectory(dao)

    @Provides
    @Singleton
    fun provideRecipientProvisioningRepository(dao: RecipientKeyDao): RecipientProvisioningRepository =
        RecipientProvisioningRepository(dao)

    @Provides
    @Singleton
    fun provideDeviceIdentityKeyStore(): AndroidDeviceIdentityKeyStore = AndroidDeviceIdentityKeyStore()

    @Provides
    @Singleton
    fun provideTrustAnchorRepository(dataStore: DataStore<UserSettings>): TrustAnchorRepository =
        ProtoTrustAnchorRepository(dataStore)

    @Provides
    @Singleton
    fun provideIdentityProvisioningCoordinator(
        deviceKeys: AndroidDeviceIdentityKeyStore,
        trustAnchors: TrustAnchorRepository,
        recipients: RecipientProvisioningRepository,
    ): IdentityProvisioningCoordinator = DefaultIdentityProvisioningCoordinator(
        deviceKeys = deviceKeys,
        trustAnchors = trustAnchors,
        recipients = recipients,
    )

    @Provides
    @Singleton
    fun provideUserSettingsDataStore(@ApplicationContext context: Context): DataStore<UserSettings> =
        context.userSettingsDataStore

    @Provides
    @Singleton
    fun provideSettingsRepository(dataStore: DataStore<UserSettings>): UserSettingsRepository =
        ProtoUserSettingsRepository(dataStore)

    @Provides
    @Singleton
    fun providePayloadProtector(): MeshPayloadProtector = AndroidKeystorePayloadProtector()

    @Provides
    @Singleton
    fun provideRequestSubmission(
        database: DeltaDatabase,
        payloadProtector: MeshPayloadProtector,
    ): ReliefRequestSubmission = DefaultReliefRequestSubmission(
        persistence = RoomRequestPersistence(database),
        payloadProtector = payloadProtector,
    )
}
