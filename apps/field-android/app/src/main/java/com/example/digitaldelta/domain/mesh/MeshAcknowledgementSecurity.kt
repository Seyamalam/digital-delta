package com.example.digitaldelta.domain.mesh

import com.example.digitaldelta.data.local.RecipientKeyDao
import com.example.digitaldelta.domain.identity.AndroidDeviceIdentityKeyStore
import com.example.digitaldelta.proto.v1.Acknowledgement
import com.example.digitaldelta.proto.v1.AcknowledgementStatus
import com.example.digitaldelta.proto.v1.Signature as ProtoSignature
import com.google.protobuf.ByteString
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.security.spec.X509EncodedKeySpec

data class PeerSigningIdentity(
    val nodeId: String,
    val keyId: String,
    val publicKeyDer: ByteArray,
    val validFromUnixMs: Long,
    val validUntilUnixMs: Long,
    val revokedAtUnixMs: Long?,
)

fun interface PeerSigningIdentityDirectory {
    suspend fun findByNodeId(nodeId: String): PeerSigningIdentity?
}

class RoomPeerSigningIdentityDirectory(
    private val dao: RecipientKeyDao,
) : PeerSigningIdentityDirectory {
    override suspend fun findByNodeId(nodeId: String): PeerSigningIdentity? =
        dao.findByNodeId(nodeId)?.let { entity ->
            PeerSigningIdentity(
                nodeId = entity.nodeId,
                keyId = entity.signingKeyId,
                publicKeyDer = entity.signingPublicKeyDer.copyOf(),
                validFromUnixMs = entity.issuedAtUnixMs,
                validUntilUnixMs = entity.expiresAtUnixMs,
                revokedAtUnixMs = entity.revokedAtUnixMs,
            )
        }
}

fun interface MeshAcknowledgementSigner {
    fun sign(acknowledgement: Acknowledgement): Acknowledgement
}

fun interface MeshAcknowledgementVerifier {
    suspend fun verify(
        acknowledgement: Acknowledgement,
        expectedNodeId: String,
        nowUnixMs: Long,
    ): Boolean
}

class AndroidMeshAcknowledgementSigner(
    private val nodeId: String,
    private val deviceKeys: AndroidDeviceIdentityKeyStore,
) : MeshAcknowledgementSigner {
    override fun sign(acknowledgement: Acknowledgement): Acknowledgement {
        require(acknowledgement.nodeId == nodeId) { "cannot sign an acknowledgement for another node" }
        val identity = deviceKeys.createOrGet(nodeId)
        return MeshAcknowledgementSecurity.sign(
            acknowledgement = acknowledgement,
            keyId = identity.signingKeyId,
            signBytes = { payload -> deviceKeys.sign(nodeId, payload) },
        )
    }
}

class DirectoryMeshAcknowledgementVerifier(
    private val directory: PeerSigningIdentityDirectory,
) : MeshAcknowledgementVerifier {
    override suspend fun verify(
        acknowledgement: Acknowledgement,
        expectedNodeId: String,
        nowUnixMs: Long,
    ): Boolean {
        if (expectedNodeId.isBlank() || acknowledgement.nodeId != expectedNodeId) return false
        if (acknowledgement.messageId.isBlank() ||
            acknowledgement.status == AcknowledgementStatus.ACKNOWLEDGEMENT_STATUS_UNSPECIFIED
        ) return false
        if (acknowledgement.recordedAtUnixMs !in
            (nowUnixMs - MeshAcknowledgementSecurity.MAX_CLOCK_SKEW_MILLIS)..
                (nowUnixMs + MeshAcknowledgementSecurity.MAX_CLOCK_SKEW_MILLIS)
        ) return false
        val identity = directory.findByNodeId(expectedNodeId) ?: return false
        if (identity.nodeId != expectedNodeId ||
            identity.validFromUnixMs > nowUnixMs ||
            identity.validUntilUnixMs <= nowUnixMs ||
            identity.revokedAtUnixMs?.let { it <= nowUnixMs } == true
        ) return false
        val signature = acknowledgement.nodeSignature
        if (signature.algorithm != MeshAcknowledgementSecurity.SIGNATURE_ALGORITHM ||
            signature.keyId != identity.keyId ||
            signature.rsa2048PssSha256.isEmpty
        ) return false
        return runCatching {
            val publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(identity.publicKeyDer))
            if (publicKey !is RSAKey || publicKey.modulus.bitLength() < 2048) return false
            MeshAcknowledgementSecurity.rsaPss().run {
                initVerify(publicKey)
                update(MeshAcknowledgementSecurity.canonicalPayload(acknowledgement))
                verify(signature.rsa2048PssSha256.toByteArray())
            }
        }.getOrDefault(false)
    }
}

object MeshAcknowledgementSecurity {
    const val SIGNATURE_ALGORITHM = "RSA-2048-PSS-SHA256"
    const val MAX_CLOCK_SKEW_MILLIS = 10 * 60 * 1_000L

    fun sign(
        acknowledgement: Acknowledgement,
        keyId: String,
        signBytes: (ByteArray) -> ByteArray,
    ): Acknowledgement {
        require(keyId.isNotBlank()) { "signing key id is required" }
        val signatureBytes = signBytes(canonicalPayload(acknowledgement))
        require(signatureBytes.isNotEmpty()) { "acknowledgement signature is required" }
        return acknowledgement.toBuilder()
            .setNodeSignature(
                ProtoSignature.newBuilder()
                    .setKeyId(keyId)
                    .setAlgorithm(SIGNATURE_ALGORITHM)
                    .setRsa2048PssSha256(ByteString.copyFrom(signatureBytes))
                    .build(),
            )
            .build()
    }

    fun canonicalPayload(acknowledgement: Acknowledgement): ByteArray = acknowledgement.toBuilder()
        .clearNodeSignature()
        .build()
        .toByteArray()

    internal fun rsaPss(): Signature = runCatching {
        Signature.getInstance("RSASSA-PSS").apply {
            setParameter(PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1))
        }
    }.getOrElse {
        Signature.getInstance("SHA256withRSA/PSS")
    }
}
