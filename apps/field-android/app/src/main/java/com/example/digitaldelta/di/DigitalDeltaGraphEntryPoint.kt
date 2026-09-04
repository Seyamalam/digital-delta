package com.example.digitaldelta.di

import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.domain.identity.AndroidDeviceIdentityKeyStore
import com.example.digitaldelta.domain.identity.RecipientProvisioningRepository
import com.example.digitaldelta.domain.identity.TrustAnchorRepository
import com.example.digitaldelta.domain.mesh.MeshRuntimeStateStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Access to singleton graph components for Android-owned entry points that Hilt cannot construct,
 * such as the upcoming foreground relay service and black-box instrumentation evidence.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DigitalDeltaGraphEntryPoint {
    fun database(): DeltaDatabase
    fun deviceIdentityKeyStore(): AndroidDeviceIdentityKeyStore
    fun recipientProvisioningRepository(): RecipientProvisioningRepository
    fun trustAnchorRepository(): TrustAnchorRepository
    fun meshRuntimeStateStore(): MeshRuntimeStateStore
}
