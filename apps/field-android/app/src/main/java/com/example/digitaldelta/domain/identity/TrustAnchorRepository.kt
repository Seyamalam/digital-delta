package com.example.digitaldelta.domain.identity

import androidx.datastore.core.DataStore
import com.example.digitaldelta.settings.v1.UserSettings
import com.google.protobuf.ByteString
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.interfaces.RSAKey
import java.security.spec.X509EncodedKeySpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class TrustedIssuerKey(
    val publicKeyDer: ByteArray,
    val fingerprint: String,
)

interface TrustAnchorRepository {
    val trustedIssuer: Flow<TrustedIssuerKey?>
    suspend fun pin(publicKeyDer: ByteArray): TrustedIssuerKey
}

class ProtoTrustAnchorRepository(
    private val dataStore: DataStore<UserSettings>,
) : TrustAnchorRepository {
    override val trustedIssuer: Flow<TrustedIssuerKey?> = dataStore.data.map { settings ->
        if (settings.trustedIssuerPublicKeyDer.isEmpty) {
            null
        } else {
            TrustedIssuerKey(
                settings.trustedIssuerPublicKeyDer.toByteArray(),
                settings.trustedIssuerFingerprint,
            )
        }
    }

    override suspend fun pin(publicKeyDer: ByteArray): TrustedIssuerKey {
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyDer))
        require(key is RSAKey && key.modulus.bitLength() >= 2048) {
            "administrator trust key must be RSA-2048 or stronger"
        }
        val trusted = TrustedIssuerKey(publicKeyDer.copyOf(), fingerprint(publicKeyDer))
        dataStore.updateData { settings ->
            settings.toBuilder()
                .setTrustedIssuerPublicKeyDer(ByteString.copyFrom(publicKeyDer))
                .setTrustedIssuerFingerprint(trusted.fingerprint)
                .build()
        }
        return trusted
    }

    private fun fingerprint(encoded: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(encoded)
        .take(8)
        .joinToString(":") { "%02X".format(it) }
}
