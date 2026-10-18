package com.noam.kidtube.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.noam.kidtube.app.Wiring
import java.util.concurrent.TimeUnit

/** The daily RSS refresh. Deep syncs are started from the admin UI, never from here. */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val graph = Wiring.graph(applicationContext)
        val results = graph.catalogSync.refreshAll()
        graph.watchLedger.rotate()
        return if (results.values.any { it.isFailure }) Result.retry() else Result.success()
    }

    companion object {
        private const val NAME = "catalog-refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
