package com.example.digitaldelta.domain.pod

import com.example.digitaldelta.domain.identity.AndroidDeviceIdentityKeyStore

class AndroidKeystoreDeliverySigner(
    private val nodeId: String,
    private val keyStore: AndroidDeviceIdentityKeyStore,
) : DeliverySigningKey {
    private val publicIdentity by lazy { keyStore.createOrGet(nodeId) }

    override val keyId: String
        get() = publicIdentity.signingKeyId

    override val publicKeyDer: ByteArray
        get() = publicIdentity.signingPublicKeyDer.copyOf()

    override fun sign(bytes: ByteArray): ByteArray = keyStore.sign(nodeId, bytes)
}
