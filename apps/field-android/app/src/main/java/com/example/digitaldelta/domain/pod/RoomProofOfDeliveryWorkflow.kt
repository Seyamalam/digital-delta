package com.example.digitaldelta.domain.pod

import androidx.room.withTransaction
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.data.local.OperationEntity
import com.example.digitaldelta.data.local.UsedNonceEntity
import com.example.digitaldelta.domain.identity.AndroidDeviceIdentityKeyStore
import com.example.digitaldelta.domain.identity.InstalledIdentityCredential
import com.example.digitaldelta.domain.identity.toAuthorizationRole
import com.example.digitaldelta.proto.v1.CustodyTransfer
import com.example.digitaldelta.proto.v1.DeliveryOffer
import com.example.digitaldelta.proto.v1.DomainEvent
import com.example.digitaldelta.proto.v1.Signature as ProtoSignature
import com.google.protobuf.ByteString
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeliveryOfferReady(
    val qrCode: String,
    val deliveryId: String,
    val senderIdentityId: String,
    val recipientIdentityId: String,
    val senderSigningKeyId: String,
    val payloadSha256: ByteArray,
    val nonce: ByteArray,
    val timestampUnixMs: Long,
    val previousReceiptSha256: ByteArray,
    val simulatedVehicle: Boolean,
)

data class CustodyReceiptRecord(
    val eventId: String,
    val deliveryId: String,
    val senderIdentityId: String,
    val recipientIdentityId: String,
    val previousReceiptSha256: ByteArray,
    val receiptHash: ByteArray,
    val recordedAtUnixMs: Long,
)

data class CustodyChain(
    val receipts: List<CustodyReceiptRecord>,
    val valid: Boolean,
)

sealed interface DeliveryReceiptResult {
    data class Verified(
        val receipt: CustodyReceiptRecord,
        val chain: List<CustodyReceiptRecord>,
    ) : DeliveryReceiptResult

    data class Rejected(
        val reason: DeliveryOfferRejection,
        val preservedChain: List<CustodyReceiptRecord>,
    ) : DeliveryReceiptResult
}

interface ProofOfDeliveryWorkflow {
    suspend fun prepare(): DeliveryOfferReady
    suspend fun verify(code: String): DeliveryReceiptResult
    suspend fun reconstructChain(): CustodyChain
    fun tamperForDemo(code: String): String
}

data class DeliveryScenario(
    val missionId: String,
    val deliveryId: String,
    val senderNodeId: String,
    val recipientNodeId: String,
    val senderIdentityId: String,
    val recipientIdentityId: String,
    val payloadDescription: String,
    val scenarioSeed: String,
    val simulatedVehicle: Boolean,
    val missionSnapshot: ByteArray = byteArrayOf(),
)

