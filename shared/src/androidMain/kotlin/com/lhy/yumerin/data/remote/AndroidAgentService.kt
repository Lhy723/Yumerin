package com.lhy.yumerin.data.remote

import com.lhy.yumerin.core.model.AgentReply
import com.lhy.yumerin.core.model.AgentCapabilityCall
import com.lhy.yumerin.core.model.AgentCapabilityDescriptor
import com.lhy.yumerin.core.model.AgentConversationMessage
import com.lhy.yumerin.core.model.AgentModelStep
import com.lhy.yumerin.core.model.AgentRequest
import com.lhy.yumerin.core.model.AgentService
import com.lhy.yumerin.core.model.newEntityId
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
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import javax.net.ssl.SSLException

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

    override suspend fun nextStep(
        request: AgentRequest,
        conversation: List<AgentConversationMessage>,
        capabilities: List<AgentCapabilityDescriptor>,
        onAccumulatedText: suspend (String) -> Unit
    ): AgentModelStep {
        if (request.model.providerKind != ModelProviderKind.OPENAI_COMPATIBLE) {
            if (capabilities.isNotEmpty() || conversation.any { it.role == "tool" }) {
                return completeLocalModelStep(request, conversation, capabilities, onAccumulatedText)
            }
            val reply = stream(request, onAccumulatedText)
            return AgentModelStep.FinalResponse(reply)
        }
        if (capabilities.isEmpty() && conversation.none { it.role == "tool" }) {
            return AgentModelStep.FinalResponse(stream(request, onAccumulatedText))
        }
        return completeOpenAiCompatibleStep(request, conversation, capabilities, onAccumulatedText)
    }

    /** Local models use a bounded JSON action protocol and execute only registered read-only capabilities. */
    private suspend fun completeLocalModelStep(
        request: AgentRequest,
        conversation: List<AgentConversationMessage>,
        capabilities: List<AgentCapabilityDescriptor>,
        onAccumulatedText: suspend (String) -> Unit
    ): AgentModelStep {
        val artifact = localModels.resolveForProfile(request.model)
            ?: return AgentModelStep.FinalResponse(
                AgentReply("找不到本地模型文件，请重新激活模型。", request.model.id, statusMessage = "local-model-missing")
            )
        return try {
            val lease = localAiService.acquire(artifact)
            try {
                val baseSystem = conversation.firstOrNull { it.role == "system" }?.content.orEmpty()
                val toolList = capabilities.joinToString("\n") { capability ->
                    "- ${capability.name}: ${capability.description}; parameters schema: ${capability.parametersJsonSchema}"
                }
                val actionInstruction = if (capabilities.isEmpty()) {
                    "This run has no available capabilities. Return exactly one JSON object: {\"type\":\"final\",\"text\":\"your user-facing answer\"}. Do not output markdown or internal reasoning."
                } else {
                    "Available read-only capabilities:\n$toolList\n" +
                        "Return exactly one JSON object and no markdown. To answer, use {\"type\":\"final\",\"text\":\"...\"}. " +
                        "To request one listed capability, use {\"type\":\"capability\",\"name\":\"...\",\"arguments\":{...}}. " +
                        "Never claim an action completed before a capability result confirms it. Do not reveal internal reasoning."
                }
                val system = "$baseSystem\n\n$actionInstruction"
                val prompt = conversation.drop(1).joinToString("\n") { message ->
                    when (message.role) {
                        "user" -> "User: ${message.content}"
                        "assistant" -> "Character: ${message.content}"
                        "tool" -> "Capability result (${message.name ?: "unknown"}): ${message.content}"
                        else -> "${message.role}: ${message.content}"
                    }
                }
                val raw = lease.generate(system, prompt, request.model.generationParameters.maxOutputTokens)
                val jsonText = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val parsed = runCatching { JSONObject(jsonText) }.getOrNull()
                when (parsed?.optString("type")) {
                    "capability" -> {
                        val name = parsed.optString("name", "")
                        val arguments = parsed.opt("arguments")?.let { value ->
                            if (value is String) value else value.toString()
                        }.orEmpty()
                        if (name.isBlank() || arguments.isBlank()) {
                            AgentModelStep.FinalResponse(
                                AgentReply("本地模型返回的能力请求无法识别，请重试或换用云端模型。", request.model.id, statusMessage = "local-invalid-capability-call")
                            )
                        } else {
                            AgentModelStep.CapabilityRequests(
                                calls = listOf(AgentCapabilityCall(newEntityId("capability"), name, arguments))
                            )
                        }
                    }
                    "final" -> {
                        val answer = parsed.optString("text", "").trim()
                        if (answer.isBlank()) {
                            AgentModelStep.FinalResponse(
                                AgentReply("本地模型返回了空消息。", request.model.id, statusMessage = "local-empty")
                            )
                        } else {
                            onAccumulatedText(answer)
                            AgentModelStep.FinalResponse(
                                AgentReply(answer, request.model.id, ContextAssembler.usedMemoryIds(request))
                            )
                        }
                    }
                    else -> {
                        val answer = raw.trim().takeIf { it.isNotBlank() }
                            ?: return AgentModelStep.FinalResponse(
                                AgentReply("本地模型返回了空消息。", request.model.id, statusMessage = "local-empty")
                            )
                        onAccumulatedText(answer)
                        AgentModelStep.FinalResponse(
                            AgentReply(answer, request.model.id, ContextAssembler.usedMemoryIds(request))
                        )
                    }
                }
            } finally {
                lease.close()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AgentModelStep.FinalResponse(
                AgentReply(
                    "本地模型运行失败：${error.message ?: "未知错误"}",
                    request.model.id,
                    statusMessage = "local-runtime-error"
                )
            )
        }
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
                val text = lease.generate(system, userPrompt, request.model.generationParameters.maxOutputTokens)
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
                return@withContext AgentReply(
                    text = formatHttpFailure(code, errorBody, apiKey),
                    modelProfileId = request.model.id,
                    statusMessage = "remote-http-$code"
                )
            }
            var finishReason: String? = null
            val content = if (request.streaming) {
                val reader = connection.inputStream.bufferedReader()
                var accumulated = ""
                var eventName: String? = null
                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.startsWith("event:")) {
                            eventName = line.substringAfter(':').trim()
                            continue
                        }
                        if (line.isBlank()) {
                            eventName = null
                            continue
                        }
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data.isBlank()) continue
                        if (data == "[DONE]") break

                        streamErrorMessage(data, eventName, apiKey)?.let { detail ->
                            return@withContext AgentReply(
                                text = "模型接口返回错误：$detail",
                                modelProfileId = request.model.id,
                                statusMessage = "remote-exception"
                            )
                        }

                        val choice = runCatching {
                            JSONObject(data).optJSONArray("choices")?.optJSONObject(0)
                        }.getOrNull()
                        val delta = choice?.optJSONObject("delta")?.optString("content", "").orEmpty()
                        if (delta.isNotEmpty()) {
                            accumulated += delta
                            onAccumulatedText(accumulated)
                        }
                        val rawFinishReason = choice?.opt("finish_reason")
                        finishReason = rawFinishReason
                            ?.takeUnless { it == JSONObject.NULL }
                            ?.toString()
                            ?.takeIf { it.isNotBlank() && it != "null" }
                        if (finishReason != null) break
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
                text = if (finishReason == "content_filter") "模型服务因内容过滤未能返回回复。" else "模型返回了空消息。",
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
                text = "模型请求失败：${formatProviderException(error, apiKey)}",
                modelProfileId = request.model.id,
                statusMessage = "remote-exception"
            )
        } finally {
            connection.disconnect()
        }
    }

    /** Tool-aware requests are non-streaming so partial tool arguments never reach the UI. */
    private suspend fun completeOpenAiCompatibleStep(
        request: AgentRequest,
        conversation: List<AgentConversationMessage>,
        capabilities: List<AgentCapabilityDescriptor>,
        onAccumulatedText: suspend (String) -> Unit
    ): AgentModelStep = withContext(Dispatchers.IO) {
        val alias = request.model.credentialAlias
        val apiKey = alias?.let { credentials.read(it) }
        if (apiKey.isNullOrBlank()) {
            return@withContext AgentModelStep.FinalResponse(
                AgentReply("没有找到这个模型的 API Key，请回到模型设置补充。", request.model.id, statusMessage = "credential-missing")
            )
        }
        val baseUrl = request.model.baseUrl?.trimEnd('/')
        if (baseUrl.isNullOrBlank()) {
            return@withContext AgentModelStep.FinalResponse(
                AgentReply("模型 Base URL 为空，请回到模型设置检查。", request.model.id, statusMessage = "base-url-missing")
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
            setRequestProperty("Accept", "application/json")
        }
        try {
            val payload = JSONObject().apply {
                put("model", request.model.modelId)
                put("messages", JSONArray().apply {
                    conversation.forEachIndexed { index, message ->
                        put(JSONObject().apply {
                            put("role", message.role)
                            val content = if (capabilities.isNotEmpty() && index == 0 && message.role == "system") {
                                message.content + " Use a provided capability when it helps; treat its result as the only evidence that the action completed, and then answer the user. Never reveal internal reasoning."
                            } else message.content
                            if (content.isNotEmpty() || message.capabilityCalls.isEmpty()) put("content", content)
                            message.name?.let { put("name", it) }
                            message.capabilityCallId?.let { put("tool_call_id", it) }
                            if (message.capabilityCalls.isNotEmpty()) {
                                put("tool_calls", JSONArray().apply {
                                    message.capabilityCalls.forEach { call ->
                                        put(JSONObject().put("id", call.id).put("type", "function").put(
                                            "function", JSONObject()
                                                .put("name", call.name)
                                                .put("arguments", call.argumentsJson)
                                        ))
                                    }
                                })
                            }
                        })
                    }
                })
                if (capabilities.isNotEmpty()) {
                    put("tools", JSONArray().apply {
                        capabilities.forEach { capability ->
                            put(JSONObject().put("type", "function").put(
                                "function", JSONObject()
                                    .put("name", capability.name)
                                    .put("description", capability.description)
                                    .put("parameters", JSONObject(capability.parametersJsonSchema))
                            ))
                        }
                    })
                    put("tool_choice", "auto")
                    put("parallel_tool_calls", false)
                }
                request.model.generationParameters.temperature?.let { put("temperature", it) }
                request.model.generationParameters.topP?.let { put("top_p", it) }
                request.model.generationParameters.maxOutputTokens?.let { put("max_tokens", it) }
                if (request.model.generationParameters.stopSequences.isNotEmpty()) {
                    put("stop", JSONArray(request.model.generationParameters.stopSequences))
                }
            }
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    .orEmpty()
                return@withContext AgentModelStep.FinalResponse(
                    AgentReply(
                        text = formatHttpFailure(code, errorBody, apiKey),
                        modelProfileId = request.model.id,
                        statusMessage = "remote-http-$code"
                    )
                )
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val message = JSONObject(body)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?: return@withContext AgentModelStep.FinalResponse(
                    AgentReply("模型返回了空消息。", request.model.id, statusMessage = "remote-empty")
                )
            val content = message.optString("content", "").takeUnless { it == "null" }.orEmpty()
            val rawCalls = message.optJSONArray("tool_calls")
            if (rawCalls != null && rawCalls.length() > 0) {
                val calls = (0 until rawCalls.length()).mapNotNull { index ->
                    val item = rawCalls.optJSONObject(index) ?: return@mapNotNull null
                    val function = item.optJSONObject("function") ?: return@mapNotNull null
                    val name = function.optString("name", "")
                    if (name.isBlank()) return@mapNotNull null
                    val arguments = function.opt("arguments")?.let { value ->
                        if (value is String) value else value.toString()
                    }.orEmpty()
                    AgentCapabilityCall(
                        id = item.optString("id").takeIf { it.isNotBlank() } ?: newEntityId("capability"),
                        name = name,
                        argumentsJson = arguments
                    )
                }
                if (calls.isEmpty()) {
                    return@withContext AgentModelStep.FinalResponse(
                        AgentReply("模型返回了无法识别的能力调用。", request.model.id, statusMessage = "remote-invalid-capability-call")
                    )
                }
                return@withContext AgentModelStep.CapabilityRequests(calls, content.takeIf { it.isNotBlank() })
            }
            if (content.isBlank()) {
                return@withContext AgentModelStep.FinalResponse(
                    AgentReply("模型返回了空消息。", request.model.id, statusMessage = "remote-empty")
                )
            }
            onAccumulatedText(content)
            AgentModelStep.FinalResponse(
                AgentReply(
                    text = content,
                    modelProfileId = request.model.id,
                    usedMemoryIds = ContextAssembler.usedMemoryIds(request)
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AgentModelStep.FinalResponse(
                AgentReply(
                    text = "模型请求失败：${formatProviderException(error, apiKey)}",
                    modelProfileId = request.model.id,
                    statusMessage = "remote-exception"
                )
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun formatHttpFailure(code: Int, body: String, apiKey: String): String {
        val detail = extractProviderErrorMessage(body, apiKey)
            ?: sanitizeErrorText(body, apiKey)
        return if (detail.isBlank()) {
            "模型请求失败（HTTP $code）：服务端没有提供错误详情。"
        } else {
            "模型请求失败（HTTP $code）：$detail"
        }
    }

    private fun streamErrorMessage(data: String, eventName: String?, apiKey: String): String? {
        extractProviderErrorMessage(data, apiKey)?.let { return it }
        if (eventName?.equals("error", ignoreCase = true) != true) return null

        val safeData = sanitizeErrorText(data, apiKey)
        val payload = runCatching { JSONObject(safeData) }.getOrNull()
        return firstNonBlank(
            payload?.optString("message").orEmpty(),
            payload?.optString("detail").orEmpty(),
            safeData
        )
    }

    private fun extractProviderErrorMessage(body: String, apiKey: String): String? {
        val safeBody = sanitizeErrorText(body, apiKey)
        val payload = runCatching { JSONObject(safeBody) }.getOrNull() ?: return null
        val error = payload.opt("error")
        if (error == null || error == JSONObject.NULL) return null

        val detail = when (error) {
            is JSONObject -> firstNonBlank(
                error.optString("message"),
                error.optString("detail"),
                error.optString("type"),
                error.optString("code")
            )
            is String -> error
            else -> error.toString()
        }
        return detail?.let { sanitizeErrorText(it, apiKey) }
    }

    private fun firstNonBlank(vararg values: String): String? =
        values.firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

    private fun sanitizeErrorText(value: String, apiKey: String): String = value
        .let { if (apiKey.isNotBlank()) it.replace(apiKey, "[REDACTED]") else it }
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_PROVIDER_ERROR_CHARS)

    private fun formatProviderException(error: Exception, apiKey: String): String = when (error) {
        is SocketTimeoutException -> "连接模型服务超时，请检查网络或服务状态。"
        is UnknownHostException -> "无法解析模型服务地址，请检查 Base URL 和网络。"
        is ConnectException -> "无法连接模型服务，请检查服务状态和 Base URL。"
        is SSLException -> "与模型服务建立 TLS 安全连接失败，请检查 HTTPS 或证书配置。"
        is IOException -> sanitizeErrorText(error.message.orEmpty(), apiKey)
            .ifBlank { "网络连接中断，请检查网络或模型服务状态。" }
        else -> sanitizeErrorText(error.message.orEmpty(), apiKey)
            .ifBlank { "网络或模型服务不可用。" }
    }

    private companion object {
        const val MAX_PROVIDER_ERROR_CHARS = 500
    }
}
