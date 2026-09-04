package com.example.digitaldelta.domain.mesh

import com.example.digitaldelta.data.local.RecipientKeyDao
import com.example.digitaldelta.domain.identity.AndroidDeviceIdentityKeyStore
import com.example.digitaldelta.domain.identity.ProvisioningCredentialService
import com.example.digitaldelta.domain.identity.RecipientProvisioningRepository
import com.example.digitaldelta.domain.identity.TrustAnchorRepository
import com.example.digitaldelta.proto.v1.IdentityProvisioningCredential
import com.example.digitaldelta.proto.v1.PeerIdentityChallenge
import com.example.digitaldelta.proto.v1.PeerIdentityProof
import com.example.digitaldelta.proto.v1.Signature as ProtoSignature
import com.google.protobuf.ByteString
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.RSAKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first

interface PeerIdentityAuthenticator {
    suspend fun isActive(nodeId: String): Boolean = true
    fun createChallenge(): PeerIdentityChallenge
    suspend fun createProof(challenge: PeerIdentityChallenge): PeerIdentityProof
    suspend fun verifyProof(
        proof: PeerIdentityProof,
        expectedChallenge: PeerIdentityChallenge,
        expectedPeerNodeId: String,
    ): Boolean
}

class AndroidPeerIdentityAuthenticator(
    private val localNodeId: String,
    private val deviceKeys: AndroidDeviceIdentityKeyStore,
    private val recipientKeys: RecipientKeyDao,
    private val trustAnchors: TrustAnchorRepository,
    private val nowUnixMs: () -> Long = System::currentTimeMillis,
    private val secureRandom: SecureRandom = SecureRandom(),
) : PeerIdentityAuthenticator {
    override suspend fun isActive(nodeId: String): Boolean {
        val record = recipientKeys.findByNodeId(nodeId) ?: return false
        val now = nowUnixMs()
        return record.revokedAtUnixMs == null && record.issuedAtUnixMs <= now && record.expiresAtUnixMs > now
    }
    override fun createChallenge(): PeerIdentityChallenge =
        PeerIdentityAuthentication.newChallenge(
            challengerNodeId = localNodeId,
            nowUnixMs = nowUnixMs(),
            nonce = ByteArray(PeerIdentityAuthentication.NONCE_BYTES).also(secureRandom::nextBytes),
        )

    override suspend fun createProof(challenge: PeerIdentityChallenge): PeerIdentityProof {
        val now = nowUnixMs()
        if (now > challenge.expiresAtUnixMs ||
            now < challenge.issuedAtUnixMs - PeerIdentityAuthentication.MAX_CLOCK_SKEW_MILLIS
        ) throw SecurityException("PEER_CHALLENGE_EXPIRED")
        val localCredential = recipientKeys.findByNodeId(localNodeId)
            ?: throw SecurityException("LOCAL_PROVISIONING_CREDENTIAL_MISSING")
        if (localCredential.issuedAtUnixMs > now ||
            localCredential.expiresAtUnixMs <= now ||
            localCredential.revokedAtUnixMs?.let { it <= now } == true
        ) throw SecurityException("LOCAL_PROVISIONING_CREDENTIAL_INACTIVE")
        val deviceIdentity = deviceKeys.createOrGet(localNodeId)
        if (localCredential.signingKeyId != deviceIdentity.signingKeyId ||
            !MessageDigest.isEqual(localCredential.signingPublicKeyDer, deviceIdentity.signingPublicKeyDer)
        ) throw SecurityException("LOCAL_PROVISIONING_KEY_MISMATCH")
        return PeerIdentityAuthentication.createProof(
            challenge = challenge,
            proverNodeId = localNodeId,
            credential = IdentityProvisioningCredential.parseFrom(localCredential.credentialBytes),
            signedAtUnixMs = now,
            signingKeyId = deviceIdentity.signingKeyId,
            signBytes = { payload -> deviceKeys.sign(localNodeId, payload) },
        )
    }

    override suspend fun verifyProof(
        proof: PeerIdentityProof,
        expectedChallenge: PeerIdentityChallenge,
        expectedPeerNodeId: String,
    ): Boolean {
        val trustedIssuer = trustAnchors.trustedIssuer.first() ?: return false
        val now = nowUnixMs()
        val verified = PeerIdentityAuthentication.verifyProof(
            proof = proof,
            expectedChallenge = expectedChallenge,
            expectedPeerNodeId = expectedPeerNodeId,
            trustedIssuerPublicKeyDer = trustedIssuer.publicKeyDer,
            nowUnixMs = now,
        )
        if (!verified) return false
        val known = recipientKeys.findByNodeId(expectedPeerNodeId)
        if (known?.revokedAtUnixMs != null) return false
        if (known != null && known.signingKeyId != proof.credential.claims.signingKeyId) return false
        RecipientProvisioningRepository(recipientKeys).accept(
            credentialBytes = proof.credential.toByteArray(),
            trustedIssuerPublicKeyDer = trustedIssuer.publicKeyDer,
            nowUnixMs = now,
        )
        return recipientKeys.findByNodeId(expectedPeerNodeId)?.revokedAtUnixMs == null
    }
}

object PeerIdentityAuthentication {
    const val NONCE_BYTES = 32
    const val CHALLENGE_TTL_MILLIS = 30_000L
    const val MAX_CLOCK_SKEW_MILLIS = 10 * 60 * 1_000L
    const val SIGNATURE_ALGORITHM = "RSA-2048-PSS-SHA256"

