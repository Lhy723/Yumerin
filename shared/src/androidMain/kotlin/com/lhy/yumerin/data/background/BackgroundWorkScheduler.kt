package com.lhy.yumerin.data.background

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object BackgroundWorkScheduler {
    private const val WORK_NAME = "yumerin_companion_maintenance"
    private const val LOCAL_MODEL_PREFIX = "yumerin_local_model_"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<CompanionMaintenanceWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun enqueueLocalModelDownload(context: Context, artifactId: String, allowMetered: Boolean = false) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (allowMetered) NetworkType.CONNECTED else NetworkType.UNMETERED)
            .build()
        val request = OneTimeWorkRequestBuilder<LocalModelDownloadWorker>()
            .setInputData(workDataOf(LocalModelDownloadWorker.KEY_ARTIFACT_ID to artifactId))
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            LOCAL_MODEL_PREFIX + artifactId,
            androidx.work.ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun pauseLocalModelDownload(context: Context, artifactId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(LOCAL_MODEL_PREFIX + artifactId)
    }

    fun cancelLocalModelDownload(context: Context, artifactId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(LOCAL_MODEL_PREFIX + artifactId)
    }
}
