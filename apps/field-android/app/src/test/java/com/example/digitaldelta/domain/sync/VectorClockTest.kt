package com.example.digitaldelta.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class VectorClockTest {
    @Test
    fun `independent offline edits are concurrent and merge without losing counters`() {
        val base = VectorClock.EMPTY.increment("phone-a")
        val left = base.increment("phone-a")
        val right = base.increment("phone-b")

        assertEquals(ClockRelation.CONCURRENT, left.compare(right))
        assertEquals(
            VectorClock(mapOf("phone-a" to 2L, "phone-b" to 1L)),
            left.merge(right),
        )
    }

    @Test
    fun `equal clocks have stable convergence hash`() {
        val first = VectorClock(mapOf("phone-b" to 3L, "phone-a" to 7L))
        val second = VectorClock(mapOf("phone-a" to 7L, "phone-b" to 3L))

        assertEquals(ClockRelation.EQUAL, first.compare(second))
        assertEquals(first.convergenceHash(), second.convergenceHash())
    }
}
