package com.lhy.yumerin.data.remote

import com.lhy.yumerin.domain.repository.ModelConfigurationTestResult
import com.lhy.yumerin.domain.repository.ModelConfigurationTester
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Small, side-effect-free Chat Completions probes used by the model setup screen. */
class AndroidModelConfigurationTester : ModelConfigurationTester {
    override suspend fun testRemote(
        baseUrl: String,
        modelId: String,
        apiKey: String
    ): ModelConfigurationTestResult = withContext(Dispatchers.IO) {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        val normalizedModelId = modelId.trim()
        val credential = apiKey.trim()
        if (normalizedBaseUrl.isBlank() || normalizedModelId.isBlank() || credential.isBlank()) {
            return@withContext ModelConfigurationTestResult(
                connectionSucceeded = false,
                toolCallsSupported = null,
                message = "请填写 Base URL、Model ID 和 API Key。"
            )
        }

        val endpoint = if (normalizedBaseUrl.endsWith("/chat/completions")) {
            normalizedBaseUrl
        } else {
            "$normalizedBaseUrl/chat/completions"
        }

        val basicPayload = JSONObject().apply {
            put("model", normalizedModelId)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "user").put("content", "Reply with exactly YUMERIN_OK."))
            })
        }
        val basic = try {
            post(endpoint, credential, basicPayload)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return@withContext ModelConfigurationTestResult(
                connectionSucceeded = false,
                toolCallsSupported = null,
                message = "连接失败：${error.safeMessage(credential)}"
            )
        }
        if (basic.statusCode !in 200..299) {
            return@withContext ModelConfigurationTestResult(
                connectionSucceeded = false,
                toolCallsSupported = null,
                message = "连接失败（HTTP ${basic.statusCode}）：${basic.body.safeBody(credential)}"
            )
        }
        val basicMessage = runCatching {
            JSONObject(basic.body).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
        }.getOrNull()
        if (basicMessage == null) {
            return@withContext ModelConfigurationTestResult(
                connectionSucceeded = false,
                toolCallsSupported = null,
                message = "服务已响应，但返回内容不是 Chat Completions 格式。"
            )
        }

        val probeToolName = "yumerin_tool_call_probe"
        val toolPayload = JSONObject().apply {
            put("model", normalizedModelId)
            put("messages", JSONArray().apply {
                put(JSONObject().put(
                    "role",
                    "system"
                ).put("content", "This is a tool-calling compatibility check. Call the only available function now; do not answer in text."))
                put(JSONObject().put("role", "user").put("content", "Invoke the compatibility check function."))
            })
            put("tools", JSONArray().put(JSONObject().put("type", "function").put(
                "function",
                JSONObject()
                    .put("name", probeToolName)
                    .put("description", "A harmless compatibility probe. Calling it performs no action.")
                    .put("parameters", JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject().put("confirmation", JSONObject()
                            .put("type", "string")
                            .put("enum", JSONArray().put("YUMERIN_TOOL_CALL_OK"))))
                        .put("required", JSONArray().put("confirmation"))
                        .put("additionalProperties", false))
            )))
            // Match the selection mode used by Yumerin; only one harmless probe tool is offered.
            put("tool_choice", "auto")
            put("parallel_tool_calls", false)
        }
        val toolResponse = try {
            post(endpoint, credential, toolPayload)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return@withContext ModelConfigurationTestResult(
                connectionSucceeded = true,
                toolCallsSupported = false,
                message = "连接成功，但工具调用测试失败：${error.safeMessage(credential)}"
            )
        }
        if (toolResponse.statusCode !in 200..299) {
            return@withContext ModelConfigurationTestResult(
                connectionSucceeded = true,
                toolCallsSupported = false,
                message = "连接成功，但工具调用测试失败（HTTP ${toolResponse.statusCode}）：${toolResponse.body.safeBody(credential)}"
            )
        }
        val toolMessage = runCatching {
            JSONObject(toolResponse.body).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
        }.getOrNull()
        if (toolMessage == null) {
            return@withContext ModelConfigurationTestResult(
                connectionSucceeded = true,
                toolCallsSupported = false,
                message = "连接成功，但工具调用响应不是 Chat Completions 格式。"
            )
        }
        val calls = toolMessage.optJSONArray("tool_calls")
        val supported = calls?.let { values ->
            (0 until values.length()).any { index ->
                values.optJSONObject(index)?.optJSONObject("function")?.optString("name") == probeToolName
            }
        } == true
        ModelConfigurationTestResult(
            connectionSucceeded = true,
            toolCallsSupported = supported,
            message = if (supported) {
                "连接成功，工具调用也正常。"
            } else {
                "连接成功，但模型没有返回工具调用；请确认该模型和服务端支持 Chat Completions 工具调用。"
            }
        )
    }

    private fun post(endpoint: String, apiKey: String, payload: JSONObject): HttpResult {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            HttpResult(status, body)
        } finally {
            connection.disconnect()
        }
    }

    private fun String.safeBody(apiKey: String): String =
        replace(apiKey, "[已隐藏]").replace(Regex("\\s+"), " ").take(240).ifBlank { "服务未提供错误详情" }

    private fun Throwable.safeMessage(apiKey: String): String =
        (message ?: "请检查网络、地址和服务状态.")
            .replace(apiKey, "[已隐藏]")
            .replace(Regex("https?://[^\\s]+"), "模型服务地址")
            .take(160)

    private data class HttpResult(val statusCode: Int, val body: String)
}
