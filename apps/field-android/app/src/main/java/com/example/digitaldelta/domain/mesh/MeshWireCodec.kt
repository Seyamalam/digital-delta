package com.example.digitaldelta.domain.mesh

import com.example.digitaldelta.proto.v1.Envelope
import com.example.digitaldelta.proto.v1.EncryptedPayload
import com.example.digitaldelta.proto.v1.PriorityClass
import com.google.protobuf.ByteString

object MeshWireCodec {
    fun createEnvelope(
        messageId: String,
        senderNodeId: String,
        recipientNodeId: String,
        createdAtUnixMs: Long,
        expiresAtUnixMs: Long,
        hopLimit: Int,
        priority: PriorityClass,
        payloadHash: ByteArray,
        simulated: Boolean,
        scenarioSeed: String,
        protectedPayload: ProtectedPayload? = null,
    ): Envelope {
        require(messageId.isNotBlank()) { "messageId is required" }
        require(senderNodeId.isNotBlank()) { "senderNodeId is required" }
        require(recipientNodeId.isNotBlank()) { "recipientNodeId is required" }
        require(expiresAtUnixMs > createdAtUnixMs) { "envelope must expire after creation" }
        require(hopLimit > 0) { "hopLimit must be positive" }
        require(payloadHash.size == 32) { "payloadHash must be a SHA-256 digest" }

        val builder = Envelope.newBuilder()
            .setMessageId(messageId)
            .setSchemaVersion(1)
            .setMinimumReaderVersion(1)
            .setSenderNodeId(senderNodeId)
            .setRecipientNodeId(recipientNodeId)
            .setCreatedAtUnixMs(createdAtUnixMs)
            .setExpiresAtUnixMs(expiresAtUnixMs)
            .setHopLimit(hopLimit)
            .setPriority(priority)
            .setPayloadSha256(ByteString.copyFrom(payloadHash))
            .setSimulated(simulated)
            .setScenarioSeed(scenarioSeed)
        protectedPayload?.let { protected ->
            builder.encryptedPayload = EncryptedPayload.newBuilder()
                .setRecipientKeyId(protected.recipientKeyId)
                .setAes256GcmCiphertext(ByteString.copyFrom(protected.ciphertext))
                .setNonce(ByteString.copyFrom(protected.nonce))
                .setAssociatedDataSha256(ByteString.copyFrom(protected.associatedDataSha256))
                .build()
        }
        return builder.build()
    }

    fun encode(envelope: Envelope): ByteArray = envelope.toByteArray()

    fun decode(bytes: ByteArray): Envelope = Envelope.parseFrom(bytes)
}
