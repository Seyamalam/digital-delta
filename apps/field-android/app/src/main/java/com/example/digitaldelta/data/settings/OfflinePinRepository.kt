package com.example.digitaldelta.data.settings

import androidx.datastore.core.DataStore
import com.example.digitaldelta.settings.v1.UserSettings
import com.google.protobuf.ByteString
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OfflinePinSnapshot(
    val configured: Boolean,
    val failedAttempts: Int,
    val lockedUntilUnixMs: Long,
)

sealed interface PinVerification {
    data object Accepted : PinVerification
    data class Rejected(val attemptsRemaining: Int) : PinVerification
    data class LockedOut(val untilUnixMs: Long) : PinVerification
}

interface OfflinePinRepository {
    suspend fun snapshot(nowUnixMs: Long): OfflinePinSnapshot
    suspend fun configure(pin: String)
    suspend fun verify(pin: String, nowUnixMs: Long): PinVerification
}

class ProtoOfflinePinRepository(
    private val dataStore: DataStore<UserSettings>,
    private val secureRandom: SecureRandom = SecureRandom(),
) : OfflinePinRepository {
    override suspend fun snapshot(nowUnixMs: Long): OfflinePinSnapshot {
        val settings = dataStore.updateData { current ->
            if (current.pinLockedUntilUnixMs in 1..nowUnixMs) {
                current.toBuilder().setPinLockedUntilUnixMs(0).setFailedPinAttempts(0).build()
            } else {
                current
            }
        }
        return settings.toSnapshot()
    }

    override suspend fun configure(pin: String) = withContext(Dispatchers.Default) {
        requireValidPin(pin)
        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val hash = derive(pin, salt)
        dataStore.updateData { settings ->
            settings.toBuilder()
                .setOfflinePinSalt(ByteString.copyFrom(salt))
                .setOfflinePinHash(ByteString.copyFrom(hash))
                .setFailedPinAttempts(0)
                .setPinLockedUntilUnixMs(0)
                .build()
        }
        hash.fill(0)
    }

    override suspend fun verify(pin: String, nowUnixMs: Long): PinVerification = withContext(Dispatchers.Default) {
        var result: PinVerification = PinVerification.Rejected(MAX_ATTEMPTS)
        dataStore.updateData { settings ->
            if (!settings.isPinConfigured()) {
                result = PinVerification.Rejected(MAX_ATTEMPTS)
                return@updateData settings
            }
            if (settings.pinLockedUntilUnixMs > nowUnixMs) {
                result = PinVerification.LockedOut(settings.pinLockedUntilUnixMs)
                return@updateData settings
            }
            val candidate = if (PIN_PATTERN.matches(pin)) {
                derive(pin, settings.offlinePinSalt.toByteArray())
            } else {
                ByteArray(HASH_BYTES)
            }
            val accepted = MessageDigest.isEqual(candidate, settings.offlinePinHash.toByteArray())
            candidate.fill(0)
            if (accepted) {
                result = PinVerification.Accepted
                settings.toBuilder().setFailedPinAttempts(0).setPinLockedUntilUnixMs(0).build()
            } else {
                val failures = settings.failedPinAttempts + 1
                if (failures >= MAX_ATTEMPTS) {
                    val until = nowUnixMs + LOCKOUT_MILLIS
                    result = PinVerification.LockedOut(until)
                    settings.toBuilder()
                        .setFailedPinAttempts(MAX_ATTEMPTS)
                        .setPinLockedUntilUnixMs(until)
                        .build()
                } else {
                    result = PinVerification.Rejected(MAX_ATTEMPTS - failures)
                    settings.toBuilder().setFailedPinAttempts(failures).build()
                }
            }
        }
        result
    }

    private fun UserSettings.toSnapshot(): OfflinePinSnapshot = OfflinePinSnapshot(
        configured = isPinConfigured(),
        failedAttempts = failedPinAttempts,
        lockedUntilUnixMs = pinLockedUntilUnixMs,
    )

    private fun UserSettings.isPinConfigured(): Boolean =
        offlinePinSalt.size() >= SALT_BYTES && offlinePinHash.size() == HASH_BYTES

    private fun requireValidPin(pin: String) {
        require(PIN_PATTERN.matches(pin)) { "PIN must contain exactly six digits" }
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val chars = pin.toCharArray()
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(PBEKeySpec(chars, salt, ITERATIONS, HASH_BYTES * 8))
                .encoded
        } finally {
            chars.fill('\u0000')
        }
    }

    companion object {
        private val PIN_PATTERN = Regex("^[0-9]{6}$")
        private const val SALT_BYTES = 16
        private const val HASH_BYTES = 32
        private const val ITERATIONS = 120_000
        private const val MAX_ATTEMPTS = 5
        private const val LOCKOUT_MILLIS = 30_000L
    }
}
