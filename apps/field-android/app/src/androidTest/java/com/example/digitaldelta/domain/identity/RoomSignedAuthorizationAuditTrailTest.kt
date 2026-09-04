package com.example.digitaldelta.domain.identity

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.proto.v1.DomainEvent
import com.example.digitaldelta.proto.v1.IdentityRole
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomSignedAuthorizationAuditTrailTest {
    private lateinit var database: DeltaDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            DeltaDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun signedRecordsFormTamperEvidentAppendOnlyChain() = runTest {
        var now = 1_788_374_217_000L
        var sequence = 0
        val trail = RoomSignedAuthorizationAuditTrail(
            database = database,
            deviceKeys = AndroidDeviceIdentityKeyStore(),
            nowUnixMs = { now++ },
            auditId = { "audit-${++sequence}" },
        )

        trail.record(
            actorIdentityId = "clinic-sylhet-01",
            actorNodeId = "N4",
            role = IdentityRole.IDENTITY_ROLE_CLINIC,
            permission = Permission.RESOLVE_CONFLICT,
            allowed = false,
            reasonCode = "ROLE_FORBIDDEN",
        )
        trail.record(
            actorIdentityId = "clinic-sylhet-01",
            actorNodeId = "N4",
            role = IdentityRole.IDENTITY_ROLE_CLINIC,
            permission = Permission.CREATE_REQUEST,
            allowed = true,
            reasonCode = "AUTHORIZED",
        )

        val operations = database.operationLogDao().authorizationAudit()
        assertEquals(listOf("audit-1", "audit-2"), operations.map { it.eventId })
        val first = DomainEvent.parseFrom(operations[0].payloadBytes)
        val second = DomainEvent.parseFrom(operations[1].payloadBytes)
        assertFalse(first.authorizationAudit.entry.allowed)
        assertTrue(second.authorizationAudit.entry.allowed)
        assertTrue(
            MessageDigest.isEqual(
                MessageDigest.getInstance("SHA-256").digest(operations[0].payloadBytes),
                second.authorizationAudit.entry.previousRecordSha256.toByteArray(),
            ),
        )
        assertTrue(trail.verifyChain())

        val tamperedSigned = first.authorizationAudit.toBuilder()
            .setEntry(first.authorizationAudit.entry.toBuilder().setReasonCode("AUTHORIZED").build())
            .build()
        val tamperedFirst = first.toBuilder().setAuthorizationAudit(tamperedSigned).build().toByteArray()
        assertFalse(trail.verifyPayloads(listOf(tamperedFirst, operations[1].payloadBytes)))
    }
}
