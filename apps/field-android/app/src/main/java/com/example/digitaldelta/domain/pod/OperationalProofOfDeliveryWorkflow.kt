package com.example.digitaldelta.domain.pod

import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.domain.identity.*
import com.example.digitaldelta.proto.v1.DomainEvent
import kotlinx.coroutines.flow.first
import java.util.Base64

/** Field handoff uses provisioned mission participants. No demo-key fallback. */
class OperationalProofOfDeliveryWorkflow(
    private val database: DeltaDatabase,
    private val keys: AndroidDeviceIdentityKeyStore,
    private val recipients: RecipientProvisioningRepository,
    private val profiles: DeviceProfileRepository,
    private val selectedMissionId: () -> String? = { null },
) : ProofOfDeliveryWorkflow {
    private var lastMissionId: String? = null

    override suspend fun prepare(): DeliveryOfferReady {
        val profile = activeProfile(Permission.OFFER_CUSTODY)
        val mission = selectedMissionId() ?: database.operationLogDao().requests().firstOrNull {
            DomainEvent.parseFrom(it.payloadBytes).reliefRequestCreated.requesterNodeId == profile.nodeId
        }?.missionId ?: error("Select an accepted mission whose custodian is this phone")
        val scenario = scenario(mission)
        require(scenario.senderNodeId == profile.nodeId && scenario.senderIdentityId == profile.identityId)
        lastMissionId = mission
        return workflow(scenario).prepare()
    }

    override suspend fun verify(code: String): DeliveryReceiptResult {
        val profile = activeProfile(Permission.ACCEPT_CUSTODY)
        val offer = runCatching { DeliveryOfferCodec().decodeCode(code).offer }.getOrNull()
            ?: return DeliveryReceiptResult.Rejected(DeliveryOfferRejection.MALFORMED, emptyList())
        val scenario = runCatching { scenario(offer.missionId) }.getOrNull()
            ?: return DeliveryReceiptResult.Rejected(DeliveryOfferRejection.WRONG_MISSION, emptyList())
        if (scenario.recipientNodeId != profile.nodeId || scenario.recipientIdentityId != profile.identityId)
            return DeliveryReceiptResult.Rejected(DeliveryOfferRejection.WRONG_RECIPIENT, emptyList())
        lastMissionId = scenario.missionId
        return workflow(scenario).verify(code)
    }

    override suspend fun reconstructChain(): CustodyChain {
        val mission = lastMissionId ?: selectedMissionId() ?: return CustodyChain(emptyList(), true)
        return workflow(scenario(mission)).reconstructChain()
    }
    override fun tamperForDemo(code: String) = DeliveryOfferCodec().tamperRecipientForDemo(code, "tampered-recipient")

    private fun workflow(scenario: DeliveryScenario) = RoomProofOfDeliveryWorkflow(database, keys, scenario,
        senderCredentialLookup = recipients::installedIdentity)

    private suspend fun activeProfile(permission: Permission): LocalDeviceProfile {
        val profile = profiles.profile.first()
        val credential = requireNotNull(recipients.installedIdentity(profile.nodeId)) { "Local credential missing" }
        val public = keys.createOrGet(profile.nodeId)
        require(credential.identityId == profile.identityId && credential.signingKeyId == public.signingKeyId && credential.signingPublicKeyDer.contentEquals(public.signingPublicKeyDer))
        require(credential.issuedAtUnixMs <= System.currentTimeMillis())
        require(AuthorizationPolicy().authorize(OfflineCredential(credential.identityId, credential.role.toAuthorizationRole(), credential.expiresAtUnixMs, credential.revokedAtUnixMs != null), permission, System.currentTimeMillis()).allowed)
        return profile
    }

    private suspend fun scenario(missionId: String): DeliveryScenario {
        val creation = database.operationLogDao().forMission(missionId).firstOrNull { it.eventType == "RELIEF_REQUEST_CREATED" }
            ?.let { DomainEvent.parseFrom(it.payloadBytes) } ?: error("Mission has not been accepted here")
        val request = creation.reliefRequestCreated
        val projection = database.missionProjectionDao().forMission(missionId)
        require(projection.isNotEmpty()) { "Mission projection missing" }
        require(!database.conflictDao().hasOpen(missionId)) { "Resolve mission conflict before custody" }
        val destination = projection.first { it.fieldCode == "DESTINATION" }.value
        val sender = requireNotNull(recipients.installedIdentity(request.requesterNodeId))
        val recipient = requireNotNull(recipients.installedIdentity(destination))
        // Both independent replicas derive exactly the same payload commitment.
        val commitment = Base64.getEncoder().encodeToString(request.toByteArray()) + "|" + projection.joinToString("|") { "${it.fieldCode}:${it.value}" }
        return DeliveryScenario(missionId, "delivery-$missionId", sender.nodeId, recipient.nodeId,
            sender.identityId, recipient.identityId, commitment, creation.scenarioSeed, creation.simulated)
    }
}
