package com.lhy.yumerin.data.remote

import com.lhy.yumerin.core.model.AgentReply
import com.lhy.yumerin.core.model.AgentRequest
import com.lhy.yumerin.core.model.AgentService
import com.lhy.yumerin.core.model.ModelProviderKind
import com.lhy.yumerin.domain.agent.ContextAssembler
import com.lhy.yumerin.data.local.LocalAiService
import com.lhy.yumerin.data.local.LocalModelManager
import com.lhy.yumerin.domain.repository.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AndroidAgentService(
    private val credentials: CredentialStore,
    private val localModels: LocalModelManager,
    private val localAiService: LocalAiService = LocalAiService.getInstance()
) : AgentService {
    override suspend fun reply(request: AgentRequest): AgentReply = when (request.model.providerKind) {
        ModelProviderKind.OPENAI_COMPATIBLE -> completeOpenAiCompatible(request)
        ModelProviderKind.LITERT_LM -> completeLocalModel(request)
    }

    override suspend fun stream(
        request: AgentRequest,
        onAccumulatedText: suspend (String) -> Unit
    ): AgentReply = when (request.model.providerKind) {
        ModelProviderKind.OPENAI_COMPATIBLE -> completeOpenAiCompatible(request, onAccumulatedText)
        ModelProviderKind.LITERT_LM -> completeLocalModel(request, onAccumulatedText)
    }

    private suspend fun completeLocalModel(
        request: AgentRequest,
        onAccumulatedText: suspend (String) -> Unit = {}
    ): AgentReply {
        val artifact = localModels.resolveForProfile(request.model)
            ?: return AgentReply("找不到本地模型文件，请重新激活模型。", request.model.id, statusMessage = "local-model-missing")
        return try {
            val lease = localAiService.acquire(artifact)
            try {
                val prompt = ContextAssembler.assemble(request)
                val system = prompt.firstOrNull { it.role == "system" }?.content.orEmpty()
                val userPrompt = prompt.drop(1).joinToString("\n") { "${it.role}: ${it.content}" }
                val text = lease.generate(system, userPrompt)
                onAccumulatedText(text)
                AgentReply(
                    text = text,
                    modelProfileId = request.model.id,
                    usedMemoryIds = ContextAssembler.usedMemoryIds(request)
                )
            } finally {
                lease.close()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AgentReply(
                text = "本地模型运行失败：${error.message ?: "未知错误"}",
                modelProfileId = request.model.id,
                statusMessage = "local-runtime-error"
            )
        }
    }

    private suspend fun completeOpenAiCompatible(
        request: AgentRequest,
        onAccumulatedText: suspend (String) -> Unit = {}
    ): AgentReply = withContext(Dispatchers.IO) {
        val alias = request.model.credentialAlias
        val apiKey = alias?.let { credentials.read(it) }
        if (apiKey.isNullOrBlank()) {
            return@withContext AgentReply(
                text = "没有找到这个模型的 API Key，请回到模型设置补充。",
                modelProfileId = request.model.id,
                statusMessage = "credential-missing"
            )
        }
        val baseUrl = request.model.baseUrl?.trimEnd('/')
        if (baseUrl.isNullOrBlank()) {
            return@withContext AgentReply(
                text = "模型 Base URL 为空，请回到模型设置检查。",
                modelProfileId = request.model.id,
                statusMessage = "base-url-missing"
            )
        }
        val endpoint = if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", if (request.streaming) "text/event-stream" else "application/json")
        }
        try {
            val payload = JSONObject().apply {
                put("model", request.model.modelId)
                put("messages", JSONArray().apply {
                    ContextAssembler.assemble(request).forEach { prompt ->
                        put(JSONObject().put("role", prompt.role).put("content", prompt.content))
                    }
                })
                request.model.generationParameters.temperature?.let { put("temperature", it) }
                request.model.generationParameters.topP?.let { put("top_p", it) }
                request.model.generationParameters.maxOutputTokens?.let { put("max_tokens", it) }
                if (request.model.generationParameters.stopSequences.isNotEmpty()) {
                    put("stop", JSONArray(request.model.generationParameters.stopSequences))
                }
                if (request.streaming) put("stream", true)
            }
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    .orEmpty()
                    .replace(apiKey, "[REDACTED]")
                return@withContext AgentReply(
                    text = "模型请求失败（HTTP $code）。" + errorBody.take(160).let { if (it.isBlank()) "" else " $it" },
                    modelProfileId = request.model.id,
                    statusMessage = "remote-http-$code"
                )
            }
            val content = if (request.streaming) {
                val reader = connection.inputStream.bufferedReader()
                var accumulated = ""
                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        val data = line.removePrefix("data:").trim()
                        if (data.isBlank() || data == "[DONE]") continue
                        val delta = runCatching {
                            JSONObject(data)
                                .optJSONArray("choices")
                                ?.optJSONObject(0)
                                ?.optJSONObject("delta")
                                ?.optString("content", "")
                                .orEmpty()
                        }.getOrDefault("")
                        if (delta.isNotEmpty()) {
                            accumulated += delta
                            onAccumulatedText(accumulated)
                        }
                    }
                } finally {
                    reader.close()
                }
                accumulated
            } else {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(body)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content", "")
                    .orEmpty()
                    .also { if (it.isNotBlank()) onAccumulatedText(it) }
            }
            if (content.isBlank()) return@withContext AgentReply(
                text = "模型返回了空消息。",
                modelProfileId = request.model.id,
                statusMessage = "remote-empty"
            )
            AgentReply(
                text = content,
                modelProfileId = request.model.id,
                usedMemoryIds = ContextAssembler.usedMemoryIds(request)
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AgentReply(
                text = "模型请求失败：${error.message ?: "网络或服务不可用"}",
                modelProfileId = request.model.id,
                statusMessage = "remote-exception"
            )
        } finally {
            connection.disconnect()
        }
    }
}
