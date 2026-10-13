package com.lhy.yumerin.data.local

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ConversationConfig
import com.lhy.yumerin.core.model.LocalModelArtifact
import com.lhy.yumerin.core.model.AgentConversationMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

/** Process-scoped LiteRT-LM engine cache with short idle grace and reference-counted leases. */
class LocalAiService private constructor(
    private val scope: CoroutineScope
) {
    internal data class CachedEngine(
        val key: String,
        val engine: Engine,
        var references: Int,
        var closeJob: Job?
    )

    private val engines = mutableMapOf<String, CachedEngine>()

    suspend fun acquire(artifact: LocalModelArtifact): Lease = withContext(Dispatchers.IO) {
        val path = requireNotNull(artifact.localPath) { "Local model is not installed" }
        val key = "${artifact.id}@${artifact.version}@${artifact.backend.lowercase()}"
        synchronized(engines) {
            engines[key]?.let { cached ->
                cached.references += 1
                cached.closeJob?.cancel()
                cached.closeJob = null
                return@withContext Lease(this@LocalAiService, cached)
            }
        }
        val engine = createInitializedEngine(path, artifact.backend)
        val cached = CachedEngine(key, engine, references = 1, closeJob = null)
        synchronized(engines) { engines[key] = cached }
        Lease(this@LocalAiService, cached)
    }

    /** Performs the same lightweight engine initialization used before inference. */
    suspend fun probe(artifact: LocalModelArtifact) {
        acquire(artifact).close()
    }

    /** Closes idle engines when Android reports that the app is backgrounded or under pressure. */
    fun trimMemory() {
        val idle = synchronized(engines) {
            engines.values.filter { it.references == 0 }.also { cached ->
                cached.forEach {
                    it.closeJob?.cancel()
                    engines.remove(it.key)
                }
            }
        }
        idle.forEach { it.engine.close() }
    }

    private fun createInitializedEngine(path: String, requestedBackend: String): Engine {
        fun create(backend: Backend): Engine = Engine(
            EngineConfig(modelPath = path, backend = backend)
        ).also { it.initialize() }

        return when (requestedBackend.trim().lowercase()) {
            "gpu" -> create(Backend.GPU())
            "cpu" -> create(Backend.CPU())
            // AUTO prefers GPU but remains usable on devices without a GPU delegate.
            else -> runCatching { create(Backend.GPU()) }.getOrElse { create(Backend.CPU()) }
        }
    }

    private fun release(cached: CachedEngine) {
        synchronized(engines) {
            cached.references = (cached.references - 1).coerceAtLeast(0)
            if (cached.references != 0 || cached.closeJob != null) return
            cached.closeJob = scope.launch(Dispatchers.IO) {
                delay(30_000)
                synchronized(engines) {
                    if (cached.references == 0) {
                        engines.remove(cached.key)
                        cached.engine.close()
                    }
                }
            }
        }
    }

    class Lease internal constructor(
        private val owner: LocalAiService,
        private val cached: CachedEngine
    ) : AutoCloseable {
        private var closed = false

        suspend fun generate(
            systemInstruction: String,
            prompt: String,
            maxOutputTokens: Int? = null
        ): String = withContext(Dispatchers.IO) {
            check(!closed) { "Local model lease is closed" }
            cached.engine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(systemInstruction),
                    maxOutputToken = maxOutputTokens
                )
            ).use { conversation ->
                conversation.sendMessage(prompt)
                    .contents
                    .contents
                    .filterIsInstance<Content.Text>()
                    .joinToString(separator = "") { it.text }
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            owner.release(cached)
        }
    }

    companion object {
        @Volatile
        private var instance: LocalAiService? = null

        fun getInstance(): LocalAiService = instance ?: synchronized(this) {
            instance ?: LocalAiService(CoroutineScope(SupervisorJob() + Dispatchers.Default)).also { instance = it }
        }
    }
}

fun List<AgentConversationMessage>.asLocalPrompt(): String = joinToString("\n") { "${it.role}: ${it.content}" }
