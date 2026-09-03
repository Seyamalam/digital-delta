package com.example.digitaldelta.domain.pod

import androidx.room.withTransaction
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.data.local.OperationEntity
import com.example.digitaldelta.data.local.UsedNonceEntity
import com.example.digitaldelta.domain.identity.AndroidDeviceIdentityKeyStore
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
        verifyInternal(code)
    }

    private suspend fun verifyInternal(code: String): DeliveryReceiptResult {
        val chainBefore = reconstructChainInternal().receipts
        val senderIdentity = deviceKeys.createOrGet(scenario.senderNodeId)
        val verification = codec.verifyCode(
            code,
            TrustedDeliveryContext(
                deliveryId = scenario.deliveryId,
                missionId = scenario.missionId,
                senderIdentityId = scenario.senderIdentityId,
                recipientIdentityId = scenario.recipientIdentityId,
                payloadSha256 = payloadHash,
                senderSigningKeyId = senderIdentity.signingKeyId,
                senderSigningPublicKeyDer = senderIdentity.signingPublicKeyDer,
                nowUnixMs = nowUnixMs(),
                allowedClockSkewMs = ALLOWED_CLOCK_SKEW_MS,
            ),
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

    private fun verifyTransferSignatures(transfer: CustodyTransfer): Boolean {
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
            .build()
        val sender = deviceKeys.createOrGet(scenario.senderNodeId)
        val senderValid = verifySignature(
            sender.signingPublicKeyDer,
            sender.signingKeyId,
            offer.toByteArray(),
            transfer.senderSignature,
        )
        val unsignedReceipt = transfer.toBuilder().clearRecipientSignature().build()
        val recipient = deviceKeys.createOrGet(scenario.recipientNodeId)
        val recipientValid = verifySignature(
            recipient.signingPublicKeyDer,
            recipient.signingKeyId,
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
