package com.example.digitaldelta.service

import android.content.Context
import androidx.work.*
import com.example.digitaldelta.di.DigitalDeltaGraphEntryPoint
import com.example.digitaldelta.domain.observer.*
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.TimeUnit

class ObserverPublicationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val configuration = runCatching { ObserverSettings(applicationContext).load() }.getOrElse { return Result.failure() }
            ?: return Result.success()
        val graph = EntryPointAccessors.fromApplication(applicationContext, DigitalDeltaGraphEntryPoint::class.java)
        return runCatching { ObserverPublisher(graph.database(), HttpObservationTransport()) {
            val current = ObserverSettings(applicationContext).load()
            current?.destination == configuration.destination && current.token == configuration.token
        }.drain(configuration) }
            .fold({ if (it) Result.success() else Result.retry() }, { Result.retry() })
    }
}

object ObserverPublication {
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun schedule(context: Context) {
        if (!ObserverSettings(context).configured()) return
        WorkManager.getInstance(context).enqueueUniqueWork("observer-publish-now", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<ObserverPublicationWorker>().setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build())
    }
    fun periodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("observer-publish-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ObserverPublicationWorker>(15, TimeUnit.MINUTES).setConstraints(network).build())
    }
}
