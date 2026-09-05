package com.example.digitaldelta.domain.pod

import androidx.room.withTransaction
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.domain.identity.*
import com.example.digitaldelta.proto.v1.DomainEvent
import com.example.digitaldelta.proto.v1.MissionCustodySnapshot
import com.example.digitaldelta.domain.sync.*
import kotlinx.coroutines.flow.first
import java.util.Base64

/** Field handoff uses provisioned mission participants. No demo-key fallback. */
class OperationalProofOfDeliveryWorkflow(
    private val database: DeltaDatabase,
    private val keys: AndroidDeviceIdentityKeyStore,
    private val recipients: RecipientProvisioningRepository,
    private val profiles: DeviceProfileRepository? = null,
    private val selectedMissionId: () -> String? = { null },
    private val receiptSink: suspend (DomainEvent) -> Unit = {},
) : ProofOfDeliveryWorkflow {
    private var lastMissionId: String? = null

    override suspend fun prepare(): DeliveryOfferReady = database.withTransaction {
        val profile = activeProfile(Permission.OFFER_CUSTODY)
        val mission = selectedMissionId() ?: database.operationLogDao().requests().firstOrNull {
            DomainEvent.parseFrom(it.payloadBytes).reliefRequestCreated.originNodeId == profile.nodeId
        }?.missionId ?: error("Select an accepted mission whose custodian is this phone")
        require(!custodyNeedsReconciliation(database.operationLogDao().forMission(mission))) { "Reconcile crossing revisions first" }
        val receipts = orderedCustodyEvents(database.operationLogDao().forMission(mission))
        val scenario = scenario(mission, receipts.firstOrNull()?.custodyTransfer?.missionSnapshot?.toByteArray(), receipts.size)
        require(scenario.senderNodeId == profile.nodeId && scenario.senderIdentityId == profile.identityId)
        lastMissionId = mission
        workflow(scenario).prepare()
    }

    override suspend fun verify(code: String): DeliveryReceiptResult = database.withTransaction {
        val profile = activeProfile(Permission.ACCEPT_CUSTODY)
        val offer = runCatching { DeliveryOfferCodec().decodeCode(code).offer }.getOrNull()
            ?: return@withTransaction DeliveryReceiptResult.Rejected(DeliveryOfferRejection.MALFORMED, emptyList())
        val receipts = orderedCustodyEvents(database.operationLogDao().forMission(offer.missionId))
        if (database.nonceDao().count(sha256(offer.nonce.toByteArray()).joinToString("") { "%02x".format(it) }) > 0)
            return@withTransaction DeliveryReceiptResult.Rejected(DeliveryOfferRejection.REPLAY_REJECTED, reconstructChain().receipts)
        if (custodyNeedsReconciliation(database.operationLogDao().forMission(offer.missionId)))
            return@withTransaction DeliveryReceiptResult.Rejected(DeliveryOfferRejection.WRONG_MISSION, emptyList())
        val scenario = runCatching { scenario(offer.missionId, receipts.firstOrNull()?.custodyTransfer?.missionSnapshot?.toByteArray(), receipts.size) }.getOrNull()
            ?: return@withTransaction DeliveryReceiptResult.Rejected(DeliveryOfferRejection.WRONG_MISSION, emptyList())
        if (scenario.recipientNodeId != profile.nodeId || scenario.recipientIdentityId != profile.identityId)
            return@withTransaction DeliveryReceiptResult.Rejected(DeliveryOfferRejection.WRONG_RECIPIENT, emptyList())
        lastMissionId = scenario.missionId
        workflow(scenario).verify(code)
    }

    suspend fun importReceipt(event: DomainEvent): Boolean = database.withTransaction {
        database.operationLogDao().find(event.eventId)?.let { require(it.payloadBytes.contentEquals(event.toByteArray())); return@withTransaction true }
        require(!event.custodyTransfer.missionSnapshot.isEmpty) { "Operational receipt requires an immutable mission version" }
        val receipts = orderedCustodyEvents(database.operationLogDao().forMission(event.custodyTransfer.missionId))
        val head = receipts.lastOrNull()?.let { sha256(it.custodyTransfer.toByteArray()) } ?: custodyGenesis
        if (!event.custodyTransfer.previousReceiptSha256.toByteArray().contentEquals(head)) throw MissingEventDependency("Custody predecessor unavailable or competing")
        receipts.firstOrNull()?.let { require(it.custodyTransfer.missionSnapshot == event.custodyTransfer.missionSnapshot) { "Custody version changed mid-chain" } }
        workflow(scenario(event.custodyTransfer.missionId, event.custodyTransfer.missionSnapshot.toByteArray(), receipts.size)).importReceipt(event)
    }

    override suspend fun reconstructChain(): CustodyChain {
        val mission = lastMissionId ?: selectedMissionId() ?: return CustodyChain(emptyList(), true)
        val receipt = orderedCustodyEvents(database.operationLogDao().forMission(mission)).firstOrNull()?.custodyTransfer
        return workflow(scenario(mission, receipt?.missionSnapshot?.toByteArray()?.takeIf { it.isNotEmpty() })).reconstructChain()
    }
    override fun tamperForDemo(code: String) = DeliveryOfferCodec().tamperRecipientForDemo(code, "tampered-recipient")

    private fun workflow(scenario: DeliveryScenario) = RoomProofOfDeliveryWorkflow(database, keys, scenario,
        senderCredentialLookup = recipients::installedIdentity, receiptSink = receiptSink, receiptCredentialLookup = recipients::signingIdentity)

    private suspend fun activeProfile(permission: Permission): LocalDeviceProfile {
        val profile = requireNotNull(profiles) { "A local profile is required for custody actions" }.profile.first()
        val credential = requireNotNull(recipients.installedIdentity(profile.nodeId)) { "Local credential missing" }
        val public = keys.createOrGet(profile.nodeId)
        require(credential.identityId == profile.identityId && credential.signingKeyId == public.signingKeyId && credential.signingPublicKeyDer.contentEquals(public.signingPublicKeyDer))
        require(credential.issuedAtUnixMs <= System.currentTimeMillis())
        require(AuthorizationPolicy().authorize(OfflineCredential(credential.identityId, credential.role.toAuthorizationRole(), credential.expiresAtUnixMs, credential.revokedAtUnixMs != null), permission, System.currentTimeMillis()).allowed)
        return profile
    }

    private suspend fun scenario(missionId: String, pinnedSnapshot: ByteArray? = null, leg: Int = 0): DeliveryScenario {
        val types = setOf("RELIEF_REQUEST_CREATED", "MISSION_FIELD_UPDATED", "CONFLICT_RESOLVED")
        val snapshot = if (pinnedSnapshot == null) MissionCustodySnapshot.newBuilder().addAllEventIds(
            database.operationLogDao().forMission(missionId).filter { it.eventType in types }.map { it.eventId }.sorted()).build()
            else MissionCustodySnapshot.parseFrom(pinnedSnapshot)
        require(snapshot.eventIdsCount in 1..128 && snapshot.eventIdsList == snapshot.eventIdsList.distinct().sorted()) { "Invalid or oversized custody version" }
        val events = snapshot.eventIdsList.map { id ->
            val operation = database.operationLogDao().find(id) ?: throw MissingEventDependency("Custody mission revision has not arrived")
            require(operation.missionId == missionId && operation.eventType in types)
            DomainEvent.parseFrom(operation.payloadBytes)
        }
        val creation = events.singleOrNull { it.hasReliefRequestCreated() } ?: throw MissingEventDependency("Mission creation has not arrived")
        val request = creation.reliefRequestCreated
        val normalized = events.map { event ->
            if (event.hasConflictResolved() && event.conflictResolved.fieldCode.isBlank()) event.toBuilder().setConflictResolved(event.conflictResolved.toBuilder()
                .setFieldCode(database.conflictDao().find(event.conflictResolved.conflictId)?.fieldCode ?: throw MissingEventDependency("Resolution field unavailable"))).build() else event
        }
        val projection = projectMissionVersion(normalized)
        val destination = projection.getValue(MissionField.DESTINATION)
        val path = projection[MissionField.CUSTODY_PATH]?.split(">") ?: listOf(request.originNodeId, destination)
        require(path.first() == request.originNodeId && path.last() == destination && leg < path.lastIndex) { "Custody path complete or destination changed; review assignment" }
        val sender = requireNotNull(recipients.installedIdentity(path[leg]))
        val recipient = requireNotNull(recipients.installedIdentity(path[leg + 1]))
        // Signed immutable event IDs pin the exact cargo/destination revision even
        // when a receipt overtakes its prerequisites or later edits in the mesh.
        val commitment = Base64.getEncoder().encodeToString(snapshot.toByteArray())
        return DeliveryScenario(missionId, "delivery-$missionId", sender.nodeId, recipient.nodeId,
            sender.identityId, recipient.identityId, commitment, creation.scenarioSeed, creation.simulated, snapshot.toByteArray())
    }
}
