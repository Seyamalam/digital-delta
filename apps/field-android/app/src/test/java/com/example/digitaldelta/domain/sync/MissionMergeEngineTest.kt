package com.example.digitaldelta.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionMergeEngineTest {
    private val engine = MissionMergeEngine()

    @Test
    fun `causally newer revision replaces the projection`() {
        val old = revision("edit-a1", MissionField.DESTINATION, "N3", mapOf("phone-a" to 1L), 100)
        val newer = revision("edit-a2", MissionField.DESTINATION, "N6", mapOf("phone-a" to 2L), 90)

        val result = engine.merge(old, newer) as MergeDecision.Applied

        assertEquals("N6", result.revision.value)
        assertEquals(ClockRelation.EQUAL, result.revision.clock.compare(newer.clock))
        assertFalse(result.concurrent)
    }

    @Test
    fun `concurrent destination edits require human review regardless of wall clock`() {
        val phoneA = revision("edit-a", MissionField.DESTINATION, "N3", mapOf("phone-a" to 2L), 500)
        val phoneB = revision("edit-b", MissionField.DESTINATION, "N6", mapOf("phone-b" to 2L), 100)

        val result = engine.merge(phoneA, phoneB) as MergeDecision.NeedsReview

        assertEquals(listOf("edit-a", "edit-b"), listOf(result.left.eventId, result.right.eventId))
        assertEquals(VectorClock(mapOf("phone-a" to 2L, "phone-b" to 2L)), result.mergedClock)
    }

    @Test
    fun `equal clocks with different safety values cannot silently pick a winner`() {
        val first = revision("edit-a", MissionField.PRIORITY, "P0", mapOf("phone-a" to 3L), 100)
        val second = revision("edit-b", MissionField.PRIORITY, "P2", mapOf("phone-a" to 3L), 900)

        val result = engine.merge(first, second)

        assertTrue(result is MergeDecision.NeedsReview)
    }

    @Test
    fun `concurrent description edits converge independent of arrival order`() {
        val phoneA = revision("edit-a", MissionField.DESCRIPTION, "Need saline", mapOf("phone-a" to 1L), 100)
        val phoneB = revision("edit-b", MissionField.DESCRIPTION, "Need saline urgently", mapOf("phone-b" to 1L), 100)

        val forward = engine.merge(phoneA, phoneB) as MergeDecision.Applied
        val reverse = engine.merge(phoneB, phoneA) as MergeDecision.Applied

        assertEquals(forward.revision, reverse.revision)
        assertEquals("Need saline urgently", forward.revision.value)
        assertTrue(forward.concurrent)
        assertEquals(64, forward.revision.clock.convergenceHash().length)
    }

    @Test
    fun `grow only identifiers and per replica stock deltas converge without loss`() {
        val receiptsA = GrowOnlySet<String>().add("receipt-a")
        val receiptsB = GrowOnlySet<String>().add("receipt-b").add("receipt-a")
        assertEquals(setOf("receipt-a", "receipt-b"), receiptsA.merge(receiptsB).values)

        val stockA = PnCounter().increment("phone-a", 12).decrement("phone-a", 2)
        val stockB = PnCounter().increment("phone-b", 7).decrement("phone-b", 1)
        val merged = stockA.merge(stockB)

        assertEquals(16L, merged.value)
        assertEquals(merged, stockB.merge(stockA))
    }

    @Test
    fun `observed assignment removal converges and cannot be resurrected by late delivery`() {
        val assigned = ObservedRemoveSet<String>()
            .add("boat-02", "phone-a:1")
            .add("drone-07", "phone-a:2")
        val removedOnA = assigned.remove("boat-02")
        val staleOnB = assigned.add("drone-07", "phone-a:2")

        val forward = removedOnA.merge(staleOnB)
        val reverse = staleOnB.merge(removedOnA)

        assertEquals(setOf("drone-07"), forward.values)
        assertEquals(forward, reverse)
        assertFalse(forward.contains("boat-02"))
        assertEquals(setOf("phone-a:1"), forward.tombstones)
    }

    @Test
    fun `concurrent unseen assignment survives removal of observed tags`() {
        val observed = ObservedRemoveSet<String>().add("boat-02", "phone-a:1")
        val removedOnA = observed.remove("boat-02")
        val concurrentlyReassignedOnB = observed.add("boat-02", "phone-b:1")

        val merged = removedOnA.merge(concurrentlyReassignedOnB)

        assertTrue(merged.contains("boat-02"))
        assertEquals(setOf("boat-02"), merged.values)
        assertEquals(setOf("phone-a:1"), merged.tombstones)
    }

    @Test
    fun `duplicate assignment operations are idempotent`() {
        val once = ObservedRemoveSet<String>().add("boat-02", "phone-a:1")
        val duplicate = once.add("boat-02", "phone-a:1")

        assertEquals(once, duplicate)
        assertEquals(once, once.merge(duplicate))
    }

    private fun revision(
        eventId: String,
        field: MissionField,
        value: String,
        clock: Map<String, Long>,
        occurredAtUnixMs: Long,
    ) = FieldRevision(
        eventId = eventId,
        missionId = "mission-sylhet-01",
        field = field,
        value = value,
        clock = VectorClock(clock),
        occurredAtUnixMs = occurredAtUnixMs,
    )
}
