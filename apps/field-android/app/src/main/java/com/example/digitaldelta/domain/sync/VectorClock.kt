package com.example.digitaldelta.domain.sync

import java.security.MessageDigest

enum class ClockRelation {
    BEFORE,
    AFTER,
    EQUAL,
    CONCURRENT,
}

data class VectorClock(
    val counters: Map<String, Long>,
) {
    init {
        require(counters.keys.none(String::isBlank)) { "Replica IDs cannot be blank" }
        require(counters.values.all { it >= 0L }) { "Vector clock counters cannot be negative" }
    }

    fun increment(replicaId: String): VectorClock {
        require(replicaId.isNotBlank())
        return copy(counters = counters + (replicaId to ((counters[replicaId] ?: 0L) + 1L)))
    }

    fun merge(other: VectorClock): VectorClock {
        val replicas = counters.keys + other.counters.keys
        return VectorClock(replicas.associateWith { replica ->
            maxOf(counters[replica] ?: 0L, other.counters[replica] ?: 0L)
        })
    }

    fun compare(other: VectorClock): ClockRelation {
        val replicas = counters.keys + other.counters.keys
        var lower = false
        var higher = false
        replicas.forEach { replica ->
            val ours = counters[replica] ?: 0L
            val theirs = other.counters[replica] ?: 0L
            if (ours < theirs) lower = true
            if (ours > theirs) higher = true
        }
        return when {
            lower && higher -> ClockRelation.CONCURRENT
            lower -> ClockRelation.BEFORE
            higher -> ClockRelation.AFTER
            else -> ClockRelation.EQUAL
        }
    }

    fun convergenceHash(): String {
        val canonical = counters.toSortedMap().entries.joinToString(separator = "\n") { (id, value) ->
            "$id:$value"
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    companion object {
        val EMPTY = VectorClock(emptyMap())
    }
}
