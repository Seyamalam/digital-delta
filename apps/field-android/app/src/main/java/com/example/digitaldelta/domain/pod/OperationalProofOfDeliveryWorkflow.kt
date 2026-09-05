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
        val scenario = scenario(mission)
        require(scenario.senderNodeId == profile.nodeId && scenario.senderIdentityId == profile.identityId)
        lastMissionId = mission
        require(workflow(scenario).reconstructChain().receipts.isEmpty()) { "This delivery already has an accepted custodian" }
        workflow(scenario).prepare()
    }

    override suspend fun verify(code: String): DeliveryReceiptResult = database.withTransaction {
        val profile = activeProfile(Permission.ACCEPT_CUSTODY)
        val offer = runCatching { DeliveryOfferCodec().decodeCode(code).offer }.getOrNull()
            ?: return@withTransaction DeliveryReceiptResult.Rejected(DeliveryOfferRejection.MALFORMED, emptyList())
        val scenario = runCatching { scenario(offer.missionId) }.getOrNull()
            ?: return@withTransaction DeliveryReceiptResult.Rejected(DeliveryOfferRejection.WRONG_MISSION, emptyList())
        if (scenario.recipientNodeId != profile.nodeId || scenario.recipientIdentityId != profile.identityId)
            return@withTransaction DeliveryReceiptResult.Rejected(DeliveryOfferRejection.WRONG_RECIPIENT, emptyList())
        lastMissionId = scenario.missionId
        workflow(scenario).verify(code)
    }

    suspend fun importReceipt(event: DomainEvent): Boolean = database.withTransaction {
        require(!event.custodyTransfer.missionSnapshot.isEmpty) { "Operational receipt requires an immutable mission version" }
        workflow(scenario(event.custodyTransfer.missionId, event.custodyTransfer.missionSnapshot.toByteArray())).importReceipt(event)
    }

    override suspend fun reconstructChain(): CustodyChain {
        val mission = lastMissionId ?: selectedMissionId() ?: return CustodyChain(emptyList(), true)
        val receipt = database.operationLogDao().forMission(mission).firstOrNull { it.eventType == "CUSTODY_TRANSFER" }
            ?.let { DomainEvent.parseFrom(it.payloadBytes).custodyTransfer }
        return workflow(scenario(mission, receipt?.missionSnapshot?.toByteArray()?.takeIf { it.isNotEmpty() })).reconstructChain()
    }
    override fun tamperForDemo(code: String) = DeliveryOfferCodec().tamperRecipientForDemo(code, "tampered-recipient")

    private fun workflow(scenario: DeliveryScenario) = RoomProofOfDeliveryWorkflow(database, keys, scenario,
        senderCredentialLookup = recipients::installedIdentity, receiptSink = receiptSink)

    private suspend fun activeProfile(permission: Permission): LocalDeviceProfile {
        val profile = requireNotNull(profiles) { "A local profile is required for custody actions" }.profile.first()
        val credential = requireNotNull(recipients.installedIdentity(profile.nodeId)) { "Local credential missing" }
        val public = keys.createOrGet(profile.nodeId)
        require(credential.identityId == profile.identityId && credential.signingKeyId == public.signingKeyId && credential.signingPublicKeyDer.contentEquals(public.signingPublicKeyDer))
        require(credential.issuedAtUnixMs <= System.currentTimeMillis())
        require(AuthorizationPolicy().authorize(OfflineCredential(credential.identityId, credential.role.toAuthorizationRole(), credential.expiresAtUnixMs, credential.revokedAtUnixMs != null), permission, System.currentTimeMillis()).allowed)
        return profile
    }

    private suspend fun scenario(missionId: String, pinnedSnapshot: ByteArray? = null): DeliveryScenario {
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
        val initial = mapOf(MissionField.DESTINATION to request.destinationNodeId,
            MissionField.PRIORITY to request.cargoList.minOf { it.priorityValue }.toString(),
            MissionField.MEDICAL_QUANTITY to request.cargoList.filter { it.itemCode in setOf("medicine", "ors", "blood") }.sumOf { it.quantity.toLong() }.toString())
        val revisions = initial.map { (field, value) -> FieldRevision(creation.eventId, missionId, field, value, VectorClock(mapOf(request.requesterNodeId to 1)), creation.occurredAtUnixMs) }.toMutableList()
        for (event in events) {
            if (event.hasMissionFieldUpdated()) {
                val update = event.missionFieldUpdated
                revisions += FieldRevision(event.eventId, missionId, MissionField.valueOf(update.fieldCode), update.value.toStringUtf8(), update.vectorClock.toDomainClock(), event.occurredAtUnixMs)
            } else if (event.hasConflictResolved()) {
                val resolution = event.conflictResolved
                val field = resolution.fieldCode.ifBlank { database.conflictDao().find(resolution.conflictId)?.fieldCode ?: throw MissingEventDependency("Resolution field unavailable") }
                revisions += FieldRevision(event.eventId, missionId, MissionField.valueOf(field), resolution.selectedValue.toStringUtf8(), resolution.vectorClock.toDomainClock(), event.occurredAtUnixMs)
            }
        }
        val projection = revisions.groupBy { it.field }.mapValues { projectRevisions(it.value) }
        require(projection.values.none { value -> value.conflicts.any { it.active } }) { "Resolve mission conflicts before custody" }
        val destination = projection.getValue(MissionField.DESTINATION).revision.value
        val sender = requireNotNull(recipients.installedIdentity(request.originNodeId))
        val recipient = requireNotNull(recipients.installedIdentity(destination))
        // Signed immutable event IDs pin the exact cargo/destination revision even
        // when a receipt overtakes its prerequisites or later edits in the mesh.
        val commitment = Base64.getEncoder().encodeToString(snapshot.toByteArray())
        return DeliveryScenario(missionId, "delivery-$missionId", sender.nodeId, recipient.nodeId,
            sender.identityId, recipient.identityId, commitment, creation.scenarioSeed, creation.simulated, snapshot.toByteArray())
    }
}
