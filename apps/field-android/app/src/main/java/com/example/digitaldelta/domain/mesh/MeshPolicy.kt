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

enum class RelayRole {
    CLIENT_ONLY,
    RELAY_READY,
    RELAY_ACTIVE,
    RELAY_URGENT,
    RELAY_CONSERVE,
}

enum class RelayLinkQuality {
    UNKNOWN,
    GOOD,
    DEGRADED,
}

data class RelayRoleInput(
    val batteryPercent: Int,
    val pendingQueueDepth: Int,
    val connectedPeerCount: Int,
    val lastContactAgeMillis: Long?,
    val lastAcknowledgementRoundTripMillis: Long?,
    val urgentPending: Boolean,
)

data class RelayRoleSelection(
    val role: RelayRole,
    val linkQuality: RelayLinkQuality,
    val proximityRecent: Boolean,
)

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

    /**
     * Selects this phone's current mesh role from locally measurable signals. Nearby Connections
     * does not expose radio RSSI, so successful acknowledgement round-trip time is the link-quality
     * signal and authenticated contact recency is the proximity signal. Unknown telemetry stays
     * explicit instead of being invented.
     */
    fun selectRelayRole(input: RelayRoleInput): RelayRoleSelection {
        require(input.batteryPercent in 0..100)
        require(input.pendingQueueDepth >= 0)
        require(input.connectedPeerCount >= 0)
        require(input.lastContactAgeMillis == null || input.lastContactAgeMillis >= 0)
        require(input.lastAcknowledgementRoundTripMillis == null || input.lastAcknowledgementRoundTripMillis >= 0)

        val linkQuality = when (input.lastAcknowledgementRoundTripMillis) {
            null -> RelayLinkQuality.UNKNOWN
            in 0..2_500 -> RelayLinkQuality.GOOD
            else -> RelayLinkQuality.DEGRADED
        }
        val proximityRecent = input.lastContactAgeMillis?.let { it <= 120_000 } ?: false
        val role = when {
            input.urgentPending && input.pendingQueueDepth > 0 -> RelayRole.RELAY_URGENT
            input.batteryPercent < 15 && !input.urgentPending -> RelayRole.RELAY_CONSERVE
            input.pendingQueueDepth > 0 && input.connectedPeerCount > 0 -> RelayRole.RELAY_ACTIVE
            input.connectedPeerCount > 0 || proximityRecent -> RelayRole.RELAY_READY
            else -> RelayRole.CLIENT_ONLY
        }
        return RelayRoleSelection(role, linkQuality, proximityRecent)
    }
}
