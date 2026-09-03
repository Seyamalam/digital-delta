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

@Database(
    entities = [MeshEnvelopeEntity::class, UsedNonceEntity::class, OperationEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class DeltaDatabase : RoomDatabase() {
    abstract fun outboxDao(): OutboxDao
    abstract fun nonceDao(): NonceDao
    abstract fun operationLogDao(): OperationLogDao
}
