package com.example.digitaldelta.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeltaMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DeltaDatabase::class.java,
    )

    @Test
    fun migrationOneToTwoPreservesEventsAndAddsRecipientDirectory() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                "INSERT INTO operation_log " +
                    "(eventId, missionId, eventType, payloadBytes, createdAtUnixMs) " +
                    "VALUES ('event-1', 'mission-1', 'REQUEST_CREATED', X'0102', 100)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            DeltaMigrations.VERSION_1_TO_2,
        ).use { migrated ->
            assertEquals(1, migrated.count("SELECT COUNT(*) FROM operation_log"))
            migrated.execSQL(
                """
                INSERT INTO recipient_keys (
                    nodeId, identityId, displayName, roleCode, encryptionKeyId,
                    encryptionPublicKeyDer, signingKeyId, signingPublicKeyDer,
                    issuerIdentityId, credentialBytes, issuedAtUnixMs, expiresAtUnixMs,
                    revokedAtUnixMs, provisionedAtUnixMs
                ) VALUES (
                    'N6', 'hospital-1', 'Habiganj Medical', 'IDENTITY_ROLE_HOSPITAL',
                    'enc-1', X'01', 'sig-1', X'02', 'admin-1', X'03', 100, 200, NULL, 110
                )
                """.trimIndent(),
            )
            assertEquals(1, migrated.count("SELECT COUNT(*) FROM recipient_keys"))
        }
    }

    @Test
    fun migrationTwoToThreePreservesRecipientsAndAddsDurableMeshInbox() {
        helper.createDatabase(TEST_DATABASE_V3, 2).apply {
            execSQL(
                "INSERT INTO recipient_keys " +
                    "(nodeId, identityId, displayName, roleCode, encryptionKeyId, " +
                    "encryptionPublicKeyDer, signingKeyId, signingPublicKeyDer, " +
                    "issuerIdentityId, credentialBytes, issuedAtUnixMs, expiresAtUnixMs, " +
                    "revokedAtUnixMs, provisionedAtUnixMs) VALUES " +
                    "('N6', 'hospital-1', 'Habiganj Medical', 'HOSPITAL', 'enc-1', " +
                    "X'01', 'sig-1', X'02', 'admin-1', X'03', 100, 200, NULL, 110)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_V3,
            3,
            true,
            DeltaMigrations.VERSION_2_TO_3,
        ).use { migrated ->
            assertEquals(1, migrated.count("SELECT COUNT(*) FROM recipient_keys"))
            assertEquals(0, migrated.count("SELECT COUNT(*) FROM mesh_inbox"))
            assertEquals(0, migrated.count("SELECT COUNT(*) FROM seen_messages"))
        }
    }

    @Test
    fun migrationThreeToFourPreservesMeshStateAndAddsConflictProjectionTables() {
        helper.createDatabase(TEST_DATABASE_V4, 3).apply {
            execSQL(
                "INSERT INTO seen_messages (messageId, expiresAtUnixMs, firstSeenAtUnixMs) " +
                    "VALUES ('message-1', 200, 100)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_V4,
            4,
            true,
            DeltaMigrations.VERSION_3_TO_4,
        ).use { migrated ->
            assertEquals(1, migrated.count("SELECT COUNT(*) FROM seen_messages"))
            assertEquals(0, migrated.count("SELECT COUNT(*) FROM mission_projections"))
            assertEquals(0, migrated.count("SELECT COUNT(*) FROM conflicts"))
        }
    }

    @Test
    fun migrationFourToFivePreservesInboxAndAddsApplicationLedger() {
        helper.createDatabase(TEST_DATABASE_V5, 4).apply {
            execSQL(
                "INSERT INTO mesh_inbox " +
                    "(messageId, wireBytes, senderNodeId, recipientNodeId, expiresAtUnixMs, " +
                    "hopCount, hopLimit, receivedAtUnixMs) VALUES " +
                    "('message-local', X'0102', 'N1', 'N4', 200, 0, 8, 100)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_V5,
            5,
            true,
            DeltaMigrations.VERSION_4_TO_5,
        ).use { migrated ->
            assertEquals(1, migrated.count("SELECT COUNT(*) FROM mesh_inbox"))
            assertEquals(0, migrated.count("SELECT COUNT(*) FROM inbox_applications"))
        }
    }

    private fun SupportSQLiteDatabase.count(query: String): Int =
        query(query).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    companion object {
        private const val TEST_DATABASE = "delta-migration-test"
        private const val TEST_DATABASE_V3 = "delta-migration-v3-test"
        private const val TEST_DATABASE_V4 = "delta-migration-v4-test"
        private const val TEST_DATABASE_V5 = "delta-migration-v5-test"
    }
}
