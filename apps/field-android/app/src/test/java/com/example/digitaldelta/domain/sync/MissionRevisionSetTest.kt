package com.example.digitaldelta.domain.sync

import org.junit.Assert.*
import org.junit.Test

class MissionRevisionSetTest {
    @Test fun threeConcurrentWritersHaveTheSameFrontierAndConflictPairsInEveryOrder() {
        val revisions = listOf("A", "B", "C").mapIndexed { index, node ->
            FieldRevision(node, "mission", MissionField.PRIORITY, (index + 1).toString(), VectorClock(mapOf("origin" to 1, node to 1)), 10)
        }
        val expected = projectRevisions(revisions)
        assertEquals(3, expected.conflicts.count { it.active })
        for (a in revisions) for (b in revisions - a) {
            val order = listOf(a, b) + (revisions - a - b)
            assertEquals(expected, projectRevisions(order))
            val resolved = FieldRevision("resolution", "mission", MissionField.PRIORITY, "2", expected.revision.clock.increment("coordinator"), 20)
            val result = projectRevisions(order + resolved)
            assertEquals("2", result.revision.value)
            assertFalse(result.conflicts.any { it.active })
            assertEquals(expected.conflicts.map { it.left.eventId to it.right.eventId }, result.conflicts.map { it.left.eventId to it.right.eventId })
        }
    }
}
