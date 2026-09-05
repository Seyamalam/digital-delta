package com.example.digitaldelta.domain.observer

import android.content.Context
import android.content.pm.ApplicationInfo
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.KeyStore

/** Publishing is opt-in. The per-node token is encrypted with a non-exportable device key. */
class ObserverSettings(private val context: Context) {
    private val preferences = context.getSharedPreferences("observer-private", Context.MODE_PRIVATE)
    fun configured(): Boolean = preferences.contains("configuration")
    fun save(code: String) {
        ObserverConfiguration.parse(code, debug())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(code.encodeToByteArray())
        check(preferences.edit().putString("configuration", Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)).commit())
    }
    fun load(): ObserverConfiguration? {
        val saved = preferences.getString("configuration", null) ?: return null
        val bytes = Base64.decode(saved, Base64.NO_WRAP)
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        return ObserverConfiguration.parse(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).decodeToString(), debug())
    }
    fun disable() { check(preferences.edit().remove("configuration").commit()) }
    private fun debug() = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private fun key(): SecretKey = synchronized(lock) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    companion object { private const val ALIAS = "digital-delta-observer-configuration-v1"; private val lock = Any() }
}
