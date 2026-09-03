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

@Entity(
    tableName = "mesh_inbox",
    indices = [Index(value = ["receivedAtUnixMs"]), Index(value = ["recipientNodeId"])],
)
data class MeshInboxEntity(
    @PrimaryKey val messageId: String,
    val wireBytes: ByteArray,
    val senderNodeId: String,
    val recipientNodeId: String,
    val expiresAtUnixMs: Long,
    val hopCount: Int,
    val hopLimit: Int,
    val receivedAtUnixMs: Long,
)

@Entity(tableName = "seen_messages", indices = [Index(value = ["expiresAtUnixMs"])])
data class SeenMessageEntity(
    @PrimaryKey val messageId: String,
    val expiresAtUnixMs: Long,
    val firstSeenAtUnixMs: Long,
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

@Entity(
    tableName = "mission_projections",
    primaryKeys = ["missionId", "fieldCode"],
    indices = [Index(value = ["missionId"])],
)
data class MissionProjectionEntity(
    val missionId: String,
    val fieldCode: String,
    val value: String,
    val vectorClockBytes: ByteArray,
    val sourceEventId: String,
    val updatedAtUnixMs: Long,
    val convergenceHash: String,
)

@Entity(
    tableName = "conflicts",
    indices = [Index(value = ["missionId", "state", "createdAtUnixMs"])],
)
data class ConflictEntity(
    @PrimaryKey val conflictId: String,
    val missionId: String,
    val fieldCode: String,
    val leftEventId: String,
    val leftValue: String,
    val leftClockBytes: ByteArray,
    val rightEventId: String,
    val rightValue: String,
    val rightClockBytes: ByteArray,
    val mergedClockBytes: ByteArray,
    val state: String,
    val selectedValue: String?,
    val resolverIdentityId: String?,
    val reasonCode: String?,
    val createdAtUnixMs: Long,
    val resolvedAtUnixMs: Long?,
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
        "SELECT EXISTS(SELECT 1 FROM mesh_outbox " +
            "WHERE state = 'PENDING' AND priority = 1 AND expiresAtUnixMs > :nowUnixMs)",
    )
    suspend fun hasUrgentPending(nowUnixMs: Long): Boolean

    @Query("SELECT * FROM mesh_outbox WHERE messageId = :messageId LIMIT 1")
    suspend fun find(messageId: String): MeshEnvelopeEntity?

    @Query(
        "UPDATE mesh_outbox SET state = 'PENDING', nextAttemptAtUnixMs = :nowUnixMs " +
            "WHERE state = 'IN_FLIGHT'",
    )
    suspend fun recoverInFlight(nowUnixMs: Long): Int

    @Query("UPDATE mesh_outbox SET state = 'IN_FLIGHT' WHERE messageId = :messageId AND state = 'PENDING'")
    suspend fun markInFlight(messageId: String): Int

    @Query(
        "UPDATE mesh_outbox SET state = 'PENDING', attemptCount = attemptCount + 1, " +
            "nextAttemptAtUnixMs = :nextAttemptAtUnixMs WHERE messageId = :messageId",
    )
    suspend fun scheduleRetry(messageId: String, nextAttemptAtUnixMs: Long): Int

    @Query(
        "UPDATE mesh_outbox SET state = 'DEAD_LETTER' " +
            "WHERE state IN ('PENDING', 'IN_FLIGHT') AND expiresAtUnixMs <= :nowUnixMs",
    )
    suspend fun deadLetterExpired(nowUnixMs: Long): Int

    @Query("UPDATE mesh_outbox SET state = 'DEAD_LETTER' WHERE messageId = :messageId")
    suspend fun markDeadLetter(messageId: String): Int

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
interface MeshInboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(envelope: MeshInboxEntity): Long

    @Query("SELECT * FROM mesh_inbox WHERE messageId = :messageId LIMIT 1")
    suspend fun find(messageId: String): MeshInboxEntity?

    @Query("SELECT COUNT(*) FROM mesh_inbox")
    suspend fun count(): Int
}

@Dao
interface SeenMessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun claim(message: SeenMessageEntity): Long

    @Query("DELETE FROM seen_messages WHERE expiresAtUnixMs <= :nowUnixMs")
    suspend fun pruneExpired(nowUnixMs: Long): Int
}

@Dao
interface NonceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun claim(nonce: UsedNonceEntity): Long

    @Query("SELECT COUNT(*) FROM used_nonces WHERE nonceSha256 = :nonceSha256")
    suspend fun count(nonceSha256: String): Int
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

@Dao
interface MissionProjectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(projection: MissionProjectionEntity)

    @Query("SELECT * FROM mission_projections WHERE missionId = :missionId AND fieldCode = :fieldCode LIMIT 1")
    suspend fun find(missionId: String, fieldCode: String): MissionProjectionEntity?

    @Query("SELECT * FROM mission_projections WHERE missionId = :missionId ORDER BY fieldCode ASC")
    suspend fun forMission(missionId: String): List<MissionProjectionEntity>

    @Query("UPDATE mission_projections SET convergenceHash = :hash WHERE missionId = :missionId")
    suspend fun updateConvergenceHash(missionId: String, hash: String)
}

