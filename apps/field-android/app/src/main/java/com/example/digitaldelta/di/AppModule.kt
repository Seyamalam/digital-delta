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
import com.example.digitaldelta.data.settings.OfflinePinRepository
import com.example.digitaldelta.data.settings.ProtoOfflinePinRepository
import com.example.digitaldelta.data.settings.userSettingsDataStore
import com.example.digitaldelta.domain.mesh.DirectoryBackedMeshPayloadProtector
import com.example.digitaldelta.domain.mesh.MeshPayloadProtector
import com.example.digitaldelta.domain.mesh.RecipientKeyDirectory
import com.example.digitaldelta.domain.identity.AndroidDeviceIdentityKeyStore
import com.example.digitaldelta.domain.identity.AuthorizationAuditTrail
import com.example.digitaldelta.domain.identity.RoomSignedAuthorizationAuditTrail
import com.example.digitaldelta.domain.identity.DefaultIdentityProvisioningCoordinator
import com.example.digitaldelta.domain.identity.DeviceProfileRepository
import com.example.digitaldelta.domain.identity.IdentityProvisioningCoordinator
import com.example.digitaldelta.domain.identity.ProtoTrustAnchorRepository
import com.example.digitaldelta.domain.identity.ProtoDeviceProfileRepository
import com.example.digitaldelta.domain.identity.RecipientProvisioningRepository
import com.example.digitaldelta.domain.identity.RoomRecipientKeyDirectory
import com.example.digitaldelta.domain.identity.CredentialRevocationPropagator
import com.example.digitaldelta.domain.identity.RoomCredentialRevocationPropagator
import com.example.digitaldelta.domain.identity.CredentialRevocationInboxProcessor
import com.example.digitaldelta.domain.identity.TrustAnchorRepository
import com.example.digitaldelta.domain.request.DefaultReliefRequestSubmission
import com.example.digitaldelta.domain.request.ReliefRequestSubmission
import com.example.digitaldelta.domain.request.RoomRequestPersistence
import com.example.digitaldelta.domain.sync.ConflictCoordinator
import com.example.digitaldelta.domain.sync.RoomConflictCoordinator
import com.example.digitaldelta.domain.routing.OfflineRouteScenario
import com.example.digitaldelta.domain.routing.DynamicRouteEngine
import com.example.digitaldelta.domain.routing.RoutePlanner
import com.example.digitaldelta.domain.routing.RouteScenario
import com.example.digitaldelta.domain.routing.SylhetMapParser
import com.example.digitaldelta.domain.triage.RoomTriageWorkflow
import com.example.digitaldelta.domain.triage.TriageWorkflow
import com.example.digitaldelta.domain.pod.ProofOfDeliveryWorkflow
import com.example.digitaldelta.domain.pod.RoomProofOfDeliveryWorkflow
import com.example.digitaldelta.domain.prediction.AssetOnnxRouteRiskPredictor
import com.example.digitaldelta.domain.prediction.ResilientRouteRiskPredictor
import com.example.digitaldelta.domain.prediction.RouteRiskPredictor
import com.example.digitaldelta.domain.fleet.DefaultHybridFleetWorkflow
import com.example.digitaldelta.domain.fleet.FleetOrchestrator
import com.example.digitaldelta.domain.fleet.GeoPoint
import com.example.digitaldelta.domain.fleet.HybridFleetInputs
import com.example.digitaldelta.domain.fleet.HybridFleetMission
import com.example.digitaldelta.domain.fleet.HybridFleetWorkflow
import com.example.digitaldelta.domain.fleet.NamedPoint
import com.example.digitaldelta.domain.fleet.RoomHybridFleetEventRecorder
import com.example.digitaldelta.domain.pod.DeliveryScenario
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
            .addMigrations(
                DeltaMigrations.VERSION_1_TO_2,
                DeltaMigrations.VERSION_2_TO_3,
                DeltaMigrations.VERSION_3_TO_4,
                DeltaMigrations.VERSION_4_TO_5,
                DeltaMigrations.VERSION_5_TO_6,
                DeltaMigrations.VERSION_6_TO_7,
            )
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
    fun provideCredentialRevocationPropagator(database: DeltaDatabase, deviceKeys: AndroidDeviceIdentityKeyStore, trustAnchors: TrustAnchorRepository): CredentialRevocationPropagator =
        RoomCredentialRevocationPropagator(database, envelopeSigner = com.example.digitaldelta.domain.mesh.AndroidEnvelopeSecurity(deviceKeys, database.recipientKeyDao(), trustAnchors))

    @Provides
    @Singleton
    fun provideCredentialRevocationInboxProcessor(
        database: DeltaDatabase,
        deviceKeys: AndroidDeviceIdentityKeyStore,
        recipients: RecipientProvisioningRepository,
        trustAnchors: TrustAnchorRepository,
        propagator: CredentialRevocationPropagator,
    ): CredentialRevocationInboxProcessor = CredentialRevocationInboxProcessor(
        database,
        deviceKeys,
        recipients,
        trustAnchors,
        propagator,
    )

    @Provides
    @Singleton
    fun provideDeviceIdentityKeyStore(): AndroidDeviceIdentityKeyStore = AndroidDeviceIdentityKeyStore()

    @Provides
    @Singleton
    fun provideAuthorizationAuditTrail(
        database: DeltaDatabase,
        deviceKeys: AndroidDeviceIdentityKeyStore,
    ): AuthorizationAuditTrail = RoomSignedAuthorizationAuditTrail(database, deviceKeys)

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
        deviceProfiles: DeviceProfileRepository,
        revocationPropagator: CredentialRevocationPropagator,
    ): IdentityProvisioningCoordinator = DefaultIdentityProvisioningCoordinator(
        deviceKeys = deviceKeys,
        trustAnchors = trustAnchors,
        recipients = recipients,
        deviceProfiles = deviceProfiles,
        revocationPropagator = revocationPropagator,
    )

    @Provides
    @Singleton
    fun provideUserSettingsDataStore(@ApplicationContext context: Context): DataStore<UserSettings> =
        context.userSettingsDataStore

    @Provides
    @Singleton
    fun provideDeviceProfileRepository(dataStore: DataStore<UserSettings>): DeviceProfileRepository =
        ProtoDeviceProfileRepository(dataStore)

    @Provides
    @Singleton
    fun provideSettingsRepository(dataStore: DataStore<UserSettings>): UserSettingsRepository =
        ProtoUserSettingsRepository(dataStore)

    @Provides
    @Singleton
    fun provideOfflinePinRepository(dataStore: DataStore<UserSettings>): OfflinePinRepository =
        ProtoOfflinePinRepository(dataStore)

    @Provides
    @Singleton
    fun providePayloadProtector(recipientKeys: RecipientKeyDirectory): MeshPayloadProtector =
        DirectoryBackedMeshPayloadProtector(recipientKeys)

    @Provides
    @Singleton
    fun provideRequestSubmission(
        database: DeltaDatabase,
        payloadProtector: MeshPayloadProtector,
        deviceKeys: AndroidDeviceIdentityKeyStore,
        trustAnchors: TrustAnchorRepository,
    ): ReliefRequestSubmission = DefaultReliefRequestSubmission(
        persistence = RoomRequestPersistence(database),
        payloadProtector = payloadProtector,
        envelopeSigner = com.example.digitaldelta.domain.mesh.AndroidEnvelopeSecurity(deviceKeys, database.recipientKeyDao(), trustAnchors),
    )

    @Provides
    @Singleton
    fun provideConflictCoordinator(database: DeltaDatabase): ConflictCoordinator =
        RoomConflictCoordinator(database)

    @Provides
    @Singleton
    fun provideRouteScenario(@ApplicationContext context: Context): RouteScenario {
        val fixture = context.assets.open("sylhet_map.json").bufferedReader().use { it.readText() }
        return OfflineRouteScenario(
            initialGraph = SylhetMapParser().parse(fixture).graph,
            engine = DynamicRouteEngine(
                planner = RoutePlanner(riskPenaltyMinutes = 180),
                allowRiskDrivenFallback = true,
            ),
        )
    }

    @Provides
    @Singleton
    fun provideRouteRiskPredictor(@ApplicationContext context: Context): RouteRiskPredictor =
        ResilientRouteRiskPredictor(AssetOnnxRouteRiskPredictor(context))

    @Provides
    @Singleton
    fun provideTriageWorkflow(database: DeltaDatabase): TriageWorkflow = RoomTriageWorkflow(database)

    @Provides
    @Singleton
    fun provideProofOfDeliveryWorkflow(
        database: DeltaDatabase,
        deviceKeys: AndroidDeviceIdentityKeyStore,
        recipients: RecipientProvisioningRepository,
    ): ProofOfDeliveryWorkflow = RoomProofOfDeliveryWorkflow(
        database,
        deviceKeys,
        senderCredentialLookup = recipients::installedIdentity,
    )

    @Provides
    @Singleton
    fun provideHybridFleetWorkflow(
        @ApplicationContext context: Context,
        database: DeltaDatabase,
        deviceKeys: AndroidDeviceIdentityKeyStore,
        recipients: RecipientProvisioningRepository,
    ): HybridFleetWorkflow {
        val fixture = context.assets.open("sylhet_map.json").bufferedReader().use { it.readText() }
        val graph = SylhetMapParser().parse(fixture).graph
        val mission = HybridFleetMission(
            missionId = "mission-drone-demo-01",
            originNodeId = "N1",
            destinationNodeId = "N7",
            boatVehicleId = "boat-02",
            droneVehicleId = "drone-07",
            graph = graph,
            rendezvousInputs = HybridFleetInputs(
                boatPosition = GeoPoint(25.0400, 91.5700),
                droneBase = GeoPoint(24.9632, 91.8668),
                droneDestination = GeoPoint(25.1200, 91.6800),
                candidates = listOf(
                    NamedPoint("R1", GeoPoint(25.0658, 91.6073)),
                    NamedPoint("R2", GeoPoint(25.0715, 91.7554)),
                    NamedPoint("R3", GeoPoint(25.0200, 91.7000)),
                ),
                boatSpeedKph = 24.0,
                droneSpeedKph = 55.0,
                droneBatteryPercent = 74,
                droneRangeAtFullChargeKm = 60.0,
                reserveBatteryPercent = 20,
            ),
            simulated = true,
        )
        val droneCustody = RoomProofOfDeliveryWorkflow(
            database = database,
            deviceKeys = deviceKeys,
            scenario = DeliveryScenario(
                missionId = mission.missionId,
                deliveryId = "DELTA-DRONE-0001",
                senderNodeId = "pod-demo-boat-02",
                recipientNodeId = "pod-demo-drone-07",
                senderIdentityId = "boat-operator-02",
                recipientIdentityId = "simulated-drone-07",
                payloadDescription = "p0-medicine:4|blood-cooler:1",
                scenarioSeed = "m8-drone-handoff-v1",
                simulatedVehicle = true,
            ),
            senderCredentialLookup = recipients::installedIdentity,
        )
        return DefaultHybridFleetWorkflow(
            mission = mission,
            orchestrator = FleetOrchestrator(),
            proofOfDelivery = droneCustody,
            eventRecorder = RoomHybridFleetEventRecorder(database),
        )
    }
}
