package com.example.digitaldelta.domain.identity

import com.example.digitaldelta.proto.v1.CredentialRevocationClaims
import com.example.digitaldelta.proto.v1.Signature as WireSignature
import com.example.digitaldelta.proto.v1.SignedCredentialRevocation
import com.google.protobuf.ByteString
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.PSSParameterSpec
import java.security.spec.X509EncodedKeySpec

class CredentialRevocationService {
    fun issue(
        claims: CredentialRevocationClaims,
        issuerKeyId: String,
        issuerPrivateKeyDer: ByteArray,
    ): ByteArray {
        validateClaims(claims)
        require(issuerKeyId.isNotBlank()) { "issuer key id is required" }
        val privateKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(issuerPrivateKeyDer))
        requireRsa2048(privateKey)
        val signature = newSignature().run {
            initSign(privateKey)
            update(claims.toByteArray())
            sign()
        }
        return SignedCredentialRevocation.newBuilder()
            .setClaims(claims)
            .setIssuerSignature(
                WireSignature.newBuilder()
                    .setKeyId(issuerKeyId)
                    .setRsa2048PssSha256(ByteString.copyFrom(signature))
                    .setAlgorithm(ProvisioningCredentialService.SIGNATURE_ALGORITHM)
                    .build(),
            )
            .build()
            .toByteArray()
    }

    fun verify(
        revocationBytes: ByteArray,
        trustedIssuerPublicKeyDer: ByteArray,
        nowUnixMs: Long,
    ): CredentialRevocationClaims = try {
        val revocation = SignedCredentialRevocation.parseFrom(revocationBytes)
        val claims = revocation.claims
        validateClaims(claims)
        if (claims.revokedAtUnixMs > nowUnixMs + MAX_FUTURE_CLOCK_SKEW_MS) {
            throw ProvisioningCredentialException("revocation time is too far in the future")
        }
        val wireSignature = revocation.issuerSignature
        if (
            wireSignature.algorithm != ProvisioningCredentialService.SIGNATURE_ALGORITHM ||
            wireSignature.rsa2048PssSha256.isEmpty
        ) {
            throw ProvisioningCredentialException("unsupported or missing revocation signature")
        }
        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(trustedIssuerPublicKeyDer))
        requireRsa2048(publicKey)
        val verified = newSignature().run {
            initVerify(publicKey)
            update(claims.toByteArray())
            verify(wireSignature.rsa2048PssSha256.toByteArray())
        }
        if (!verified) throw ProvisioningCredentialException("revocation signature is invalid")
        claims
    } catch (error: ProvisioningCredentialException) {
        throw error
    } catch (error: Exception) {
        throw ProvisioningCredentialException("revocation could not be verified", error)
    }

    private fun validateClaims(claims: CredentialRevocationClaims) {
        require(claims.revocationId.isNotBlank()) { "revocation id is required" }
        require(claims.credentialId.isNotBlank()) { "credential id is required" }
        require(claims.identityId.isNotBlank()) { "identity id is required" }
        require(claims.nodeId.isNotBlank()) { "node id is required" }
        require(claims.revokedAtUnixMs > 0) { "revocation time is required" }
        require(claims.reasonCode.isNotBlank()) { "revocation reason is required" }
        require(claims.issuerIdentityId.isNotBlank()) { "issuer identity is required" }
        require(claims.nonce.size() >= 16) { "128-bit revocation nonce is required" }
    }

    private fun requireRsa2048(key: java.security.Key) {
        require(key is RSAKey && key.modulus.bitLength() >= 2048) { "RSA key must be at least 2048 bits" }
    }

    private fun newSignature(): Signature = runCatching {
        Signature.getInstance("RSASSA-PSS").apply {
            setParameter(PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1))
        }
    }.getOrElse { Signature.getInstance("SHA256withRSA/PSS") }

    companion object {
        private const val MAX_FUTURE_CLOCK_SKEW_MS = 5 * 60 * 1_000L
    }
}