class RoomProofOfDeliveryWorkflow(
    private val database: DeltaDatabase,
    private val deviceKeys: AndroidDeviceIdentityKeyStore,
    private val scenario: DeliveryScenario = DEFAULT_SCENARIO,
    private val codec: DeliveryOfferCodec = DeliveryOfferCodec(),
    private val nowUnixMs: () -> Long = System::currentTimeMillis,
    private val nonceBytes: () -> ByteArray = {
        ByteArray(16).also(SecureRandom()::nextBytes)
    },
    private val eventId: () -> String = { "custody-${UUID.randomUUID()}" },
    private val senderCredentialLookup: suspend (String) -> InstalledIdentityCredential? = { null },
    private val receiptSink: suspend (DomainEvent) -> Unit = {},
) : ProofOfDeliveryWorkflow {
    private val payloadHash = sha256(scenario.payloadDescription.encodeToByteArray())

    override suspend fun prepare(): DeliveryOfferReady = withContext(Dispatchers.IO) {
        prepareInternal()
    }

    private suspend fun prepareInternal(): DeliveryOfferReady {
        val sender = AndroidKeystoreDeliverySigner(scenario.senderNodeId, deviceKeys)
        val previous = reconstructChainInternal().receipts.lastOrNull()?.receiptHash ?: GENESIS_HASH
        val nonce = nonceBytes()
        val now = nowUnixMs()
        val code = codec.createCode(
            DeliveryOfferDraft(
                deliveryId = scenario.deliveryId,
                missionId = scenario.missionId,
                senderIdentityId = scenario.senderIdentityId,
                recipientIdentityId = scenario.recipientIdentityId,
                payloadSha256 = payloadHash,
                nonce = nonce,
                timestampUnixMs = now,
                previousReceiptSha256 = previous,
                simulatedVehicle = scenario.simulatedVehicle,
                missionSnapshot = scenario.missionSnapshot,
            ),
            sender,
        )
        return DeliveryOfferReady(
            qrCode = code,
            deliveryId = scenario.deliveryId,
            senderIdentityId = scenario.senderIdentityId,
            recipientIdentityId = scenario.recipientIdentityId,
            senderSigningKeyId = sender.keyId,
            payloadSha256 = payloadHash.copyOf(),
            nonce = nonce.copyOf(),
            timestampUnixMs = now,
            previousReceiptSha256 = previous.copyOf(),
            simulatedVehicle = scenario.simulatedVehicle,
        )
    }

    override suspend fun verify(code: String): DeliveryReceiptResult = withContext(Dispatchers.IO) {
        // Serialize the head read, signature/nonce validation and append, not only the write.
        database.withTransaction { verifyInternal(code) }
    }

    private suspend fun verifyInternal(code: String): DeliveryReceiptResult {
        val chainBefore = reconstructChainInternal().receipts
        val installedSender = senderCredentialLookup(scenario.senderNodeId)
            ?.takeIf { it.identityId == scenario.senderIdentityId }
        if (installedSender != null && !com.example.digitaldelta.domain.identity.AuthorizationPolicy().isAllowed(installedSender.role.toAuthorizationRole(), com.example.digitaldelta.domain.identity.Permission.OFFER_CUSTODY)) {
            return DeliveryReceiptResult.Rejected(DeliveryOfferRejection.UNKNOWN_SIGNING_KEY, chainBefore)
        }
        val trustedSender = when {
            installedSender != null -> TrustedDeliveryContext(
                deliveryId = scenario.deliveryId,
                missionId = scenario.missionId,
                senderIdentityId = scenario.senderIdentityId,
                recipientIdentityId = scenario.recipientIdentityId,
                payloadSha256 = payloadHash,
                senderSigningKeyId = installedSender.signingKeyId,
                senderSigningPublicKeyDer = installedSender.signingPublicKeyDer,
                nowUnixMs = nowUnixMs(),
                allowedClockSkewMs = ALLOWED_CLOCK_SKEW_MS,
                credentialValidUntilUnixMs = installedSender.expiresAtUnixMs,
                credentialRevokedAtUnixMs = installedSender.revokedAtUnixMs,
                missionSnapshot = scenario.missionSnapshot,
            )
            scenario.simulatedVehicle -> {
                val localDemoIdentity = deviceKeys.createOrGet(scenario.senderNodeId)
                TrustedDeliveryContext(
                    deliveryId = scenario.deliveryId,
                    missionId = scenario.missionId,
                    senderIdentityId = scenario.senderIdentityId,
                    recipientIdentityId = scenario.recipientIdentityId,
                    payloadSha256 = payloadHash,
                    senderSigningKeyId = localDemoIdentity.signingKeyId,
                    senderSigningPublicKeyDer = localDemoIdentity.signingPublicKeyDer,
                    nowUnixMs = nowUnixMs(),
                    allowedClockSkewMs = ALLOWED_CLOCK_SKEW_MS,
                    missionSnapshot = scenario.missionSnapshot,
                )
            }
            else -> null
        }
        val verification = codec.verifyCode(
            code,
            trustedSender,
        )
        if (verification is DeliveryOfferVerification.Rejected) {
            return DeliveryReceiptResult.Rejected(verification.reason, chainBefore)
        }
        val signed = (verification as DeliveryOfferVerification.Verified).signedOffer
        val nonceHash = sha256(signed.offer.nonce.toByteArray()).toHex()
        if (database.nonceDao().count(nonceHash) > 0) {
            return DeliveryReceiptResult.Rejected(DeliveryOfferRejection.REPLAY_REJECTED, chainBefore)
        }
        val expectedPrevious = chainBefore.lastOrNull()?.receiptHash ?: GENESIS_HASH
        if (!MessageDigest.isEqual(signed.offer.previousReceiptSha256.toByteArray(), expectedPrevious)) {
            return DeliveryReceiptResult.Rejected(
                DeliveryOfferRejection.PREVIOUS_RECEIPT_MISMATCH,
                chainBefore,
            )
        }
        val now = nowUnixMs()
        val event = createCustodyEvent(signed.offer, signed.senderSignature, now)
        val transfer = event.custodyTransfer
        val receipt = CustodyReceiptRecord(
            eventId = event.eventId,
            deliveryId = transfer.deliveryId,
            senderIdentityId = transfer.senderIdentityId,
            recipientIdentityId = transfer.recipientIdentityId,
            previousReceiptSha256 = transfer.previousReceiptSha256.toByteArray(),
            receiptHash = sha256(transfer.toByteArray()),
            recordedAtUnixMs = now,
        )
        val inserted = database.withTransaction {
            val nonceClaim = database.nonceDao().claim(
                UsedNonceEntity(
                    nonceSha256 = nonceHash,
                    deliveryId = transfer.deliveryId,
                    usedAtUnixMs = now,
                ),
            )
            if (nonceClaim == -1L) {
                false
            } else {
                database.operationLogDao().append(
                    OperationEntity(
                        eventId = event.eventId,
                        missionId = scenario.missionId,
                        eventType = EVENT_TYPE,
                        payloadBytes = event.toByteArray(),
                        createdAtUnixMs = now,
                    ),
                )
                receiptSink(event)
                true
            }
        }
        if (!inserted) {
            return DeliveryReceiptResult.Rejected(DeliveryOfferRejection.REPLAY_REJECTED, chainBefore)
        }
        val chain = reconstructChainInternal()
        check(chain.valid) { "custody chain became invalid after append" }
        return DeliveryReceiptResult.Verified(receipt, chain.receipts)
    }

    /** Import the dual-signed receipt, not a transport acknowledgement. */
    suspend fun importReceipt(event: DomainEvent): Boolean = database.withTransaction {
        val existing = database.operationLogDao().find(event.eventId)
        if (existing != null) {
            require(existing.payloadBytes.contentEquals(event.toByteArray()))
            return@withTransaction true
        }
        val transfer = event.custodyTransfer
        require(event.hasCustodyTransfer() && event.schemaVersion == 1 && event.eventId.isNotBlank())
        require(transfer.missionId == scenario.missionId && transfer.deliveryId == scenario.deliveryId)
        require(transfer.missionSnapshot.toByteArray().contentEquals(scenario.missionSnapshot))
        require(event.actorIdentityId == scenario.recipientIdentityId && transfer.senderIdentityId == scenario.senderIdentityId && transfer.recipientIdentityId == scenario.recipientIdentityId)
        require(event.simulated == scenario.simulatedVehicle && transfer.simulatedVehicle == scenario.simulatedVehicle && event.scenarioSeed == scenario.scenarioSeed)
        require(MessageDigest.isEqual(transfer.payloadSha256.toByteArray(), payloadHash))
        require(transfer.nonce.size() in 16..64 && transfer.timestampUnixMs > 0)
        require(verifyTransferSignatures(transfer)) { "Invalid custody signatures" }
        for ((node, permission) in listOf(scenario.senderNodeId to com.example.digitaldelta.domain.identity.Permission.OFFER_CUSTODY,
            scenario.recipientNodeId to com.example.digitaldelta.domain.identity.Permission.ACCEPT_CUSTODY)) {
            val credential = senderCredentialLookup(node) ?: throw com.example.digitaldelta.domain.sync.MissingEventDependency("Custody signer credential unavailable")
            val at = transfer.timestampUnixMs
            require(credential.issuedAtUnixMs <= at && com.example.digitaldelta.domain.identity.AuthorizationPolicy().authorize(
                com.example.digitaldelta.domain.identity.OfflineCredential(credential.identityId, credential.role.toAuthorizationRole(), credential.expiresAtUnixMs,
                    credential.revokedAtUnixMs?.let { it <= at } == true), permission, at).allowed) { "Custody signer was not authorized at handoff" }
        }
        val chain = reconstructChainInternal()
        require(chain.valid)
        val previous = chain.receipts.lastOrNull()?.receiptHash ?: GENESIS_HASH
        if (!MessageDigest.isEqual(transfer.previousReceiptSha256.toByteArray(), previous))
            throw com.example.digitaldelta.domain.sync.MissingEventDependency("Custody head is unavailable or competing")
        require(database.nonceDao().claim(UsedNonceEntity(sha256(transfer.nonce.toByteArray()).toHex(), transfer.deliveryId, event.occurredAtUnixMs)) != -1L)
        database.operationLogDao().append(OperationEntity(event.eventId, scenario.missionId, EVENT_TYPE, event.toByteArray(), event.occurredAtUnixMs))
        true
    }

    override suspend fun reconstructChain(): CustodyChain = withContext(Dispatchers.IO) {
        reconstructChainInternal()
    }

    private suspend fun reconstructChainInternal(): CustodyChain {
        val operations = database.operationLogDao().forMission(scenario.missionId)
            .filter { it.eventType == EVENT_TYPE }
        val receipts = mutableListOf<CustodyReceiptRecord>()
        var expectedPrevious = GENESIS_HASH
        var valid = true
        for (operation in operations) {
            val event = runCatching { DomainEvent.parseFrom(operation.payloadBytes) }.getOrNull()
            if (event == null || !event.hasCustodyTransfer()) {
                valid = false
                continue
            }
            val transfer = event.custodyTransfer
            if (!MessageDigest.isEqual(transfer.previousReceiptSha256.toByteArray(), expectedPrevious)) {
                valid = false
            }
            if (!verifyTransferSignatures(transfer)) valid = false
            val receiptHash = sha256(transfer.toByteArray())
            receipts += CustodyReceiptRecord(
                eventId = event.eventId,
                deliveryId = transfer.deliveryId,
                senderIdentityId = transfer.senderIdentityId,
                recipientIdentityId = transfer.recipientIdentityId,
                previousReceiptSha256 = transfer.previousReceiptSha256.toByteArray(),
                receiptHash = receiptHash,
                recordedAtUnixMs = event.occurredAtUnixMs,
            )
            expectedPrevious = receiptHash
        }
        return CustodyChain(receipts, valid)
    }

    override fun tamperForDemo(code: String): String =
        codec.tamperRecipientForDemo(code, "tampered-recipient")

    private fun createCustodyEvent(
        offer: DeliveryOffer,
        senderSignature: ProtoSignature,
        now: Long,
    ): DomainEvent {
        val unsignedReceipt = CustodyTransfer.newBuilder()
            .setMissionId(scenario.missionId)
            .setMissionSnapshot(offer.missionSnapshot)
            .setDeliveryId(offer.deliveryId)
            .setSenderIdentityId(offer.senderIdentityId)
            .setRecipientIdentityId(offer.recipientIdentityId)
            .setPayloadSha256(offer.payloadSha256)
            .setNonce(offer.nonce)
            .setTimestampUnixMs(offer.timestampUnixMs)
            .setPreviousReceiptSha256(offer.previousReceiptSha256)
            .setSenderSignature(senderSignature)
            .setSimulatedVehicle(offer.simulatedVehicle)
            .build()
        val recipient = deviceKeys.createOrGet(scenario.recipientNodeId)
        val recipientSignature = ProtoSignature.newBuilder()
            .setKeyId(recipient.signingKeyId)
            .setAlgorithm(DeliveryOfferCodec.SIGNATURE_ALGORITHM)
            .setRsa2048PssSha256(
                ByteString.copyFrom(deviceKeys.sign(scenario.recipientNodeId, unsignedReceipt.toByteArray())),
            )
            .build()
        val transfer = unsignedReceipt.toBuilder().setRecipientSignature(recipientSignature).build()
        return DomainEvent.newBuilder()
            .setEventId(eventId())
            .setSchemaVersion(1)
            .setActorIdentityId(scenario.recipientIdentityId)
            .setOccurredAtUnixMs(now)
            .setSimulated(scenario.simulatedVehicle)
            .setScenarioSeed(scenario.scenarioSeed)
            .setCustodyTransfer(transfer)
            .build()
    }

    private suspend fun verifyTransferSignatures(transfer: CustodyTransfer): Boolean {
        val offer = DeliveryOffer.newBuilder()
            .setDeliveryId(transfer.deliveryId)
            .setMissionId(scenario.missionId)
            .setSenderIdentityId(transfer.senderIdentityId)
            .setRecipientIdentityId(transfer.recipientIdentityId)
            .setPayloadSha256(transfer.payloadSha256)
            .setNonce(transfer.nonce)
            .setTimestampUnixMs(transfer.timestampUnixMs)
            .setPreviousReceiptSha256(transfer.previousReceiptSha256)
            .setSimulatedVehicle(transfer.simulatedVehicle)
            .setMissionSnapshot(transfer.missionSnapshot)
            .build()
        val installedSender = senderCredentialLookup(scenario.senderNodeId)
            ?.takeIf { it.identityId == scenario.senderIdentityId }
        val sender = if (installedSender == null && scenario.simulatedVehicle) {
            deviceKeys.createOrGet(scenario.senderNodeId)
        } else {
            null
        }
        val senderSigningPublicKeyDer = installedSender?.signingPublicKeyDer ?: sender?.signingPublicKeyDer
            ?: return false
        val senderSigningKeyId = installedSender?.signingKeyId ?: sender?.signingKeyId ?: return false
        val senderValid = verifySignature(
            senderSigningPublicKeyDer,
            senderSigningKeyId,
            offer.toByteArray(),
            transfer.senderSignature,
        )
        val unsignedReceipt = transfer.toBuilder().clearRecipientSignature().build()
        val installedRecipient = senderCredentialLookup(scenario.recipientNodeId)
            ?.takeIf { it.identityId == scenario.recipientIdentityId }
        val demoRecipient = if (installedRecipient == null && scenario.simulatedVehicle) deviceKeys.createOrGet(scenario.recipientNodeId) else null
        val recipientValid = verifySignature(
            installedRecipient?.signingPublicKeyDer ?: demoRecipient?.signingPublicKeyDer ?: return false,
            installedRecipient?.signingKeyId ?: demoRecipient?.signingKeyId ?: return false,
            unsignedReceipt.toByteArray(),
            transfer.recipientSignature,
        )
        return senderValid && recipientValid
    }

    private fun verifySignature(
        publicKeyDer: ByteArray,
        expectedKeyId: String,
        payload: ByteArray,
        signature: ProtoSignature,
    ): Boolean = runCatching {
        if (signature.keyId != expectedKeyId ||
            signature.algorithm != DeliveryOfferCodec.SIGNATURE_ALGORITHM
        ) return false
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyDer))
        newRsaPssSignature().run {
            initVerify(publicKey)
            update(payload)
            verify(signature.rsa2048PssSha256.toByteArray())
        }
    }.getOrDefault(false)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        val DEFAULT_SCENARIO = DeliveryScenario(
            missionId = "mission-pod-demo-01",
            deliveryId = "DELTA-2026-0001",
            senderNodeId = "pod-demo-boat-02",
            recipientNodeId = "pod-demo-hospital-01",
            senderIdentityId = "boat-operator-02",
            recipientIdentityId = "hospital-operator-01",
            payloadDescription = "medicine:10|ors:20",
            scenarioSeed = "m5-pod-demo-v1",
            simulatedVehicle = true,
        )
        private const val EVENT_TYPE = "CUSTODY_TRANSFER"
        // Field phones can drift while disconnected; keep the window bounded but demo-operable.
        private const val ALLOWED_CLOCK_SKEW_MS = 10 * 60_000L
        private val GENESIS_HASH = sha256("digital-delta-custody-genesis-v1".encodeToByteArray())
    }
}