@Dao
interface ConflictDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(conflict: ConflictEntity)

    @Query("SELECT * FROM conflicts WHERE conflictId = :conflictId LIMIT 1")
    suspend fun find(conflictId: String): ConflictEntity?

    @Query("SELECT * FROM conflicts ORDER BY createdAtUnixMs DESC, conflictId DESC LIMIT 1")
    suspend fun latest(): ConflictEntity?

    @Query(
        "SELECT * FROM conflicts WHERE missionId = :missionId " +
            "ORDER BY createdAtUnixMs DESC, conflictId DESC LIMIT 1",
    )
    suspend fun latestForMission(missionId: String): ConflictEntity?

    @Query("SELECT COUNT(*) FROM conflicts WHERE missionId = :missionId")
    suspend fun countForMission(missionId: String): Int

    @Query(
        "UPDATE conflicts SET state = 'RESOLVED', selectedValue = :selectedValue, " +
            "resolverIdentityId = :resolverIdentityId, reasonCode = :reasonCode, " +
            "resolvedAtUnixMs = :resolvedAtUnixMs WHERE conflictId = :conflictId AND state = 'OPEN'",
    )
    suspend fun resolve(
        conflictId: String,
        selectedValue: String,
        resolverIdentityId: String,
        reasonCode: String,
        resolvedAtUnixMs: Long,
    ): Int
}

@Database(
    entities = [
        MeshEnvelopeEntity::class,
        UsedNonceEntity::class,
        OperationEntity::class,
        RecipientKeyEntity::class,
        MeshInboxEntity::class,
        SeenMessageEntity::class,
        MissionProjectionEntity::class,
        ConflictEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class DeltaDatabase : RoomDatabase() {
    abstract fun outboxDao(): OutboxDao
    abstract fun nonceDao(): NonceDao
    abstract fun operationLogDao(): OperationLogDao
    abstract fun recipientKeyDao(): RecipientKeyDao
    abstract fun meshInboxDao(): MeshInboxDao
    abstract fun seenMessageDao(): SeenMessageDao
    abstract fun missionProjectionDao(): MissionProjectionDao
    abstract fun conflictDao(): ConflictDao
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

    val VERSION_2_TO_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS mesh_inbox (
                    messageId TEXT NOT NULL,
                    wireBytes BLOB NOT NULL,
                    senderNodeId TEXT NOT NULL,
                    recipientNodeId TEXT NOT NULL,
                    expiresAtUnixMs INTEGER NOT NULL,
                    hopCount INTEGER NOT NULL,
                    hopLimit INTEGER NOT NULL,
                    receivedAtUnixMs INTEGER NOT NULL,
                    PRIMARY KEY(messageId)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_mesh_inbox_receivedAtUnixMs " +
                    "ON mesh_inbox (receivedAtUnixMs)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_mesh_inbox_recipientNodeId " +
                    "ON mesh_inbox (recipientNodeId)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS seen_messages (
                    messageId TEXT NOT NULL,
                    expiresAtUnixMs INTEGER NOT NULL,
                    firstSeenAtUnixMs INTEGER NOT NULL,
                    PRIMARY KEY(messageId)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_seen_messages_expiresAtUnixMs " +
                    "ON seen_messages (expiresAtUnixMs)",
            )
        }
    }

    val VERSION_3_TO_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS mission_projections (
                    missionId TEXT NOT NULL,
                    fieldCode TEXT NOT NULL,
                    value TEXT NOT NULL,
                    vectorClockBytes BLOB NOT NULL,
                    sourceEventId TEXT NOT NULL,
                    updatedAtUnixMs INTEGER NOT NULL,
                    convergenceHash TEXT NOT NULL,
                    PRIMARY KEY(missionId, fieldCode)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_mission_projections_missionId " +
                    "ON mission_projections (missionId)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS conflicts (
                    conflictId TEXT NOT NULL,
                    missionId TEXT NOT NULL,
                    fieldCode TEXT NOT NULL,
                    leftEventId TEXT NOT NULL,
                    leftValue TEXT NOT NULL,
                    leftClockBytes BLOB NOT NULL,
                    rightEventId TEXT NOT NULL,
                    rightValue TEXT NOT NULL,
                    rightClockBytes BLOB NOT NULL,
                    mergedClockBytes BLOB NOT NULL,
                    state TEXT NOT NULL,
                    selectedValue TEXT,
                    resolverIdentityId TEXT,
                    reasonCode TEXT,
                    createdAtUnixMs INTEGER NOT NULL,
                    resolvedAtUnixMs INTEGER,
                    PRIMARY KEY(conflictId)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_conflicts_missionId_state_createdAtUnixMs " +
                    "ON conflicts (missionId, state, createdAtUnixMs)",
            )
        }
    }
}
