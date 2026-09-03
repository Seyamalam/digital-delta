package com.example.digitaldelta.domain.mesh

data class RelayEnvelope(
    val messageId: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val hopCount: Int,
    val hopLimit: Int,
) {
    init {
        require(messageId.isNotBlank())
        require(expiresAtMillis >= createdAtMillis)
        require(hopCount >= 0)
        require(hopLimit > 0)
    }
}

enum class RelayDecision {
    FORWARD,
    REJECT_DUPLICATE,
    REJECT_EXPIRED,
    REJECT_HOP_LIMIT,
}

class SeenMessageIndex {
    private val ids = linkedSetOf<String>()

    fun record(messageId: String): Boolean = ids.add(messageId)

    fun contains(messageId: String): Boolean = messageId in ids
}

class MeshPolicy(
    private val normalIntervalMillis: Long = 10_000L,
    private val urgentIntervalMillis: Long = 5_000L,
) {
    fun broadcastIntervalMillis(batteryPercent: Int, urgent: Boolean): Long {
        require(batteryPercent in 0..100)
        val baseInterval = if (urgent) urgentIntervalMillis else normalIntervalMillis
        return if (batteryPercent < 30) {
            (baseInterval / 0.40).toLong()
        } else {
            baseInterval
        }
    }

    fun evaluate(
        envelope: RelayEnvelope,
        nowMillis: Long,
        seenMessages: SeenMessageIndex,
    ): RelayDecision = when {
        seenMessages.contains(envelope.messageId) -> RelayDecision.REJECT_DUPLICATE
        nowMillis > envelope.expiresAtMillis -> RelayDecision.REJECT_EXPIRED
        envelope.hopCount >= envelope.hopLimit -> RelayDecision.REJECT_HOP_LIMIT
        else -> RelayDecision.FORWARD
    }
}
