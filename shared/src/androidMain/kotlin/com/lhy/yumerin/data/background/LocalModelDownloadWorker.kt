package com.lhy.yumerin.data.background

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lhy.yumerin.data.local.LocalModelManager
import com.lhy.yumerin.data.room.createAndroidAppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalModelDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val artifactId = inputData.getString(KEY_ARTIFACT_ID) ?: return@withContext Result.failure()
        val graph = createAndroidAppGraph(applicationContext)
        val artifact = graph.models.getLocalArtifact(artifactId) ?: return@withContext Result.failure()
        val manager = LocalModelManager(
            graph.models,
            File(applicationContext.filesDir, "models")
        )
        setForeground(createForegroundInfo(0, artifact.fileSizeBytes))
        runCatching {
            manager.download(artifact) { downloaded, total ->
                setProgress(workDataOf("downloaded" to downloaded, "total" to total))
                setForeground(createForegroundInfo(downloaded, total))
            }
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                if (error.message?.contains("checksum", ignoreCase = true) == true) {
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        )
    }

    private fun createForegroundInfo(downloaded: Long, total: Long): ForegroundInfo {
        val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.LOCAL_MODEL_DOWNLOAD)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在准备本地模型")
            .setContentText(if (total > 0) "$progress%" else "下载中")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, total <= 0)
            .build()
        return ForegroundInfo(2001, notification)
    }

    companion object {
        const val KEY_ARTIFACT_ID = "artifact_id"
    }
}
