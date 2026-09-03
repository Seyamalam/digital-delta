package com.example.digitaldelta.domain.identity

import com.example.digitaldelta.proto.v1.IdentityEnrollmentRequest
import com.example.digitaldelta.proto.v1.IdentityRole
import com.google.protobuf.ByteString
import java.security.KeyFactory
import java.security.interfaces.RSAKey
import java.security.spec.X509EncodedKeySpec

class EnrollmentRequestService {
    fun create(
        identityId: String,
        displayName: String,
        role: IdentityRole,
        publicIdentity: DevicePublicIdentity,
        createdAtUnixMs: Long,
        nonce: ByteArray,
    ): ByteArray {
        require(identityId.isNotBlank()) { "identity id is required" }
        require(displayName.isNotBlank()) { "display name is required" }
        require(role != IdentityRole.IDENTITY_ROLE_UNSPECIFIED) { "role is required" }
        require(createdAtUnixMs > 0) { "creation time is required" }
        require(nonce.size >= 16) { "enrollment nonce must be at least 128 bits" }
        validateRsa2048(publicIdentity.encryptionPublicKeyDer)
        validateRsa2048(publicIdentity.signingPublicKeyDer)
        return IdentityEnrollmentRequest.newBuilder()
            .setIdentityId(identityId)
            .setNodeId(publicIdentity.nodeId)
            .setDisplayName(displayName)
            .setRole(role)
            .setEncryptionKeyId(publicIdentity.encryptionKeyId)
            .setRsa2048EncryptionPublicKeyDer(ByteString.copyFrom(publicIdentity.encryptionPublicKeyDer))
            .setSigningKeyId(publicIdentity.signingKeyId)
            .setRsa2048SigningPublicKeyDer(ByteString.copyFrom(publicIdentity.signingPublicKeyDer))
            .setCreatedAtUnixMs(createdAtUnixMs)
            .setNonce(ByteString.copyFrom(nonce))
            .build()
            .toByteArray()
    }

    private fun validateRsa2048(encoded: ByteArray) {
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(encoded))
        require(key is RSAKey && key.modulus.bitLength() >= 2048) { "RSA key must be at least 2048 bits" }
    }
}
