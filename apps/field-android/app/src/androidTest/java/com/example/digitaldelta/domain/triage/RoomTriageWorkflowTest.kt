package com.example.digitaldelta.domain.triage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.proto.v1.DomainEvent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTriageWorkflowTest {
    private lateinit var database: DeltaDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DeltaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun close() = database.close()

    @Test
    fun confirmationAppendsExactPreemptionEventBeforeShowingConfirmed() = runTest {
        val workflow = RoomTriageWorkflow(
            database = database,
            nowUnixMs = { 1_800_000_000_000 },
            eventId = { "preemption-event-1" },
        )
        val proposal = workflow.evaluate(200) as TriageWorkflowSnapshot.Proposed

        val confirmed = workflow.confirm(proposal, "coordinator-sylhet-01")

        assertEquals("preemption-event-1", confirmed.eventId)
        val operation = database.operationLogDao().forMission("mission-sylhet-01").single()
        val event = DomainEvent.parseFrom(operation.payloadBytes)
        assertEquals("cargo-medicine-p0", event.preemptionConfirmed.urgentCargoId)
        assertEquals("cargo-tarpaulin-p2", event.preemptionConfirmed.depositedCargoId)
        assertEquals("N3", event.preemptionConfirmed.waypointNodeId)
        assertEquals("coordinator-sylhet-01", event.preemptionConfirmed.confirmerIdentityId)
        assertEquals("triage-v2", event.preemptionConfirmed.policyVersion)
        assertEquals("SLA_BREACH_30_PERCENT", event.preemptionConfirmed.reasonCode)
        assertEquals(25, event.preemptionConfirmed.estimatedMinutesGained)
        assertEquals(true, event.simulated)

        val deposited = database.cargoAssignmentDao().find(
            missionId = "mission-sylhet-01",
            cargoId = "cargo-tarpaulin-p2",
        )
        requireNotNull(deposited)
        assertEquals("N3", deposited.assignedNodeId)
        assertEquals("DEPOSITED_FOR_REASSIGNMENT", deposited.state)
        assertEquals("P2", deposited.priorityCode)
        assertEquals("coordinator-sylhet-01", deposited.assignedByIdentityId)
        assertEquals("preemption-event-1", deposited.sourceEventId)
        assertEquals(64, deposited.convergenceHash.length)
    }

    @Test
    fun duplicateEventRollsBackAssignmentProjectionMutation() = runTest {
        val workflow = RoomTriageWorkflow(
            database = database,
            nowUnixMs = { 1_800_000_000_000 },
            eventId = { "preemption-event-atomic" },
        )
        val original = workflow.evaluate(200) as TriageWorkflowSnapshot.Proposed
        workflow.confirm(original, "coordinator-sylhet-01")

        val conflicting = original.copy(
            proposal = original.proposal.copy(waypointId = "N4"),
        )
        val failure = runCatching {
            workflow.confirm(conflicting, "coordinator-sylhet-01")
        }.exceptionOrNull()

        assertTrue(failure != null)
        assertEquals(
            "N3",
            database.cargoAssignmentDao().find(
                missionId = "mission-sylhet-01",
                cargoId = "cargo-tarpaulin-p2",
            )?.assignedNodeId,
        )
        assertEquals(1, database.operationLogDao().forMission("mission-sylhet-01").size)
    }
}
