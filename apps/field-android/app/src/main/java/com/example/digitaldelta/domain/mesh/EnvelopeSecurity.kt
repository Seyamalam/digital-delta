package com.example.digitaldelta.domain.mesh

import com.example.digitaldelta.data.local.RecipientKeyDao
import com.example.digitaldelta.domain.identity.AndroidDeviceIdentityKeyStore
import com.example.digitaldelta.domain.identity.ProvisioningCredentialService
import com.example.digitaldelta.domain.identity.TrustAnchorRepository
import com.example.digitaldelta.proto.v1.Envelope
import com.example.digitaldelta.proto.v1.IdentityProvisioningCredential
import com.example.digitaldelta.proto.v1.Signature
import com.google.protobuf.ByteString
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import kotlinx.coroutines.flow.first

fun interface EnvelopeSigner { suspend fun sign(envelope: Envelope): Envelope }
fun interface EnvelopeVerifier { suspend fun verify(envelope: Envelope, nowUnixMs: Long): Boolean }

/** Immutable origin metadata and ciphertext are signed. Only the relay hop counter may change. */
object EnvelopeSecurity {
    fun canonical(envelope: Envelope): ByteArray = envelope.toBuilder()
        .clearSenderSignature().clearHopCount().build().toByteArray()

    fun verifySignature(envelope: Envelope, publicKeyDer: ByteArray): Boolean = runCatching {
        require(envelope.senderSignature.algorithm == MeshAcknowledgementSecurity.SIGNATURE_ALGORITHM)
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyDer))
        MeshAcknowledgementSecurity.rsaPss().run {
            initVerify(key)
            update(canonical(envelope))
            verify(envelope.senderSignature.rsa2048PssSha256.toByteArray())
        }
    }.getOrDefault(false)
}

class AndroidEnvelopeSecurity(
    private val keys: AndroidDeviceIdentityKeyStore,
    private val directory: RecipientKeyDao,
    private val trust: TrustAnchorRepository,
    private val nowUnixMs: () -> Long = System::currentTimeMillis,
) : EnvelopeSigner, EnvelopeVerifier {
    override suspend fun sign(envelope: Envelope): Envelope {
        val installed = requireNotNull(directory.findByNodeId(envelope.senderNodeId)) { "Origin credential missing" }
        val now = nowUnixMs()
        require(installed.revokedAtUnixMs == null && installed.issuedAtUnixMs <= now && now < installed.expiresAtUnixMs) { "Origin authority inactive" }
        val identity = keys.createOrGet(envelope.senderNodeId)
        require(installed.signingKeyId == identity.signingKeyId && installed.signingPublicKeyDer.contentEquals(identity.signingPublicKeyDer)) { "Origin key mismatch" }
        val unsigned = envelope.toBuilder()
            .setSenderCredential(IdentityProvisioningCredential.parseFrom(installed.credentialBytes)).build()
        return unsigned.toBuilder().setSenderSignature(Signature.newBuilder()
            .setKeyId(identity.signingKeyId)
            .setAlgorithm(MeshAcknowledgementSecurity.SIGNATURE_ALGORITHM)
            .setRsa2048PssSha256(ByteString.copyFrom(keys.sign(envelope.senderNodeId, EnvelopeSecurity.canonical(unsigned)))))
            .build()
    }

    override suspend fun verify(envelope: Envelope, nowUnixMs: Long): Boolean = runCatching {
        require(envelope.hasSenderCredential() && envelope.hasSenderSignature())
        require(envelope.hasEncryptedPayload() && !envelope.encryptedPayload.aes256GcmCiphertext.isEmpty)
        val issuer = requireNotNull(trust.trustedIssuer.first())
        val claims = ProvisioningCredentialService().verify(envelope.senderCredential.toByteArray(), issuer.publicKeyDer, nowUnixMs)
        require(claims.nodeId == envelope.senderNodeId && claims.signingKeyId == envelope.senderSignature.keyId)
        val known = directory.findByNodeId(claims.nodeId)
        require(known?.revokedAtUnixMs == null) { "Origin revoked" }
        require(known == null || known.signingKeyId == claims.signingKeyId) { "Origin key changed" }
        EnvelopeSecurity.verifySignature(envelope, claims.rsa2048SigningPublicKeyDer.toByteArray())
    }.getOrDefault(false)
}