    fun newChallenge(
        challengerNodeId: String,
        nowUnixMs: Long,
        nonce: ByteArray,
    ): PeerIdentityChallenge {
        require(challengerNodeId.isNotBlank()) { "challenger node id is required" }
        require(nonce.size == NONCE_BYTES) { "peer challenge nonce must be $NONCE_BYTES bytes" }
        return PeerIdentityChallenge.newBuilder()
            .setChallengerNodeId(challengerNodeId)
            .setNonce(ByteString.copyFrom(nonce))
            .setIssuedAtUnixMs(nowUnixMs)
            .setExpiresAtUnixMs(nowUnixMs + CHALLENGE_TTL_MILLIS)
            .build()
    }

    fun createProof(
        challenge: PeerIdentityChallenge,
        proverNodeId: String,
        credential: IdentityProvisioningCredential,
        signedAtUnixMs: Long,
        signingKeyId: String,
        signBytes: (ByteArray) -> ByteArray,
    ): PeerIdentityProof {
        require(validChallengeShape(challenge)) { "peer challenge is malformed" }
        require(proverNodeId.isNotBlank()) { "prover node id is required" }
        require(credential.hasClaims()) { "provisioning credential is required" }
        require(signingKeyId.isNotBlank()) { "signing key id is required" }
        val unsigned = PeerIdentityProof.newBuilder()
            .setChallenge(challenge)
            .setProverNodeId(proverNodeId)
            .setCredential(credential)
            .setSignedAtUnixMs(signedAtUnixMs)
            .build()
        val signature = signBytes(canonicalPayload(unsigned))
        require(signature.isNotEmpty()) { "peer identity signature is required" }
        return unsigned.toBuilder()
            .setNodeSignature(
                ProtoSignature.newBuilder()
                    .setKeyId(signingKeyId)
                    .setAlgorithm(SIGNATURE_ALGORITHM)
                    .setRsa2048PssSha256(ByteString.copyFrom(signature)),
            )
            .build()
    }

    fun verifyProof(
        proof: PeerIdentityProof,
        expectedChallenge: PeerIdentityChallenge,
        expectedPeerNodeId: String,
        trustedIssuerPublicKeyDer: ByteArray,
        nowUnixMs: Long,
    ): Boolean {
        if (!validChallengeShape(expectedChallenge) || proof.challenge != expectedChallenge) return false
        if (expectedPeerNodeId.isBlank() || proof.proverNodeId != expectedPeerNodeId) return false
        if (nowUnixMs > expectedChallenge.expiresAtUnixMs ||
            nowUnixMs < expectedChallenge.issuedAtUnixMs - MAX_CLOCK_SKEW_MILLIS ||
            proof.signedAtUnixMs !in expectedChallenge.issuedAtUnixMs..expectedChallenge.expiresAtUnixMs ||
            proof.signedAtUnixMs !in
            (nowUnixMs - MAX_CLOCK_SKEW_MILLIS)..(nowUnixMs + MAX_CLOCK_SKEW_MILLIS)
        ) return false
        if (!proof.hasCredential() || !proof.hasNodeSignature()) return false
        val claims = runCatching {
            ProvisioningCredentialService().verify(
                proof.credential.toByteArray(),
                trustedIssuerPublicKeyDer,
                nowUnixMs,
            )
        }.getOrNull() ?: return false
        if (claims.nodeId != expectedPeerNodeId ||
            claims.signingKeyId != proof.nodeSignature.keyId ||
            proof.nodeSignature.algorithm != SIGNATURE_ALGORITHM ||
            proof.nodeSignature.rsa2048PssSha256.isEmpty
        ) return false
        return runCatching {
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(
                X509EncodedKeySpec(claims.rsa2048SigningPublicKeyDer.toByteArray()),
            )
            if (publicKey !is RSAKey || publicKey.modulus.bitLength() < 2048) return false
            rsaPss().run {
                initVerify(publicKey)
                update(canonicalPayload(proof))
                verify(proof.nodeSignature.rsa2048PssSha256.toByteArray())
            }
        }.getOrDefault(false)
    }

    fun canonicalPayload(proof: PeerIdentityProof): ByteArray = proof.toBuilder()
        .clearNodeSignature()
        .build()
        .toByteArray()

    private fun validChallengeShape(challenge: PeerIdentityChallenge): Boolean =
        challenge.challengerNodeId.isNotBlank() &&
            challenge.nonce.size() == NONCE_BYTES &&
            challenge.issuedAtUnixMs < challenge.expiresAtUnixMs &&
            challenge.expiresAtUnixMs - challenge.issuedAtUnixMs == CHALLENGE_TTL_MILLIS

    private fun rsaPss(): Signature = runCatching {
        Signature.getInstance("RSASSA-PSS").apply {
            setParameter(PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1))
        }
    }.getOrElse {
        Signature.getInstance("SHA256withRSA/PSS")
    }
}

class PendingPeerChallenges {
    private val challenges = ConcurrentHashMap<String, PeerIdentityChallenge>()

    fun put(endpointId: String, challenge: PeerIdentityChallenge) {
        require(endpointId.isNotBlank()) { "endpoint id is required" }
        check(challenges.putIfAbsent(endpointId, challenge) == null) {
            "endpoint already has a pending challenge"
        }
    }

    fun expected(endpointId: String): PeerIdentityChallenge? = challenges[endpointId]

    fun consume(endpointId: String, challenge: PeerIdentityChallenge): Boolean =
        challenges.remove(endpointId, challenge)

    fun remove(endpointId: String) {
        challenges.remove(endpointId)
    }

    fun clear() = challenges.clear()
}
