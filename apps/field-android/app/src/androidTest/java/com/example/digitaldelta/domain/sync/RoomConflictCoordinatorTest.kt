package com.example.digitaldelta.domain.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.digitaldelta.data.local.DeltaDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomConflictCoordinatorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "conflict-coordinator-test"
    private var database: DeltaDatabase? = null

    @After
    fun cleanup() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun concurrentDestinationEditsPersistAcrossRestartAndResolveWithOneProjection() = runTest {
        val firstDatabase = openDatabase()
        val firstCoordinator = RoomConflictCoordinator(firstDatabase) { 1_800_000_000_000 }

        val raised = firstCoordinator.simulateDestinationConflict() as MissionConflictSnapshot.Open
        assertEquals("N3", raised.leftValue)
        assertEquals("N6", raised.rightValue)
        assertEquals(3, firstDatabase.operationLogDao().forMission(raised.missionId).size)

        firstDatabase.close()
        database = null
        val restartedDatabase = openDatabase()
        val restartedCoordinator = RoomConflictCoordinator(restartedDatabase) { 1_800_000_001_000 }
        val restored = restartedCoordinator.snapshot() as MissionConflictSnapshot.Open
        assertEquals(raised.conflictId, restored.conflictId)

        val resolved = restartedCoordinator.resolve(
            conflictId = restored.conflictId,
            selectedSide = ConflictSide.RIGHT,
            resolverIdentityId = "coordinator-sylhet-01",
        ) as MissionConflictSnapshot.Resolved

        assertEquals("N6", resolved.selectedValue)
        assertEquals(64, resolved.convergenceHash.length)
        assertEquals("N6", restartedDatabase.missionProjectionDao().find(resolved.missionId, "DESTINATION")?.value)
        assertEquals(4, restartedDatabase.operationLogDao().forMission(resolved.missionId).size)
        assertTrue(restartedCoordinator.snapshot() is MissionConflictSnapshot.Resolved)

        val repeated = restartedCoordinator.simulateDestinationConflict() as MissionConflictSnapshot.Open
        assertTrue(repeated.conflictId != resolved.conflictId)
        assertEquals(7, restartedDatabase.operationLogDao().forMission(resolved.missionId).size)
    }

    private fun openDatabase(): DeltaDatabase = Room.databaseBuilder(
        context,
        DeltaDatabase::class.java,
        databaseName,
    ).build().also { database = it }
}
