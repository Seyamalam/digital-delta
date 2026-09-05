package com.example.digitaldelta.domain.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.domain.identity.*
import com.example.digitaldelta.domain.mesh.*
import com.example.digitaldelta.domain.request.*
import com.example.digitaldelta.domain.observer.*
import com.example.digitaldelta.domain.pod.*
import com.example.digitaldelta.domain.fleet.*
import com.example.digitaldelta.domain.routing.SylhetMapParser
import com.example.digitaldelta.domain.routing.VehicleType
import com.example.digitaldelta.proto.v1.*
import com.google.protobuf.ByteString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.UUID

/** Separate Room files and non-overlapping Keystore namespaces, not physical radios. */
@RunWith(AndroidJUnit4::class)
class IndependentFieldWorkflowTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val namespace = "delta-review-${UUID.randomUUID()}"
    private val phones = mutableListOf<Phone>()
    private val issuer = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val trust = object : TrustAnchorRepository {
        override val trustedIssuer = MutableStateFlow<TrustedIssuerKey?>(TrustedIssuerKey(issuer.public.encoded, "test-issuer"))
        override suspend fun pin(publicKeyDer: ByteArray) = error("Already pinned")
    }

    private inner class Phone(val profile: LocalDeviceProfile) {
        val file = "$namespace-${profile.nodeId}.db"
        var db = open()
        val keys = AndroidDeviceIdentityKeyStore("$namespace-${profile.nodeId}")
        val profiles = object : DeviceProfileRepository {
            override val profile = MutableStateFlow(this@Phone.profile)
            override suspend fun select(code: String) = error("Fixed independent device")
        }
        val security get() = AndroidEnvelopeSecurity(keys, db.recipientKeyDao(), trust)
        val recipients get() = RecipientProvisioningRepository(db.recipientKeyDao())
        val protector get() = DirectoryBackedMeshPayloadProtector(RoomRecipientKeyDirectory(db.recipientKeyDao()))
        val publisher get() = MissionEventPublisher(db, profiles, protector, security)
        fun open() = Room.databaseBuilder(context, DeltaDatabase::class.java, file).build()
        fun restart() { db.close(); db = open() }
        suspend fun receive(bytes: ByteArray, previousHop: String) = RoomMeshIngress(db, profile.nodeId,
            AndroidMeshAcknowledgementSigner(profile.nodeId, keys), envelopeVerifier = security).receive(bytes, previousHop)
        suspend fun apply() = CredentialRevocationInboxProcessor(db, keys, recipients, trust, NoOpCredentialRevocationPropagator).process(profile.nodeId)
        suspend fun sendTo(vararg peers: Phone) {
            val transport = object : PeerTransport {
                override suspend fun send(peerId: String, wireBytes: ByteArray) = peers.first { it.profile.nodeId == peerId }.receive(wireBytes, profile.nodeId)
            }
            MeshOutboxDispatcher(db, transport, DirectoryMeshAcknowledgementVerifier(RoomPeerSigningIdentityDirectory(db.recipientKeyDao())))
                .dispatchConnected(peers.map { it.profile.nodeId }.toSet())
        }
    }

    private suspend fun setupPhones(codes: List<String> = listOf(DeviceProfiles.COORDINATOR, DeviceProfiles.RELAY, DeviceProfiles.HOSPITAL)): List<Phone> {
        val nodes = codes.map(DeviceProfiles::require)
        val created = nodes.map { Phone(it).also(phones::add) }
        val now = System.currentTimeMillis()
        for (phone in created) {
            val public = phone.keys.createOrGet(phone.profile.nodeId)
            val claims = IdentityProvisioningClaims.newBuilder().setCredentialId("cred-${phone.profile.nodeId}")
                .setIdentityId(phone.profile.identityId).setNodeId(phone.profile.nodeId).setDisplayName(phone.profile.displayName).setRole(phone.profile.role)
                .setEncryptionKeyId(public.encryptionKeyId).setRsa2048EncryptionPublicKeyDer(ByteString.copyFrom(public.encryptionPublicKeyDer))
                .setSigningKeyId(public.signingKeyId).setRsa2048SigningPublicKeyDer(ByteString.copyFrom(public.signingPublicKeyDer))
                .setIssuedAtUnixMs(now - 60_000).setExpiresAtUnixMs(now + 86_400_000).setIssuerIdentityId("test-admin").build()
            val credential = ProvisioningCredentialService().issue(claims, "test-admin-key", issuer.private.encoded)
            for (replica in created) replica.recipients.accept(credential, issuer.public.encoded, now)
        }
        return created
    }

    @After fun close() {
        phones.forEach { it.db.close(); context.deleteDatabase(it.file) }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        store.aliases().toList().filter { it.startsWith("$namespace-") }.forEach(store::deleteEntry)
    }

    @Test fun operationalPreemptionHoldsCargoWithoutInventingCustodyAndReleasesDriverAfterDelivery() = runTest {
        val (hub, driver, hospital) = setupPhones()
        val graph = SylhetMapParser().parse(context.assets.open("sylhet_map.json").bufferedReader().use { it.readText() }).graph
        fun planner(phone: Phone) = OperationalDispatchPlanner(phone.db, phone.publisher, graph)
        suspend fun request(priority: PriorityClass) = DefaultReliefRequestSubmission(RoomRequestPersistence(hub.db, true), hub.protector,
            envelopeSigner = hub.security).submit(ReliefRequestDraft("N1", "N1", "N6", listOf(CargoDraft("medicine", 5, "pack")),
            priority, false, "", hub.profile.identityId, setOf("RLY-01"))).requestId
        val lower = request(PriorityClass.PRIORITY_CLASS_P2)
        val urgent = request(PriorityClass.PRIORITY_CLASS_P0)
        suspend fun command(phone: Phone, id: String, held: String? = null) = DispatchCommand(id, "RLY-01", VehicleType.TRUCK,
            dispatchReviewIds(phone.db.operationLogDao().forMission(id)), held,
            held?.let { dispatchReviewIds(phone.db.operationLogDao().forMission(it)) }.orEmpty())
        planner(hub).confirm(command(hub, lower))
        val lowerBefore = dispatchReviewIds(hub.db.operationLogDao().forMission(lower))
        assertTrue(runCatching { planner(hub).confirm(command(hub, urgent)) }.isFailure)
        assertEquals(lowerBefore, dispatchReviewIds(hub.db.operationLogDao().forMission(lower)))
        assertTrue(runCatching { planner(hub).confirm(command(hub, urgent, lower).copy(vehicle = VehicleType.BOAT)) }.isFailure)
        val stale = command(hub, urgent, lower)
        hub.publisher.edit(urgent, MissionField.MEDICAL_QUANTITY, "7")
        assertTrue(runCatching { planner(hub).confirm(stale) }.isFailure)
        hub.publisher.edit(urgent, MissionField.PRIORITY, "3")
        assertTrue(runCatching { planner(hub).confirm(command(hub, urgent, lower)) }.isFailure)
        hub.publisher.edit(urgent, MissionField.PRIORITY, "1")
        val beforeFailure = listOf(lower, urgent).associateWith { dispatchReviewIds(hub.db.operationLogDao().forMission(it)) }
        val outboxBeforeFailure = hub.db.outboxDao().activeQueueDepth(System.currentTimeMillis())
        val failedProtector = object : MeshPayloadProtector {
            override suspend fun protect(recipientNodeId: String, plaintext: ByteArray, associatedData: ByteArray): ProtectedPayload {
                // Let the lower mission's hold commit inside the enclosing transaction,
                // then fail the urgent fan-out. Neither revision may escape rollback.
                val event = DomainEvent.parseFrom(plaintext)
                if (event.hasMissionFieldUpdated() && event.missionFieldUpdated.missionId == urgent) error("Injected encryption failure")
                return hub.protector.protect(recipientNodeId, plaintext, associatedData)
            }
        }
        val failedPublisher = MissionEventPublisher(hub.db, hub.profiles, failedProtector, hub.security)
        assertTrue(runCatching { OperationalDispatchPlanner(hub.db, failedPublisher, graph).confirm(command(hub, urgent, lower)) }.isFailure)
        assertEquals(outboxBeforeFailure, hub.db.outboxDao().activeQueueDepth(System.currentTimeMillis()))
        for ((id, expected) in beforeFailure) assertEquals(expected, dispatchReviewIds(hub.db.operationLogDao().forMission(id)))
        hub.sendTo(driver, hospital); driver.apply(); hospital.apply()
        // P1 planning envelopes can overtake the P2 creation; the next inbox
        // application pass retries those causal dependencies without resending.
        driver.apply(); hospital.apply()
        assertTrue(runCatching { planner(driver).confirm(command(driver, urgent, lower)) }.isFailure)
        assertEquals(DispatchReservation.READY, DispatchReservation.decode(dispatchVersion(driver.db.operationLogDao().forMission(lower)).getValue(MissionField.DISPATCH)).state)
        planner(hub).confirm(command(hub, urgent, lower))
        hub.sendTo(driver, hospital); driver.restart(); driver.apply(); hospital.apply()
        for (phone in listOf(hub, driver, hospital)) {
            val history = phone.db.operationLogDao().forMission(lower)
            assertEquals(DispatchReservation.HOLD, DispatchReservation.decode(dispatchVersion(history).getValue(MissionField.DISPATCH)).state)
            assertEquals(urgent, DispatchReservation.decode(dispatchVersion(history).getValue(MissionField.DISPATCH)).preemptedByMissionId)
            assertTrue(history.none { it.eventType == "CUSTODY_TRANSFER" || it.eventType == "PREEMPTION_CONFIRMED" })
            assertTrue(reservedDispatchOperators(history).isEmpty())
            assertEquals(setOf("RLY-01"), reservedDispatchOperators(phone.db.operationLogDao().forMission(urgent)))
        }
        fun custody(phone: Phone, id: String) = OperationalProofOfDeliveryWorkflow(phone.db, phone.keys, phone.recipients, phone.profiles,
            selectedMissionId = { id }, receiptSink = phone.publisher::publishReceipt)
        assertTrue(runCatching { custody(hub, lower).prepare() }.isFailure)
        val offer = custody(hub, urgent).prepare()
        assertTrue(custody(driver, urgent).verify(offer.qrCode) is DeliveryReceiptResult.Verified)
        driver.sendTo(hub, hospital); hub.apply(); hospital.apply()
        assertTrue(runCatching { planner(hub).hold(urgent, dispatchReviewIds(hub.db.operationLogDao().forMission(urgent))) }.isFailure)
        assertTrue(runCatching { planner(hub).confirm(command(hub, lower)) }.isFailure)
        val delivery = custody(driver, urgent).prepare()
        assertTrue(custody(hospital, urgent).verify(delivery.qrCode) is DeliveryReceiptResult.Verified)
        hospital.sendTo(hub, driver); hub.apply(); driver.apply()
        assertTrue(reservedDispatchOperators(hub.db.operationLogDao().forMission(urgent)).isEmpty())
        planner(hub).confirm(command(hub, lower))
        hub.sendTo(driver, hospital); driver.apply(); hospital.apply()
        assertEquals(driver.profile.identityId, custody(hub, lower).prepare().recipientIdentityId)
        assertTrue(custody(hospital, urgent).reconstructChain().valid)
    }

    @Test fun disconnectedCoordinatorsKeepDoubleBookingsVisibleUntilAnExplicitHold() = runTest {
        val (hub, airport, driver, hospital) = setupPhones(listOf(DeviceProfiles.COORDINATOR, DeviceProfiles.AIRPORT, DeviceProfiles.RELAY, DeviceProfiles.HOSPITAL))
        val graph = SylhetMapParser().parse(context.assets.open("sylhet_map.json").bufferedReader().use { it.readText() }).graph
        suspend fun request(priority: PriorityClass) = DefaultReliefRequestSubmission(RoomRequestPersistence(hub.db, true), hub.protector,
            envelopeSigner = hub.security).submit(ReliefRequestDraft("N1", "N1", "N6", listOf(CargoDraft("medicine", 5, "pack")),
            priority, false, "", hub.profile.identityId, setOf("RLY-01", "N2"))).requestId
        val lower = request(PriorityClass.PRIORITY_CLASS_P2)
        val urgent = request(PriorityClass.PRIORITY_CLASS_P0)
        hub.sendTo(airport, driver, hospital); airport.apply(); driver.apply(); hospital.apply()
        suspend fun plan(phone: Phone, id: String) = OperationalDispatchPlanner(phone.db, phone.publisher, graph).confirm(
            DispatchCommand(id, "RLY-01", VehicleType.TRUCK, dispatchReviewIds(phone.db.operationLogDao().forMission(id))))
        plan(hub, lower); plan(airport, urgent)
        hub.sendTo(airport, driver, hospital); airport.sendTo(hub, driver, hospital)
        listOf(hub, airport, driver, hospital).forEach { it.apply() }
        for (phone in listOf(hub, airport, driver, hospital)) {
            assertEquals(setOf("RLY-01"), reservedDispatchOperators(phone.db.operationLogDao().forMission(lower)))
            assertEquals(setOf("RLY-01"), reservedDispatchOperators(phone.db.operationLogDao().forMission(urgent)))
            assertTrue(runCatching { requireDispatchReady(phone.db, lower) }.isFailure)
            assertTrue(runCatching { requireDispatchReady(phone.db, urgent) }.isFailure)
        }
        OperationalDispatchPlanner(hub.db, hub.publisher, graph).hold(lower, dispatchReviewIds(hub.db.operationLogDao().forMission(lower)))
        hub.sendTo(airport, driver, hospital); airport.apply(); driver.apply(); hospital.apply()
        for (phone in listOf(hub, airport, driver, hospital)) {
            requireDispatchReady(phone.db, urgent)
            assertTrue(runCatching { requireDispatchReady(phone.db, lower) }.isFailure)
        }
    }

    @Test fun assignedDriverSignsAnIntermediateHandoffAndCrossingEditsRequireCoordinatorReconciliation() = runTest {
        val (hub, driver, hospital) = setupPhones()
        val submitted = DefaultReliefRequestSubmission(RoomRequestPersistence(hub.db, applyLocalProjection = true), hub.protector,
            envelopeSigner = hub.security).submit(ReliefRequestDraft(hub.profile.nodeId, "N1", hospital.profile.nodeId,
            listOf(CargoDraft("medicine", 5, "pack")), PriorityClass.PRIORITY_CLASS_P0, false, "", hub.profile.identityId,
            participantNodeIds = setOf(driver.profile.nodeId)))
        hub.sendTo(driver, hospital); driver.apply(); hospital.apply()
        hub.publisher.edit(submitted.requestId, MissionField.CUSTODY_PATH, "N1>RLY-01>N6")
        hub.sendTo(driver, hospital); driver.apply(); hospital.apply()
        fun custody(phone: Phone) = OperationalProofOfDeliveryWorkflow(phone.db, phone.keys, phone.recipients, phone.profiles,
            selectedMissionId = { submitted.requestId }, receiptSink = phone.publisher::publishReceipt)
        val offer = custody(hub).prepare()
        assertEquals(driver.profile.identityId, offer.recipientIdentityId)
        // This edit is made on the hub while the signed offer is being accepted elsewhere.
        hub.publisher.edit(submitted.requestId, MissionField.MEDICAL_QUANTITY, "12")
        assertTrue(custody(driver).verify(offer.qrCode) is DeliveryReceiptResult.Verified)
        driver.sendTo(hub, hospital); assertEquals(1, hub.apply().applied); hospital.apply()
        assertTrue(custodyNeedsReconciliation(hub.db.operationLogDao().forMission(submitted.requestId)))
        hub.sendTo(driver, hospital); driver.apply(); hospital.apply()
        assertTrue(runCatching { custody(driver).prepare() }.isFailure)
        assertTrue(runCatching { driver.publisher.reconcile(submitted.requestId, "Keep the signed five packs; create a follow-up request for additional stock.") }.isFailure)
        assertTrue(runCatching { hub.publisher.reconcile(submitted.requestId, "The dialog did not include the crossing edit.", emptySet()) }.isFailure)
        hub.publisher.reconcile(submitted.requestId, "Keep the signed five packs; create a follow-up request for additional stock.")
        hub.sendTo(driver, hospital); driver.apply(); hospital.apply()
        assertFalse(custodyNeedsReconciliation(driver.db.operationLogDao().forMission(submitted.requestId)))
        val second = custody(driver).prepare()
        assertEquals(hospital.profile.identityId, second.recipientIdentityId)
        assertArrayEquals(offer.payloadSha256, second.payloadSha256)
        assertTrue(custody(hospital).verify(second.qrCode) is DeliveryReceiptResult.Verified)
        hospital.sendTo(hub, driver); hub.apply(); driver.apply()
        for (phone in listOf(hub, driver, hospital)) {
            val chain = custody(phone).reconstructChain()
            assertTrue(chain.valid); assertEquals(2, chain.receipts.size)
            assertEquals(chain.receipts[0].recipientIdentityId, chain.receipts[1].senderIdentityId)
            assertTrue(runCatching { custody(phone).prepare() }.isFailure)
        }
        val oldClaims = IdentityProvisioningCredential.parseFrom(hub.db.recipientKeyDao().findByNodeId(hub.profile.nodeId)!!.credentialBytes).claims
        val replacementKeys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val replacement = ProvisioningCredentialService().issue(oldClaims.toBuilder()
            .setCredentialId("replacement-hub-credential").setIssuedAtUnixMs(System.currentTimeMillis() - 1)
            .setEncryptionKeyId("replacement-hub-encryption").setRsa2048EncryptionPublicKeyDer(ByteString.copyFrom(replacementKeys.public.encoded))
            .setSigningKeyId("replacement-hub-signing").setRsa2048SigningPublicKeyDer(ByteString.copyFrom(replacementKeys.public.encoded)).build(),
            "test-admin-key", issuer.private.encoded)
        for (phone in listOf(hub, driver, hospital)) {
            phone.recipients.accept(replacement, issuer.public.encoded)
            assertEquals("replacement-hub-signing", phone.recipients.installedIdentity(hub.profile.nodeId)?.signingKeyId)
            assertTrue(custody(phone).reconstructChain().valid)
        }
    }

    @Test fun recordedMissionPlanUsesAcceptedDestinationAndSurvivesOfflinePublication() = runTest {
        val (a, _, c) = setupPhones()
        val submitted = DefaultReliefRequestSubmission(RoomRequestPersistence(a.db, applyLocalProjection = true), a.protector,
            envelopeSigner = a.security).submit(ReliefRequestDraft(a.profile.nodeId, "N1", c.profile.nodeId,
            listOf(CargoDraft("medicine", 5, "pack")), PriorityClass.PRIORITY_CLASS_P0, false, "", a.profile.identityId))
        val graph = com.example.digitaldelta.domain.routing.SylhetMapParser().parse(context.assets.open("sylhet_map.json").bufferedReader().use { it.readText() }).graph
        fun recorder() = com.example.digitaldelta.domain.routing.MissionPlanRecorder(a.db, a.profiles, a.keys, graph)
        val events = recorder().record(submitted.requestId)
        assertEquals(listOf("E5"), events.single { it.hasRoutePlanned() }.routePlanned.edgeIdsList)
        assertEquals(120, events.single { it.hasRoutePlanned() }.routePlanned.etaMinutes)
        assertTrue(events.all { it.simulated && it.scenarioSeed == "packaged-network-v1" })
        val evaluation = events.single { it.hasSlaEvaluated() }.slaEvaluated
        assertTrue(evaluation.slowedArrivalMinutes > 120)
        assertEquals("BREACH", evaluation.stateCode)
        assertEquals(events.first().eventId, evaluation.routeEventId)
        val configuration = ObserverConfiguration("https://observer.invalid/v1/observations", a.profile.nodeId, "test-token-never-sent")
        assertFalse(ObserverPublisher(a.db, ObservationTransport { _, _ -> error("Laptop unavailable") }).drain(configuration))
        a.restart()
        val sent = mutableListOf<org.json.JSONObject>()
        val publisher = ObserverPublisher(a.db, ObservationTransport { _, body -> sent += org.json.JSONObject(body.decodeToString()); 201 })
        assertTrue(publisher.drain(configuration))
        assertEquals(3, sent.size)
        assertTrue(publisher.drain(configuration))
        assertEquals(3, sent.size)
        assertEquals(events.map { it.eventId }.toSet(), sent.filter { it.getString("kind") != "reliefRequestCreated" }.map { it.getString("eventId") }.toSet())
        assertFalse(sent.any { it.toString().contains("medicine") })
        a.publisher.edit(submitted.requestId, MissionField.PRIORITY, "4")
        val withinSla = recorder().record(submitted.requestId)
        assertEquals(2, withinSla.size)
        assertEquals("WITHIN_SLA", withinSla.last().slaEvaluated.stateCode)
        assertEquals(withinSla.first().eventId, withinSla.last().slaEvaluated.routeEventId)
        a.publisher.edit(submitted.requestId, MissionField.DESTINATION, "N7")
        val noRoute = recorder().record(submitted.requestId)
        assertEquals(2, noRoute.size)
        assertEquals(0, noRoute.first().routePlanned.edgeIdsCount)
        assertEquals("NO_FEASIBLE_GROUND_ROUTE", noRoute.first().routePlanned.explanationCode)
        assertEquals("NO_ROUTE", noRoute.last().slaEvaluated.stateCode)
        assertEquals(0, noRoute.last().slaEvaluated.slowedArrivalMinutes)
        assertTrue(publisher.drain(configuration))
        assertEquals(listOf("BREACH", "WITHIN_SLA", "NO_ROUTE"), sent.filter { it.getString("kind") == "slaEvaluated" }
            .map { it.getJSONObject("presentation").getString("stateCode") })
        a.publisher.edit(submitted.requestId, MissionField.DESTINATION, "N1")
        val sameNode = recorder().record(submitted.requestId)
        assertEquals("ALREADY_AT_DESTINATION", sameNode.first().routePlanned.explanationCode)
        assertEquals("WITHIN_SLA", sameNode.last().slaEvaluated.stateCode)
        val authority = a.db.recipientKeyDao().findByNodeId(a.profile.nodeId)!!
        a.db.recipientKeyDao().upsert(authority.copy(revokedAtUnixMs = System.currentTimeMillis()))
        assertTrue(runCatching { recorder().record(submitted.requestId) }.isFailure)
    }

    @Test fun signedRequestSurvivesRelayRestartThenIndependentEditsConvergeAndCustodyVerifies() = runTest {
        val (a, b, c) = setupPhones()
        val receipt = DefaultReliefRequestSubmission(RoomRequestPersistence(a.db, applyLocalProjection = true), a.protector,
            envelopeSigner = a.security).submit(ReliefRequestDraft(a.profile.nodeId, "N1", c.profile.nodeId,
            listOf(CargoDraft("medicine", 5, "pack")), PriorityClass.PRIORITY_CLASS_P0, false, "", a.profile.identityId))
        val original = a.db.outboxDao().find(receipt.messageId)!!
        a.sendTo(b)
        assertTrue(b.db.operationLogDao().forMission(receipt.requestId).isEmpty()) // relay cannot decrypt/apply
        b.restart()
        b.sendTo(a, c) // Origin stays connected and sorts first; direct recipient wins.
        assertEquals(1, c.apply().applied)
        c.receive(original.wireBytes, a.profile.nodeId)
        c.restart()
        assertEquals(0, c.apply().applied)
        assertEquals("5", c.db.missionProjectionDao().find(receipt.requestId, "MEDICAL_QUANTITY")?.value)
        assertEquals(1, c.db.operationLogDao().forMission(receipt.requestId).size)

        a.publisher.edit(receipt.requestId, MissionField.PRIORITY, "2")
        c.publisher.edit(receipt.requestId, MissionField.PRIORITY, "3")
        c.sendTo(a); assertEquals(1, a.apply().applied)
        a.sendTo(c); assertEquals(1, c.apply().applied)
        val conflictA = a.db.conflictDao().latestForMission(receipt.requestId)!!
        val conflictC = c.db.conflictDao().latestForMission(receipt.requestId)!!
        assertEquals(conflictA.conflictId, conflictC.conflictId)
        assertEquals("OPEN", conflictA.state)
        a.publisher.resolve(conflictA.conflictId, ConflictSide.LEFT)
        a.sendTo(c); assertEquals(1, c.apply().applied)
        assertEquals(a.db.missionProjectionDao().find(receipt.requestId, "PRIORITY")?.convergenceHash,
            c.db.missionProjectionDao().find(receipt.requestId, "PRIORITY")?.convergenceHash)

        val sender = OperationalProofOfDeliveryWorkflow(a.db, a.keys, a.recipients, a.profiles)
        val recipient = OperationalProofOfDeliveryWorkflow(c.db, c.keys, c.recipients, c.profiles)
        val offer = sender.prepare()
        assertFalse(offer.simulatedVehicle)
        assertEquals(a.profile.identityId, offer.senderIdentityId)
        assertEquals(DeliveryOfferRejection.INVALID_SIGNATURE,
            (recipient.verify(sender.tamperForDemo(offer.qrCode)) as DeliveryReceiptResult.Rejected).reason)
        val accepted = recipient.verify(offer.qrCode) as DeliveryReceiptResult.Verified
        assertEquals(1, accepted.chain.size)
        assertTrue(recipient.reconstructChain().valid)
        assertEquals(DeliveryOfferRejection.REPLAY_REJECTED, (recipient.verify(offer.qrCode) as DeliveryReceiptResult.Rejected).reason)
    }

    @Test fun optionalPublicationRetriesAfterDisconnectWithoutRepeatingFieldEffects() = runTest {
        val (a) = setupPhones()
        val receipt = DefaultReliefRequestSubmission(RoomRequestPersistence(a.db, applyLocalProjection = true), a.protector,
            envelopeSigner = a.security).submit(ReliefRequestDraft("N1", "N1", "N6", listOf(CargoDraft("medicine", 5, "pack")), PriorityClass.PRIORITY_CLASS_P0, true, "android-observer-recovery-test", a.profile.identityId))
        var online = false
        val published = mutableListOf<String>()
        val transport = ObservationTransport { _, bytes ->
            check(online) { "laptop disconnected" }
            val text = bytes.decodeToString()
            assertFalse(text.contains("medicine")); assertFalse(text.contains("payload")); assertFalse(text.contains("signature"))
            published += org.json.JSONObject(text).getString("eventId")
            201
        }
        val config = ObserverConfiguration("https://observer.example/v1/observations", "N1", "test-token-not-for-production-123456")
        assertFalse(ObserverPublisher(a.db, transport).drain(config))
        assertNotNull(a.db.outboxDao().find(receipt.messageId))
        a.restart(); online = true
        assertTrue(ObserverPublisher(a.db, transport).drain(config))
        assertTrue(ObserverPublisher(a.db, transport).drain(config))
        assertEquals(1, published.size)
        assertEquals(1, a.db.operationLogDao().forMission(receipt.requestId).size)
        // Optional additional live boundary evidence. Never required for offline field checks.
        // The ignored, debug-only credential is created by the local evidence setup.
        if (context.assets.list("")?.contains("observer-local-evidence.json") == true) {
            val configCode = context.assets.open("observer-local-evidence.json").bufferedReader().use { it.readText() }
            val local = ObserverConfiguration.parse(configCode, allowEmulatorHttp = true)
            assertTrue(ObserverPublisher(a.db, HttpObservationTransport()).drain(local))
            assertTrue(ObserverPublisher(a.db, HttpObservationTransport()).drain(local))
            val records = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                java.net.URI(local.endpoint).toURL().openConnection().apply { connectTimeout = 5_000; readTimeout = 5_000 }
                    .getInputStream().bufferedReader().use { it.readText() }
            }
            val rows = org.json.JSONObject(records).getJSONArray("observations")
            val matching = (0 until rows.length()).map(rows::getJSONObject).filter { it.getString("eventId") == published.single() }
            assertEquals(1, matching.size)
            assertEquals(receipt.requestId, matching.single().getJSONObject("presentation").getString("requestId"))
            assertTrue(matching.single().getBoolean("simulated"))
            android.util.Log.i("DeltaObserverEvidence", "HTTP_HONO_MATCH eventId=${published.single()} requestId=${receipt.requestId}")
        }
    }

    @Test fun knownRevocationRejectsCryptographicallyValidFreshPeerProof() = runTest {
        val (a, _, b) = setupPhones()
        val verifier = AndroidPeerIdentityAuthenticator(a.profile.nodeId, a.keys, a.db.recipientKeyDao(), trust)
        val prover = AndroidPeerIdentityAuthenticator(b.profile.nodeId, b.keys, b.db.recipientKeyDao(), trust)
        val record = a.db.recipientKeyDao().findByNodeId(b.profile.nodeId)!!
        a.db.recipientKeyDao().upsert(record.copy(revokedAtUnixMs = System.currentTimeMillis()))
        val challenge = verifier.createChallenge()
        val proof = prover.createProof(challenge) // B has not learned its revocation.
        assertTrue(PeerIdentityAuthentication.verifyProof(proof, challenge, b.profile.nodeId, issuer.public.encoded, System.currentTimeMillis()))
        assertFalse(verifier.verifyProof(proof, challenge, b.profile.nodeId))
        assertFalse(verifier.isActive(b.profile.nodeId))
        assertNotNull(a.db.recipientKeyDao().findByNodeId(b.profile.nodeId)!!.revokedAtUnixMs)
    }

    @Test fun dualSignaturesDoNotGrantAClinicPermissionToAcceptCustody() = runTest {
        val (hub, clinic) = setupPhones(listOf(DeviceProfiles.COORDINATOR, DeviceProfiles.CLINIC))
        val scenario = DeliveryScenario("role-test", "delivery-role-test", "N1", "N4", hub.profile.identityId, clinic.profile.identityId, "medicine:5", "", false)
        val workflow = RoomProofOfDeliveryWorkflow(hub.db, hub.keys, scenario, senderCredentialLookup = hub.recipients::installedIdentity)
        val signed = DeliveryOfferCodec().decodeCode(workflow.prepare().qrCode)
        val offer = signed.offer
        val unsigned = CustodyTransfer.newBuilder().setMissionId(scenario.missionId).setDeliveryId(offer.deliveryId)
            .setSenderIdentityId(offer.senderIdentityId).setRecipientIdentityId(offer.recipientIdentityId)
            .setPayloadSha256(offer.payloadSha256).setNonce(offer.nonce).setTimestampUnixMs(offer.timestampUnixMs)
            .setPreviousReceiptSha256(offer.previousReceiptSha256).setSenderSignature(signed.senderSignature).build()
        val transfer = unsigned.toBuilder().setRecipientSignature(com.example.digitaldelta.proto.v1.Signature.newBuilder()
            .setKeyId(clinic.keys.createOrGet("N4").signingKeyId).setAlgorithm(DeliveryOfferCodec.SIGNATURE_ALGORITHM)
            .setRsa2048PssSha256(ByteString.copyFrom(clinic.keys.sign("N4", unsigned.toByteArray())))).build()
        val event = DomainEvent.newBuilder().setSchemaVersion(1).setEventId("unauthorized-clinic-receipt")
            .setActorIdentityId(clinic.profile.identityId).setOccurredAtUnixMs(offer.timestampUnixMs).setCustodyTransfer(transfer).build()
        val error = runCatching { workflow.importReceipt(event) }.exceptionOrNull()
        assertEquals("Custody signer was not authorized at handoff", error?.message)
        assertTrue(hub.db.operationLogDao().forMission(scenario.missionId).isEmpty())
    }

    @Test fun clinicRequestReachesCoordinatorThreeWritersConvergeAndSignedReceiptReturnsToOrigin() = runTest {
        val (clinic, hub, hospital) = setupPhones(listOf(DeviceProfiles.CLINIC, DeviceProfiles.COORDINATOR, DeviceProfiles.HOSPITAL))
        val receipt = DefaultReliefRequestSubmission(RoomRequestPersistence(clinic.db, applyLocalProjection = true), clinic.protector,
            envelopeSigner = clinic.security).submit(ReliefRequestDraft(clinic.profile.nodeId, hub.profile.nodeId, hospital.profile.nodeId,
            listOf(CargoDraft("medicine", 5, "pack")), PriorityClass.PRIORITY_CLASS_P0, false, "", clinic.profile.identityId))
        clinic.sendTo(hub, hospital)
        assertEquals(1, hub.apply().applied)
        assertEquals(1, hospital.apply().applied)
        clinic.publisher.edit(receipt.requestId, MissionField.PRIORITY, "2")
        hub.publisher.edit(receipt.requestId, MissionField.PRIORITY, "3")
        hospital.publisher.edit(receipt.requestId, MissionField.PRIORITY, "4")
        // Each replica receives its concurrent revisions in a different order.
        hospital.sendTo(clinic, hub); clinic.apply(); hub.apply()
        clinic.sendTo(hub, hospital); hub.apply(); hospital.apply()
        hub.sendTo(hospital, clinic); hospital.apply(); clinic.apply()
        val hashes = listOf(clinic, hub, hospital).map { it.db.missionProjectionDao().find(receipt.requestId, "PRIORITY")!!.convergenceHash }
        assertEquals(1, hashes.toSet().size)
        listOf(clinic, hub, hospital).forEach { assertEquals(3, it.db.conflictDao().countForMission(receipt.requestId)) }
        hub.publisher.resolve(hub.db.conflictDao().latestForMission(receipt.requestId)!!.conflictId, ConflictSide.LEFT)
        hub.sendTo(hospital); hospital.apply() // Clinic has not received the resolution yet.
        listOf(hub, hospital).forEach { assertFalse(it.db.conflictDao().hasOpen(receipt.requestId)) }
        assertEquals(hub.db.missionProjectionDao().find(receipt.requestId, "PRIORITY")!!.convergenceHash,
            hospital.db.missionProjectionDao().find(receipt.requestId, "PRIORITY")!!.convergenceHash)

        val sender = OperationalProofOfDeliveryWorkflow(hub.db, hub.keys, hub.recipients, hub.profiles)
        val receiver = OperationalProofOfDeliveryWorkflow(hospital.db, hospital.keys, hospital.recipients, hospital.profiles,
            receiptSink = hospital.publisher::publishReceipt)
        val offer = sender.prepare()
        assertEquals(hub.profile.identityId, offer.senderIdentityId)
        val accepted = receiver.verify(offer.qrCode) as DeliveryReceiptResult.Verified
        // Origin is still offline from acceptance and makes a later request revision.
        hub.publisher.edit(receipt.requestId, MissionField.MEDICAL_QUANTITY, "6")
        hospital.restart() // Receipt and encrypted return copies survive independently.
        val earlyReceipt = hospital.db.outboxDao().pending(System.currentTimeMillis(), 100).single {
            val envelope = MeshWireCodec.decode(it.wireBytes)
            envelope.senderNodeId == hospital.profile.nodeId && envelope.recipientNodeId == clinic.profile.nodeId
        }
        clinic.receive(earlyReceipt.wireBytes, hospital.profile.nodeId)
        assertEquals(1, clinic.apply().retry) // Pinned resolution dependency, never terminal rejection.
        hospital.sendTo(hub)
        assertEquals(1, hub.apply().applied)
        hub.sendTo(clinic)
        clinic.apply(); clinic.apply()
        assertFalse(clinic.db.conflictDao().hasOpen(receipt.requestId))
        assertEquals(1, clinic.db.operationLogDao().forMission(receipt.requestId).count { it.eventType == "CUSTODY_TRANSFER" })
        val reconstructed = sender.reconstructChain()
        assertTrue(reconstructed.valid)
        assertArrayEquals(accepted.receipt.receiptHash, reconstructed.receipts.single().receiptHash)
        assertTrue(runCatching { sender.prepare() }.isFailure) // Delivered stock cannot be offered again.
        assertTrue(runCatching { hub.publisher.edit(receipt.requestId, MissionField.DESTINATION, "N4") }.isFailure)
        assertEquals(1, hub.db.operationLogDao().forMission(receipt.requestId).count { it.eventType == "CUSTODY_TRANSFER" })
    }
}
