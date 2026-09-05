package com.example.digitaldelta.domain.observer

import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.data.local.ObserverPublication
import com.example.digitaldelta.proto.v1.DomainEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

/** Per-node credential, provisioned out of band. Never included in logs or public state. */
class ObserverConfiguration(val endpoint: String, val sourceNodeId: String, val token: String) {
    val destination: String get() = "$endpoint|$sourceNodeId"
    companion object {
        fun parse(code: String, allowEmulatorHttp: Boolean = false): ObserverConfiguration {
            require(code.length in 1..4096)
            val value = JSONObject(code)
            require(value.keys().asSequence().toSet() == setOf("endpoint", "sourceNodeId", "token"))
            val uri = URI(value.getString("endpoint"))
            require(uri.userInfo == null && uri.query == null && uri.fragment == null && uri.host != null)
            require(uri.path == "/v1/observations")
            require(uri.scheme == "https" || (allowEmulatorHttp && uri.scheme == "http" && uri.host in setOf("127.0.0.1", "10.0.2.2", "localhost")))
            val source = value.getString("sourceNodeId")
            require(source.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,95}")))
            val token = value.getString("token")
            require(token.length in 32..512 && token.all { it.code in 33..126 })
            return ObserverConfiguration(uri.toASCIIString(), source, token)
        }
    }
}

/** Explicit allowlist; no cargo items, free text, signatures, keys or mesh bytes. */
object PublicObservationAdapter {
    fun encode(event: DomainEvent, source: String): ByteArray? {
        val fields = JSONObject()
        val kind = when {
            event.hasReliefRequestCreated() -> {
                val request = event.reliefRequestCreated
                fields.put("requestId", request.requestId).put("requesterNodeId", request.requesterNodeId)
                    .put("originNodeId", request.originNodeId).put("destinationNodeId", request.destinationNodeId)
                    .put("cargoCount", request.cargoCount)
                "reliefRequestCreated"
            }
            event.hasRoutePlanned() -> {
                val route = event.routePlanned
                fields.put("missionId", route.missionId).put("vehicleId", route.vehicleId).put("mode", route.mode.name)
                    .put("edgeIds", org.json.JSONArray(route.edgeIdsList)).put("etaMinutes", route.etaMinutes)
                    .put("riskAdjusted", route.riskAdjusted).put("explanationCode", route.explanationCode)
                "routePlanned"
            }
            event.hasSlaBreachPredicted() -> {
                val sla = event.slaBreachPredicted
                fields.put("missionId", sla.missionId).put("priority", sla.priority.name)
                    .put("baselineEtaMinutes", sla.baselineEtaMinutes).put("slowedEtaMinutes", sla.slowedEtaMinutes)
                    .put("slaMinutes", sla.slaMinutes).put("policyVersion", sla.policyVersion)
                "slaBreachPredicted"
            }
            else -> return null
        }
        val result = JSONObject().put("eventId", event.eventId).put("sourceNodeId", source).put("kind", kind)
            .put("occurredAtUnixMs", event.occurredAtUnixMs).put("simulated", event.simulated).put("presentation", fields)
        if (event.scenarioSeed.isNotBlank()) result.put("scenarioSeed", event.scenarioSeed)
        return result.toString().encodeToByteArray().also { require(it.size <= 16_384) }
    }
}

fun interface ObservationTransport { suspend fun publish(configuration: ObserverConfiguration, body: ByteArray): Int }

class HttpObservationTransport : ObservationTransport {
    override suspend fun publish(configuration: ObserverConfiguration, body: ByteArray): Int = withContext(Dispatchers.IO) {
        val connection = URI(configuration.endpoint).toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${configuration.token}")
            connection.setRequestProperty("X-Source-Node", configuration.sourceNodeId)
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            connection.responseCode
        } finally { connection.disconnect() }
    }
}

class ObserverPublisher(private val database: DeltaDatabase, private val transport: ObservationTransport, private val enabled: () -> Boolean = { true }) {
    /** Returns true only when drained; transient failures leave the ledger entry pending. */
    suspend fun drain(configuration: ObserverConfiguration, limit: Int = 40): Boolean {
        val authority = database.recipientKeyDao().findByNodeId(configuration.sourceNodeId) ?: return true
        val now = System.currentTimeMillis()
        if (authority.revokedAtUnixMs != null || authority.issuedAtUnixMs > now || authority.expiresAtUnixMs <= now) return true
        val rows = database.observerPublicationDao().pending(configuration.destination, limit)
        for (row in rows) {
            if (!enabled()) return true
            val current = database.recipientKeyDao().findByNodeId(configuration.sourceNodeId) ?: return true
            if (current.revokedAtUnixMs != null || current.expiresAtUnixMs <= System.currentTimeMillis() || current.identityId != authority.identityId) return true
            val encoded = runCatching {
                val event = DomainEvent.parseFrom(row.payloadBytes)
                if (event.actorIdentityId == authority.identityId) PublicObservationAdapter.encode(event, configuration.sourceNodeId) else null
            }
            if (encoded.isFailure) {
                database.observerPublicationDao().record(ObserverPublication(row.eventId, configuration.destination, "REJECTED_LOCAL_RECORD", now))
                continue
            }
            val body = encoded.getOrNull()
            val state = if (body == null) "FILTERED" else {
                val status = runCatching { transport.publish(configuration, body) }.getOrNull() ?: return false
                when (status) {
                    200, 201 -> "PUBLISHED"
                    400, 409, 413, 422 -> "REJECTED_$status"
                    else -> return false // Authentication, throttling, redirect and server errors preserve work.
                }
            }
            database.observerPublicationDao().record(ObserverPublication(row.eventId, configuration.destination, state, now))
        }
        return rows.size < limit
    }
}
