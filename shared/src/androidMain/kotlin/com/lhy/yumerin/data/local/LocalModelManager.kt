package com.lhy.yumerin.data.local

import com.lhy.yumerin.core.model.LocalModelArtifact
import com.lhy.yumerin.core.model.LocalModelState
import com.lhy.yumerin.core.model.ModelProfile
import com.lhy.yumerin.domain.repository.ModelProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class LocalModelManager(
    private val repository: ModelProfileRepository,
    private val modelsDirectory: File
) {
    suspend fun registerCatalogEntry(artifact: LocalModelArtifact) {
        repository.saveLocalArtifact(artifact.copy(state = LocalModelState.CATALOGUED))
    }

    suspend fun markDownloading(artifact: LocalModelArtifact) {
        repository.saveLocalArtifact(artifact.copy(state = LocalModelState.DOWNLOADING, updatedAt = System.currentTimeMillis()))
    }

    suspend fun download(
        artifact: LocalModelArtifact,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): LocalModelArtifact = withContext(Dispatchers.IO) {
        val sourceUrl = requireNotNull(artifact.sourceUrl) { "Catalog entry has no download source" }
        modelsDirectory.mkdirs()
        val installedFileName = artifact.installedFileName()
        val partial = File(modelsDirectory, ".$installedFileName.part")
        val existingBytes = partial.takeIf { it.exists() }?.length() ?: 0L
        val connection = (URL(sourceUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            if (existingBytes > 0) setRequestProperty("Range", "bytes=$existingBytes-")
        }
        val append = existingBytes > 0 && connection.responseCode == HttpURLConnection.HTTP_PARTIAL
        if (!append) partial.delete()
        val downloadedBefore = if (append) existingBytes else 0L
        val responseLength = connection.contentLengthLong.coerceAtLeast(0L)
        val totalBytes = if (append && responseLength > 0L) downloadedBefore + responseLength else responseLength
        repository.saveLocalArtifact(
            artifact.copy(state = LocalModelState.DOWNLOADING, updatedAt = System.currentTimeMillis())
        )
        try {
            connection.inputStream.use { input ->
                FileOutputStream(partial, append).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    var downloaded = downloadedBefore
                    var lastReport = downloaded
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastReport >= 512 * 1024L) {
                            onProgress(downloaded, totalBytes)
                            lastReport = downloaded
                        }
                    }
                    onProgress(downloaded, totalBytes)
                }
            }
            importVerifiedModel(artifact.copy(state = LocalModelState.VERIFYING), partial)
                .also { partial.delete() }
        } catch (error: CancellationException) {
            // A user pause/cancel must keep the resumable partial file and not
            // turn a deliberate interruption into a checksum/runtime failure.
            repository.saveLocalArtifact(
                artifact.copy(state = LocalModelState.DOWNLOADING, updatedAt = System.currentTimeMillis())
            )
            throw error
        } catch (error: Exception) {
            repository.saveLocalArtifact(
                artifact.copy(
                    state = LocalModelState.FAILED,
                    diagnosticReason = error.message ?: "本地模型下载失败",
                    updatedAt = System.currentTimeMillis()
                )
            )
            throw error
        } finally {
            connection.disconnect()
        }
    }

    suspend fun importVerifiedModel(
        artifact: LocalModelArtifact,
        sourceFile: File
    ): LocalModelArtifact = withContext(Dispatchers.IO) {
        require(sourceFile.isFile) { "Model file does not exist" }
        modelsDirectory.mkdirs()
        val verifying = artifact.copy(state = LocalModelState.VERIFYING, updatedAt = System.currentTimeMillis())
        repository.saveLocalArtifact(verifying)

        val actualSha256 = sourceFile.sha256()
        if (!actualSha256.equals(artifact.sha256, ignoreCase = true)) {
            val failed = verifying.copy(
                state = LocalModelState.FAILED,
                diagnosticReason = "LiteRT-LM model checksum mismatch",
                updatedAt = System.currentTimeMillis()
            )
            repository.saveLocalArtifact(failed)
            error("LiteRT-LM model checksum mismatch")
        }

        val destination = File(modelsDirectory, artifact.installedFileName())
        val temporary = File(modelsDirectory, ".${artifact.installedFileName()}.part")
        if (sourceFile.absoluteFile != temporary.absoluteFile) {
            sourceFile.copyTo(temporary, overwrite = true)
        }
        check(temporary.renameTo(destination)) { "Unable to atomically install local model" }
        val installed = verifying.copy(
            // A checksum-verified artifact is immediately the current version for its
            // logical model. Older versions remain available for rollback/removal rules.
            state = LocalModelState.ACTIVE,
            localPath = destination.absolutePath,
            updatedAt = System.currentTimeMillis()
        )
        repository.saveLocalArtifact(installed)
        installed
    }

    suspend fun activate(id: String): LocalModelArtifact {
        val artifact = requireNotNull(repository.getLocalArtifact(id)) { "Unknown local model: $id" }
        check(artifact.state == LocalModelState.INSTALLED || artifact.state == LocalModelState.ACTIVE) {
            "Only verified installed models can be activated"
        }
        val active = artifact.copy(state = LocalModelState.ACTIVE, updatedAt = System.currentTimeMillis())
        repository.saveLocalArtifact(active)
        return active
    }

    suspend fun getArtifact(id: String): LocalModelArtifact? = repository.getLocalArtifact(id)

    suspend fun resolveForProfile(profile: ModelProfile): LocalModelArtifact? {
        val logicalId = profile.localModelId ?: return null
        return repository.getLocalArtifacts()
            .asSequence()
            .filter { it.deletedAt == null }
            .filter { it.logicalModelId == logicalId || it.id == logicalId }
            .filter { it.state == LocalModelState.ACTIVE && it.localPath != null }
            .maxWithOrNull(Comparator { left, right -> compareVersions(left.version, right.version) })
    }

    suspend fun remove(id: String) {
        val artifact = requireNotNull(repository.getLocalArtifact(id)) { "Unknown local model: $id" }
        val assigned = repository.getProfiles().count {
            it.enabled && it.localModelId != null &&
                (it.localModelId == artifact.logicalModelId || it.localModelId == artifact.id)
        }
        check(assigned == 0 && artifact.assignedProfileCount == 0) { "Cannot remove an assigned local model" }
        artifact.localPath?.let { File(it).delete() }
        repository.saveLocalArtifact(artifact.copy(state = LocalModelState.REMOVING, updatedAt = System.currentTimeMillis()))
        repository.deleteLocalArtifact(id)
    }
}

private fun compareVersions(left: String, right: String): Int {
    val leftParts = Regex("\\d+").findAll(left).map { it.value.toIntOrNull() ?: 0 }.toList().ifEmpty { listOf(0) }
    val rightParts = Regex("\\d+").findAll(right).map { it.value.toIntOrNull() ?: 0 }.toList().ifEmpty { listOf(0) }
    val size = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until size) {
        val difference = (leftParts.getOrElse(index) { 0 } - rightParts.getOrElse(index) { 0 })
        if (difference != 0) return difference
    }
    return left.compareTo(right)
}

private fun LocalModelArtifact.installedFileName(): String {
    require(fileName.isNotBlank() && File(fileName).name == fileName) { "模型文件名无效" }
    return "$id-$fileName"
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
