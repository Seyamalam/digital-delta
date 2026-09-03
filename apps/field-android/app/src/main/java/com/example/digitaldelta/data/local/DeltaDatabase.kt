package com.example.digitaldelta.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

enum class QueueState {
    PENDING,
    IN_FLIGHT,
    ACKNOWLEDGED,
    DEAD_LETTER,
}

@Entity(
    tableName = "mesh_outbox",
    indices = [Index(value = ["state", "nextAttemptAtUnixMs", "priority"])],
)
data class MeshEnvelopeEntity(
    @PrimaryKey val messageId: String,
    val wireBytes: ByteArray,
    val priority: Int,
    val expiresAtUnixMs: Long,
    val state: String,
    val attemptCount: Int,
    val nextAttemptAtUnixMs: Long,
    val acknowledgedAtUnixMs: Long? = null,
)

@Entity(tableName = "used_nonces", indices = [Index(value = ["deliveryId"])])
data class UsedNonceEntity(
    @PrimaryKey val nonceSha256: String,
    val deliveryId: String,
    val usedAtUnixMs: Long,
)

@Entity(
    tableName = "operation_log",
    indices = [Index(value = ["missionId", "createdAtUnixMs"])],
)
data class OperationEntity(
    @PrimaryKey val eventId: String,
    val missionId: String,
    val eventType: String,
    val payloadBytes: ByteArray,
    val createdAtUnixMs: Long,
)

@Entity(
    tableName = "recipient_keys",
    indices = [Index(value = ["encryptionKeyId"], unique = true)],
)
data class RecipientKeyEntity(
    @PrimaryKey val nodeId: String,
    val identityId: String,
    val displayName: String,
    val roleCode: String,
    val encryptionKeyId: String,
    val encryptionPublicKeyDer: ByteArray,
    val signingKeyId: String,
    val signingPublicKeyDer: ByteArray,
    val issuerIdentityId: String,
    val credentialBytes: ByteArray,
    val issuedAtUnixMs: Long,
    val expiresAtUnixMs: Long,
    val revokedAtUnixMs: Long?,
    val provisionedAtUnixMs: Long,
)

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(envelope: MeshEnvelopeEntity): Long

    @Query(
        """
        SELECT * FROM mesh_outbox
        WHERE state = 'PENDING'
          AND expiresAtUnixMs > :nowUnixMs
          AND nextAttemptAtUnixMs <= :nowUnixMs
        ORDER BY priority ASC, nextAttemptAtUnixMs ASC, messageId ASC
        LIMIT :limit
        """,
    )
    suspend fun pending(nowUnixMs: Long, limit: Int): List<MeshEnvelopeEntity>

    @Query(
        """
        UPDATE mesh_outbox
        SET state = 'ACKNOWLEDGED', acknowledgedAtUnixMs = :acknowledgedAtUnixMs
        WHERE messageId = :messageId
        """,
    )
    suspend fun markAcknowledged(messageId: String, acknowledgedAtUnixMs: Long): Int
}

@Dao
interface NonceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun claim(nonce: UsedNonceEntity): Long
}

@Dao
interface OperationLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun append(operation: OperationEntity)

    @Query(
        """
        SELECT * FROM operation_log
        WHERE missionId = :missionId
        ORDER BY createdAtUnixMs ASC, eventId ASC
        """,
    )
    suspend fun forMission(missionId: String): List<OperationEntity>
}

@Dao
interface RecipientKeyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(key: RecipientKeyEntity)

    @Query("SELECT * FROM recipient_keys WHERE nodeId = :nodeId LIMIT 1")
    suspend fun findByNodeId(nodeId: String): RecipientKeyEntity?

    @Query("SELECT * FROM recipient_keys ORDER BY provisionedAtUnixMs DESC, nodeId ASC LIMIT 1")
    suspend fun mostRecentlyProvisioned(): RecipientKeyEntity?
}

@Database(
    entities = [
        MeshEnvelopeEntity::class,
        UsedNonceEntity::class,
        OperationEntity::class,
        RecipientKeyEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class DeltaDatabase : RoomDatabase() {
    abstract fun outboxDao(): OutboxDao
    abstract fun nonceDao(): NonceDao
    abstract fun operationLogDao(): OperationLogDao
    abstract fun recipientKeyDao(): RecipientKeyDao
}

object DeltaMigrations {
    val VERSION_1_TO_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS recipient_keys (
                    nodeId TEXT NOT NULL,
                    identityId TEXT NOT NULL,
                    displayName TEXT NOT NULL,
                    roleCode TEXT NOT NULL,
                    encryptionKeyId TEXT NOT NULL,
                    encryptionPublicKeyDer BLOB NOT NULL,
                    signingKeyId TEXT NOT NULL,
                    signingPublicKeyDer BLOB NOT NULL,
                    issuerIdentityId TEXT NOT NULL,
                    credentialBytes BLOB NOT NULL,
                    issuedAtUnixMs INTEGER NOT NULL,
                    expiresAtUnixMs INTEGER NOT NULL,
                    revokedAtUnixMs INTEGER,
                    provisionedAtUnixMs INTEGER NOT NULL,
                    PRIMARY KEY(nodeId)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_recipient_keys_encryptionKeyId " +
                    "ON recipient_keys (encryptionKeyId)",
            )
        }
    }
}
