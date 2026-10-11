package com.lhy.yumerin.data.local

import android.content.Context
import android.app.ActivityManager
import android.os.Build
import android.os.StatFs
import com.lhy.yumerin.core.model.EntityId
import com.lhy.yumerin.core.model.LocalModelArtifact
import com.lhy.yumerin.core.model.LocalModelState
import com.lhy.yumerin.data.background.BackgroundWorkScheduler
import com.lhy.yumerin.domain.repository.LocalModelGateway
import com.lhy.yumerin.domain.repository.ModelProfileRepository
import java.io.File

class AndroidLocalModelGateway(
    private val repository: ModelProfileRepository,
    private val modelsDirectory: File,
    private val context: Context,
    private val localAiService: LocalAiService = LocalAiService.getInstance()
) : LocalModelGateway {
    private val manager = LocalModelManager(repository, modelsDirectory)

    override suspend fun registerCatalogEntry(artifact: LocalModelArtifact) {
        require(artifact.sourceUrl?.startsWith("https://", ignoreCase = true) != false) {
            "本地模型目录只允许 HTTPS 来源；也可以直接导入本地 .litertlm 文件"
        }
        manager.registerCatalogEntry(artifact)
    }

    override suspend fun importArtifact(artifact: LocalModelArtifact, sourcePath: String): LocalModelArtifact {
        require(artifact.licenseAcknowledged) { "请先确认模型许可证和来源" }
        require(artifact.fileName.endsWith(".litertlm", ignoreCase = true)) {
            "LiteRT-LM 模型必须使用 .litertlm 文件"
        }
        val source = File(sourcePath)
        require(source.isFile) { "找不到待导入的模型文件" }
        if (artifact.fileSizeBytes > 0L) {
            require(source.length() == artifact.fileSizeBytes) { "模型文件大小与目录项不一致" }
        }
        require(artifact.sha256.length == 64) { "目录项缺少有效 SHA-256" }
        val availableBytes = StatFs(source.parentFile?.path ?: context.filesDir.path).availableBytes
        require(availableBytes >= source.length() * 2) { "设备可用空间不足以安全安装此模型" }
        return runCatching {
            manager.importVerifiedModel(artifact.copy(state = LocalModelState.VERIFYING), source)
        }.getOrElse { error ->
            repository.saveLocalArtifact(
                artifact.copy(
                    state = LocalModelState.FAILED,
                    diagnosticReason = error.message ?: "模型校验失败",
                    updatedAt = System.currentTimeMillis()
                )
            )
            throw error
        }
    }

    override suspend fun startDownload(artifactId: EntityId, allowMetered: Boolean) {
        val artifact = requireNotNull(repository.getLocalArtifact(artifactId)) { "找不到本地模型" }
        require(artifact.licenseAcknowledged) { "请先确认模型许可证和隐私信息" }
        BackgroundWorkScheduler.enqueueLocalModelDownload(context, artifactId, allowMetered)
    }

    override suspend fun pauseDownload(artifactId: EntityId) {
        BackgroundWorkScheduler.pauseLocalModelDownload(context, artifactId)
    }

    override suspend fun cancelDownload(artifactId: EntityId) {
        BackgroundWorkScheduler.cancelLocalModelDownload(context, artifactId)
        repository.getLocalArtifact(artifactId)?.let { artifact ->
            File(modelsDirectory, ".${artifact.id}-${File(artifact.fileName).name}.part").delete()
            repository.saveLocalArtifact(
                artifact.copy(
                    state = LocalModelState.CATALOGUED,
                    diagnosticReason = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun activate(artifactId: EntityId): LocalModelArtifact {
        val artifact = requireNotNull(repository.getLocalArtifact(artifactId)) { "找不到本地模型" }
        require(artifact.licenseAcknowledged) { "请先确认模型许可证和来源" }
        return runCatching {
            checkCompatibility(artifact)
            localAiService.probe(artifact)
            manager.activate(artifactId)
        }.getOrElse { error ->
            repository.saveLocalArtifact(
                artifact.copy(
                    state = LocalModelState.FAILED,
                    diagnosticReason = error.message ?: "设备兼容性检查失败",
                    updatedAt = System.currentTimeMillis()
                )
            )
            throw error
        }
    }

    override suspend fun remove(artifactId: EntityId) {
        manager.remove(artifactId)
    }

    private fun checkCompatibility(artifact: LocalModelArtifact) {
        require(artifact.localPath?.endsWith(".litertlm", ignoreCase = true) == true) {
            "模型文件格式不受支持"
        }
        require(Build.VERSION.SDK_INT >= 29) { "当前 Android 版本不满足本地模型要求" }
        val modelFile = File(requireNotNull(artifact.localPath))
        require(modelFile.isFile) { "找不到已安装的模型文件" }
        val modelRoot = modelsDirectory.canonicalFile.toPath()
        require(modelFile.canonicalFile.toPath().startsWith(modelRoot)) { "模型文件不在应用私有目录中" }
        if (artifact.fileSizeBytes > 0L) {
            require(modelFile.length() == artifact.fileSizeBytes) { "模型文件大小不匹配" }
        }
        val availableStorage = StatFs(modelsDirectory.path).availableBytes
        require(availableStorage >= modelFile.length() * 2L) { "可用存储不足以安全加载模型" }
        val memoryInfo = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(memoryInfo)
        require(memoryInfo.availMem >= modelFile.length().coerceAtLeast(1L)) { "可用内存不足以加载模型" }
        checkDeclaredRequirements(artifact.deviceRequirements)
        if (artifact.backend.trim().equals("gpu", ignoreCase = true)) {
            require(hasGpuRuntime()) { "当前设备没有可用的 GPU 运行时" }
        }
    }

    private fun checkDeclaredRequirements(requirements: String) {
        if (requirements.isBlank()) return
        Regex("android\\s*>=\\s*(\\d+)", RegexOption.IGNORE_CASE).find(requirements)?.let {
            require(Build.VERSION.SDK_INT >= it.groupValues[1].toInt()) { "Android 版本不满足模型要求" }
        }
        Regex("ram\\s*>=\\s*(\\d+)\\s*(gb|mb)", RegexOption.IGNORE_CASE).find(requirements)?.let {
            val requestedBytes = it.groupValues[1].toLong() * if (it.groupValues[2].equals("gb", true)) {
                1_073_741_824L
            } else {
                1_048_576L
            }
            val memoryInfo = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(memoryInfo)
            require(memoryInfo.totalMem >= requestedBytes) { "设备内存不满足模型要求" }
        }
        Regex("abi\\s*[:=]\\s*([A-Za-z0-9_,.\\- ]+)", RegexOption.IGNORE_CASE).find(requirements)?.let {
            val accepted = it.groupValues[1].split(',', ' ', ';').filter(String::isNotBlank)
            require(accepted.any { abi -> Build.SUPPORTED_ABIS.contains(abi) }) { "设备 ABI 不满足模型要求" }
        }
    }

    private fun hasGpuRuntime(): Boolean = runCatching {
        val display = android.opengl.EGL14.eglGetDisplay(android.opengl.EGL14.EGL_DEFAULT_DISPLAY)
        if (display == android.opengl.EGL14.EGL_NO_DISPLAY) return@runCatching false
        val major = IntArray(1)
        val minor = IntArray(1)
        val initialized = android.opengl.EGL14.eglInitialize(display, major, 0, minor, 0)
        if (initialized) android.opengl.EGL14.eglTerminate(display)
        initialized
    }.getOrDefault(false)
}
