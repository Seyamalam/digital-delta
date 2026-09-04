package com.example.digitaldelta.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.digitaldelta.di.DigitalDeltaGraphEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class MeshMaintenanceWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val graph = EntryPointAccessors.fromApplication(
            applicationContext,
            DigitalDeltaGraphEntryPoint::class.java,
        )
        val now = System.currentTimeMillis()
        val database = graph.database()
        database.outboxDao().deadLetterExpired(now)
        database.seenMessageDao().pruneExpired(now)
        val localNodeId = graph.deviceProfileRepository().profile.first().nodeId
        val batch = graph.credentialRevocationInboxProcessor().process(localNodeId)
        val output = Data.Builder()
            .putInt("applied", batch.applied)
            .putInt("rejected", batch.rejected)
            .putInt("deferred", batch.deferred)
            .putInt("retry", batch.retry)
            .build()
        return if (batch.retry > 0) Result.retry() else Result.success(output)
    }
}

object MeshMaintenance {
    private const val IMMEDIATE_WORK = "digital-delta-mesh-maintenance-now"
    private const val PERIODIC_WORK = "digital-delta-mesh-maintenance-periodic"

    fun scheduleNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<MeshMaintenanceWorker>().build(),
        )
    }

    fun schedulePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MeshMaintenanceWorker>(15, TimeUnit.MINUTES).build(),
        )
    }
}
