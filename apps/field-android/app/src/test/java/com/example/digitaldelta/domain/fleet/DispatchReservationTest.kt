package com.example.digitaldelta.domain.fleet

import com.example.digitaldelta.domain.routing.VehicleType
import org.junit.Assert.*
import org.junit.Test

class DispatchReservationTest {
    @Test fun `reservation round trips hold reason without implying a deposit`() {
        val held = DispatchReservation(DispatchReservation.HOLD, "RLY-01", VehicleType.TRUCK, "urgent-1")
        assertEquals(held, DispatchReservation.decode(held.encode()))
        assertEquals("urgent-1", DispatchReservation.decode(held.encode()).preemptedByMissionId)
        val ready = DispatchReservation(DispatchReservation.READY, "RLY-01", VehicleType.BOAT)
        assertEquals(ready, DispatchReservation.decode(ready.encode()))
        assertEquals(listOf("N1", "RLY-01", "N6"), dispatchCustodyPath("N1", "N6", "N1>N5>N6", ready.encode()))
        assertEquals(listOf("N1", "N5", "N6"), dispatchCustodyPath("N1", "N6", "N1>N5>N6", null))
    }
    @Test fun `malformed reservations and drone dispatch are rejected`() {
        listOf("DEPOSITED|RLY-01|TRUCK", "READY|RLY-01|DRONE", "READY||TRUCK", "READY|RLY-01|TRUCK|urgent-1",
            "HOLD_AT_ORIGIN|RLY-01|TRUCK|", "READY|RLY-01|TRUCK|extra|extra").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { DispatchReservation.decode(value) }
        }
    }
}
