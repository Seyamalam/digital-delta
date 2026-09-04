package com.example.digitaldelta.domain.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.digitaldelta.data.local.DeltaDatabase
import com.example.digitaldelta.proto.v1.*
import com.google.protobuf.ByteString
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Domain application seam; signed envelope verification is exercised separately. */
@RunWith(AndroidJUnit4::class)
class ReceivedEventApplicationTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), DeltaDatabase::class.java).build()
    private val application = ReceivedEventApplication(database)
    private val origin = Envelope.newBuilder().setSenderNodeId("N4")
        .setSenderCredential(IdentityProvisioningCredential.newBuilder().setClaims(
            IdentityProvisioningClaims.newBuilder().setIdentityId("clinic-1").setRole(IdentityRole.IDENTITY_ROLE_CLINIC),
        )).build()
    private fun request() = DomainEvent.newBuilder().setSchemaVersion(1).setEventId("request-created")
        .setActorIdentityId("clinic-1").setOccurredAtUnixMs(1_800_000_000_000)
        .setReliefRequestCreated(ReliefRequestCreated.newBuilder().setRequestId("request-1")
            .setRequesterNodeId("N4").setDestinationNodeId("N6")
            .addCargo(CargoItem.newBuilder().setItemCode("medicine").setQuantity(5).setPriorityValue(1))
            .addCargo(CargoItem.newBuilder().setItemCode("tarpaulin").setQuantity(8).setPriorityValue(3))).build()
    @After fun close() = database.close()

    @Test fun requestIsAtomicIdempotentAndDoesNotCountTarpaulinsAsMedicine() = runTest {
        assertTrue(application.apply(request(), origin, "N6"))
        assertTrue(application.apply(request(), origin, "N6"))
        assertEquals(1, database.operationLogDao().forMission("request-1").size)
        assertEquals("5", database.missionProjectionDao().find("request-1", "MEDICAL_QUANTITY")?.value)
        assertEquals("N6", database.missionProjectionDao().find("request-1", "DESTINATION")?.value)
    }

    @Test fun actorMismatchAndUnknownMissionUpdatesDoNotWrite() = runTest {
        assertTrue(runCatching { application.apply(request().toBuilder().setActorIdentityId("someone-else").build(), origin, "N6") }.isFailure)
        assertTrue(runCatching { application.apply(update("unknown", "clinic-1"), origin, "N6") }.isFailure)
        assertTrue(database.operationLogDao().forMission("request-1").isEmpty())
    }

    @Test fun unrelatedClinicCannotEditAnotherClinicsMission() = runTest {
        application.apply(request(), origin, "N6")
        val stranger = origin.toBuilder().setSenderNodeId("N5").setSenderCredential(
            origin.senderCredential.toBuilder().setClaims(origin.senderCredential.claims.toBuilder().setIdentityId("clinic-2")),
        ).build()
        assertTrue(runCatching { application.apply(update("request-1", "clinic-2"), stranger, "N6") }.isFailure)
        assertEquals("1", database.missionProjectionDao().find("request-1", "PRIORITY")?.value)
        assertEquals(1, database.operationLogDao().forMission("request-1").size)
    }

    private fun update(missionId: String, actor: String) = DomainEvent.newBuilder().setSchemaVersion(1)
        .setEventId("priority-update").setActorIdentityId(actor).setOccurredAtUnixMs(1_800_000_000_001)
        .setMissionFieldUpdated(MissionFieldUpdated.newBuilder().setMissionId(missionId).setFieldCode("PRIORITY")
            .setValue(ByteString.copyFromUtf8("2")).setVectorClock(com.example.digitaldelta.proto.v1.VectorClock.newBuilder()
                .addEntries(VectorClockEntry.newBuilder().setReplicaId("N4").setCounter(2)))).build()
}
