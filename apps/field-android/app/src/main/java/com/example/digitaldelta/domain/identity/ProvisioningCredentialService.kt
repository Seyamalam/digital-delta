package com.example.digitaldelta.domain.identity

import com.example.digitaldelta.proto.v1.IdentityProvisioningClaims
import com.example.digitaldelta.proto.v1.IdentityProvisioningCredential
import com.example.digitaldelta.proto.v1.IdentityRole
import com.example.digitaldelta.proto.v1.Signature as WireSignature
import com.google.protobuf.ByteString
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.PSSParameterSpec
import java.security.spec.X509EncodedKeySpec

class ProvisioningCredentialException(message: String, cause: Throwable? = null) :
    SecurityException(message, cause)

class ProvisioningCredentialService {
    fun issue(
        claims: IdentityProvisioningClaims,
        issuerKeyId: String,
        issuerPrivateKeyDer: ByteArray,
    ): ByteArray {
        validateClaims(claims)
        require(issuerKeyId.isNotBlank()) { "issuer key id is required" }
        val privateKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(issuerPrivateKeyDer))
        requireRsa2048(privateKey)
        val signatureBytes = newSignature().run {
            initSign(privateKey)
            update(claims.toByteArray())
            sign()
        }
        return IdentityProvisioningCredential.newBuilder()
            .setClaims(claims)
            .setIssuerSignature(
                WireSignature.newBuilder()
                    .setKeyId(issuerKeyId)
                    .setRsa2048PssSha256(ByteString.copyFrom(signatureBytes))
                    .setAlgorithm(SIGNATURE_ALGORITHM)
                    .build(),
            )
            .build()
            .toByteArray()
    }

    fun verify(
        credentialBytes: ByteArray,
        trustedIssuerPublicKeyDer: ByteArray,
        nowUnixMs: Long,
    ): IdentityProvisioningClaims = try {
        val credential = IdentityProvisioningCredential.parseFrom(credentialBytes)
        val claims = credential.claims
        validateClaims(claims)
        if (nowUnixMs < claims.issuedAtUnixMs || nowUnixMs >= claims.expiresAtUnixMs) {
            throw ProvisioningCredentialException("credential is outside its validity window")
        }
        val wireSignature = credential.issuerSignature
        if (wireSignature.algorithm != SIGNATURE_ALGORITHM || wireSignature.rsa2048PssSha256.isEmpty) {
            throw ProvisioningCredentialException("unsupported or missing issuer signature")
        }
        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(trustedIssuerPublicKeyDer))
        requireRsa2048(publicKey)
        val verified = newSignature().run {
            initVerify(publicKey)
            update(claims.toByteArray())
            verify(wireSignature.rsa2048PssSha256.toByteArray())
        }
        if (!verified) throw ProvisioningCredentialException("issuer signature is invalid")
        claims
    } catch (error: ProvisioningCredentialException) {
        throw error
    } catch (error: Exception) {
        throw ProvisioningCredentialException("credential could not be verified", error)
    }

    private fun validateClaims(claims: IdentityProvisioningClaims) {
        require(claims.credentialId.isNotBlank()) { "credential id is required" }
        require(claims.identityId.isNotBlank()) { "identity id is required" }
        require(claims.nodeId.isNotBlank()) { "node id is required" }
        require(claims.displayName.isNotBlank()) { "display name is required" }
        require(claims.role != IdentityRole.IDENTITY_ROLE_UNSPECIFIED) { "role is required" }
        require(claims.encryptionKeyId.isNotBlank()) { "encryption key id is required" }
        require(claims.signingKeyId.isNotBlank()) { "signing key id is required" }
        require(claims.issuedAtUnixMs < claims.expiresAtUnixMs) { "credential validity window is invalid" }
        require(claims.issuerIdentityId.isNotBlank()) { "issuer identity id is required" }
        val encryptionKey = KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(claims.rsa2048EncryptionPublicKeyDer.toByteArray()),
        )
        val signingKey = KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(claims.rsa2048SigningPublicKeyDer.toByteArray()),
        )
        requireRsa2048(encryptionKey)
        requireRsa2048(signingKey)
    }

    private fun requireRsa2048(key: java.security.Key) {
        require(key is RSAKey && key.modulus.bitLength() >= 2048) { "RSA key must be at least 2048 bits" }
    }

    private fun newSignature(): Signature = runCatching {
        Signature.getInstance("RSASSA-PSS").apply {
            setParameter(PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1))
        }
    }.getOrElse {
        // Android exposes the digest-bound standard name on releases whose provider does not
        // publish the generic RSASSA-PSS alias.
        Signature.getInstance("SHA256withRSA/PSS")
    }

    companion object {
        const val SIGNATURE_ALGORITHM = "RSA-2048-PSS-SHA256"
    }
}
