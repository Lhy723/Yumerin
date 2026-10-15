package com.lhy.yumerin

import androidx.compose.animation.*
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lhy.yumerin.core.model.*
import com.lhy.yumerin.data.memory.*
import com.lhy.yumerin.designsystem.*
import com.lhy.yumerin.domain.*
import com.lhy.yumerin.domain.memory.*
import com.lhy.yumerin.domain.repository.*
import com.lhy.yumerin.domain.usecase.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Clock

@Composable
internal fun ModelsPage(
    graph: AppGraph,
    profiles: List<ModelProfile>,
    localArtifacts: List<com.lhy.yumerin.core.model.LocalModelArtifact>,
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onPickLocalModel: ((onSelected: (String?) -> Unit) -> Unit),
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var providerName by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("https://api.openai.com/v1") }
    var modelId by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var contextLength by remember { mutableStateOf("") }
    var temperature by remember { mutableStateOf("") }
    var topP by remember { mutableStateOf("") }
    var maxOutputTokens by remember { mutableStateOf("") }
    var stopSequences by remember { mutableStateOf("") }
    var artifactEditing by remember { mutableStateOf(false) }
    var artifactName by remember { mutableStateOf("") }
    var artifactLogicalId by remember { mutableStateOf("") }
    var artifactVersion by remember { mutableStateOf("") }
    var artifactFileName by remember { mutableStateOf("") }
    var artifactSize by remember { mutableStateOf("") }
    var artifactSha256 by remember { mutableStateOf("") }
    var artifactLicense by remember { mutableStateOf("") }
    var artifactSourceUrl by remember { mutableStateOf("") }
    var artifactBackend by remember { mutableStateOf("auto") }
    var artifactContextLength by remember { mutableStateOf("") }
    var artifactRequirements by remember { mutableStateOf("") }
    var allowMeteredDownload by remember { mutableStateOf(false) }
    var modelActionMessage by remember { mutableStateOf<String?>(null) }
    var modelTestRunning by remember { mutableStateOf(false) }
    var modelTestResult by remember { mutableStateOf<ModelConfigurationTestResult?>(null) }

    PageColumn {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MiuixBackButton(onClick = onBack)
            Text("模型", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            if (editing) {
                TextButton(onClick = { editing = false }, enabled = !modelTestRunning) { Text("取消") }
            } else {
                MiuixGlassIconButton(
                    icon = YumerinAddIcon,
                    contentDescription = "添加模型",
                    onClick = { editing = true },
                    enabled = !modelTestRunning
                )
            }
        }
        if (editing) {
            MiuixCardSurface(Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("OpenAI-compatible 模型", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(displayName, { displayName = it }, Modifier.fillMaxWidth(), label = { Text("显示名称") }, singleLine = true)
                    OutlinedTextField(providerName, { providerName = it }, Modifier.fillMaxWidth(), label = { Text("提供商") }, singleLine = true)
                    OutlinedTextField(baseUrl, { baseUrl = it; modelTestResult = null }, Modifier.fillMaxWidth(), label = { Text("Base URL") }, singleLine = true)
                    OutlinedTextField(modelId, { modelId = it; modelTestResult = null }, Modifier.fillMaxWidth(), label = { Text("Model ID") }, singleLine = true)
                    OutlinedTextField(
                        apiKey,
                        { apiKey = it; modelTestResult = null },
                        Modifier.fillMaxWidth(),
                        label = { Text("API Key（必填，仅本地安全存储）") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(contextLength, { contextLength = it }, Modifier.fillMaxWidth(), label = { Text("上下文长度（可选）") }, singleLine = true)
                    OutlinedTextField(temperature, { temperature = it }, Modifier.fillMaxWidth(), label = { Text("Temperature（可选）") }, singleLine = true)
                    OutlinedTextField(topP, { topP = it }, Modifier.fillMaxWidth(), label = { Text("Top P（可选）") }, singleLine = true)
                    OutlinedTextField(maxOutputTokens, { maxOutputTokens = it }, Modifier.fillMaxWidth(), label = { Text("Max output tokens（可选）") }, singleLine = true)
                    OutlinedTextField(stopSequences, { stopSequences = it }, Modifier.fillMaxWidth(), label = { Text("Stop sequences（逗号分隔，可选）") })
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val testBaseUrl = baseUrl
                                val testModelId = modelId
                                val testApiKey = apiKey
                                scope.launch {
                                    modelTestRunning = true
                                    modelTestResult = null
                                    try {
                                        modelTestResult = graph.modelConfigurationTester.testRemote(
                                            baseUrl = testBaseUrl,
                                            modelId = testModelId,
                                            apiKey = testApiKey
                                        )
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Throwable) {
                                        modelTestResult = ModelConfigurationTestResult(
                                            connectionSucceeded = false,
                                            toolCallsSupported = null,
                                            message = "测试未完成，请检查配置、网络和服务状态。"
                                        )
                                    } finally {
                                        modelTestRunning = false
                                    }
                                }
                            },
                            enabled = !modelTestRunning && baseUrl.isNotBlank() && modelId.isNotBlank() && apiKey.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (modelTestRunning) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("测试中")
                            } else {
                                Text("测试模型")
                            }
                        }
                        Button(
                            onClick = {
                            val alias = newEntityId("credential")
                            val now = nowMillis()
                            scope.launch {
                                if (apiKey.isNotBlank()) graph.credentials.save(alias, apiKey)
                                graph.models.saveProfile(
                                    ModelProfile(
                                        id = newEntityId("model"),
                                        displayName = displayName.ifBlank { modelId },
                                        providerKind = com.lhy.yumerin.core.model.ModelProviderKind.OPENAI_COMPATIBLE,
                                        providerName = providerName.ifBlank { "OpenAI-compatible" },
                                        baseUrl = baseUrl.trimEnd('/'),
                                        modelId = modelId,
                                        credentialAlias = alias.takeIf { apiKey.isNotBlank() },
                                        contextLength = contextLength.toIntOrNull(),
                                        contextLengthMode = if (contextLength.toIntOrNull() == null) {
                                            com.lhy.yumerin.core.model.ContextLengthMode.AUTO
                                        } else {
                                            com.lhy.yumerin.core.model.ContextLengthMode.CUSTOM
                                        },
                                        generationParameters = com.lhy.yumerin.core.model.GenerationParameters(
                                            temperature = temperature.toDoubleOrNull(),
                                            topP = topP.toDoubleOrNull(),
                                            maxOutputTokens = maxOutputTokens.toIntOrNull(),
                                            stopSequences = stopSequences.split(",").map(String::trim).filter(String::isNotBlank)
                                        ),
                                        localModelId = null,
                                        localModelVersion = null,
                                        enabled = true,
                                        createdAt = now,
                                        updatedAt = now
                                    )
                                )
                                displayName = ""
                                providerName = ""
                                modelId = ""
                                apiKey = ""
                                contextLength = ""
                                temperature = ""
                                topP = ""
                                maxOutputTokens = ""
                                stopSequences = ""
                                editing = false
                            }
                            },
                            enabled = !modelTestRunning && modelId.isNotBlank() && apiKey.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text("保存模型") }
                    }
                    Text(
                        "测试会发送两条短请求（可能产生少量费用），不读取聊天或记忆，也不会保存本次配置。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    modelTestResult?.let { result ->
                        val resultColor = when {
                            result.connectionSucceeded && result.toolCallsSupported == true -> MaterialTheme.colorScheme.primary
                            result.connectionSucceeded -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.error
                        }
                        Text(result.message, color = resultColor)
                    }
                }
            }
        }
        MiuixCardSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("模型配置", style = MaterialTheme.typography.titleLarge)
                Text("支持用户自带 API Key 的 OpenAI-compatible 模型；密钥只会保存在 Android 安全存储。")
                if (profiles.isEmpty()) {
                    Text("还没有模型配置。首次发送消息时可以从这里开始配置。")
                } else {
                    profiles.forEach { profile ->
                        HorizontalDivider()
                        Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
                        Text("${profile.providerName} · ${profile.modelId}")
                        TextButton(onClick = { onSettingsChanged(settings.copy(defaultModelProfileId = profile.id)) }) {
                            Text(if (settings.defaultModelProfileId == profile.id) "当前全局模型" else "设为全局模型")
                        }
                    }
                }
            }
        }
        MiuixCardSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("本地模型", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { artifactEditing = !artifactEditing }) { Text(if (artifactEditing) "取消" else "添加目录项") }
                }
                Text("目录项必须先确认许可证；模型文件不会随数据导出包复制。默认下载使用 Wi-Fi，可显式允许移动网络。")
                if (artifactEditing) {
                    OutlinedTextField(artifactName, { artifactName = it }, Modifier.fillMaxWidth(), label = { Text("模型名称") }, singleLine = true)
                    OutlinedTextField(artifactLogicalId, { artifactLogicalId = it }, Modifier.fillMaxWidth(), label = { Text("逻辑模型 ID（不同版本保持一致）") }, singleLine = true)
                    OutlinedTextField(artifactVersion, { artifactVersion = it }, Modifier.fillMaxWidth(), label = { Text("版本") }, singleLine = true)
                    OutlinedTextField(artifactFileName, { artifactFileName = it }, Modifier.fillMaxWidth(), label = { Text("文件名（.litertlm）") }, singleLine = true)
                    OutlinedTextField(artifactSize, { artifactSize = it }, Modifier.fillMaxWidth(), label = { Text("大小（字节）") }, singleLine = true)
                    OutlinedTextField(artifactSha256, { artifactSha256 = it }, Modifier.fillMaxWidth(), label = { Text("SHA-256") }, singleLine = true)
                    OutlinedTextField(artifactLicense, { artifactLicense = it }, Modifier.fillMaxWidth(), label = { Text("许可证") })
                    OutlinedTextField(artifactSourceUrl, { artifactSourceUrl = it }, Modifier.fillMaxWidth(), label = { Text("官方下载地址（可选）") }, singleLine = true)
                    OutlinedTextField(artifactBackend, { artifactBackend = it }, Modifier.fillMaxWidth(), label = { Text("后端（auto / cpu / gpu）") }, singleLine = true)
                    OutlinedTextField(artifactContextLength, { artifactContextLength = it }, Modifier.fillMaxWidth(), label = { Text("上下文长度") }, singleLine = true)
                    OutlinedTextField(artifactRequirements, { artifactRequirements = it }, Modifier.fillMaxWidth(), label = { Text("设备要求") })
                    Button(
                        onClick = {
                            val now = nowMillis()
                            scope.launch {
                                graph.localModelGateway.registerCatalogEntry(
                                    com.lhy.yumerin.core.model.LocalModelArtifact(
                                        id = newEntityId("local-model"),
                                        logicalModelId = artifactLogicalId.trim().ifBlank {
                                            artifactName.trim().lowercase().replace(Regex("[^a-z0-9._-]+"), "-")
                                        },
                                        displayName = artifactName.trim(), version = artifactVersion.trim(),
                                        fileName = artifactFileName.trim().ifBlank { "model.litertlm" }, fileSizeBytes = artifactSize.toLongOrNull() ?: 0L,
                                        sha256 = artifactSha256.trim(), license = artifactLicense.trim(), sourceUrl = artifactSourceUrl.trim().ifBlank { null },
                                        backend = artifactBackend.trim().ifBlank { "auto" }, contextLength = artifactContextLength.toIntOrNull(),
                                        state = com.lhy.yumerin.core.model.LocalModelState.CATALOGUED, localPath = null, assignedProfileCount = 0,
                                        createdAt = now, updatedAt = now, deviceRequirements = artifactRequirements.trim()
                                    )
                                )
                                artifactEditing = false
                            }
                        },
                        enabled = artifactName.isNotBlank() && artifactVersion.isNotBlank() && artifactSha256.length == 64,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("保存目录项") }
                }
                if (localArtifacts.isEmpty()) {
                    Text("当前没有已导入的 .litertlm 模型。")
                } else {
                    localArtifacts.forEach { artifact ->
                        HorizontalDivider()
                        Text(artifact.displayName, style = MaterialTheme.typography.titleMedium)
                        Text("${artifact.version} · ${artifact.state} · ${artifact.fileSizeBytes} bytes · ${artifact.backend}")
                        val assignmentCount = profiles.count {
                            it.enabled && it.localModelId != null &&
                                (it.localModelId == artifact.logicalModelId || it.localModelId == artifact.id)
                        }
                        Text("逻辑模型：${artifact.logicalModelId} · 许可证：${if (artifact.licenseAcknowledged) "已确认" else "未确认"}；分配：$assignmentCount")
                        artifact.diagnosticReason?.let { Text("诊断：$it", color = MaterialTheme.colorScheme.error) }
                        SwitchRow("确认许可证和来源", artifact.licenseAcknowledged) {
                            scope.launch {
                                graph.models.saveLocalArtifact(artifact.copy(licenseAcknowledged = it, updatedAt = nowMillis()))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (artifact.state == com.lhy.yumerin.core.model.LocalModelState.CATALOGUED || artifact.state == com.lhy.yumerin.core.model.LocalModelState.FAILED) {
                                TextButton(onClick = {
                                    onPickLocalModel { path ->
                                        if (path != null) scope.launch {
                                            runCatching {
                                                graph.localModelGateway.importArtifact(artifact, path)
                                            }.onFailure { modelActionMessage = it.message ?: "导入失败" }
                                        }
                                    }
                                }) { Text("导入文件") }
                                if (artifact.sourceUrl != null && artifact.licenseAcknowledged) {
                                    TextButton(onClick = { scope.launch { graph.localModelGateway.startDownload(artifact.id, allowMeteredDownload) } }) { Text("下载") }
                                }
                            }
                            if (artifact.state == com.lhy.yumerin.core.model.LocalModelState.DOWNLOADING && artifact.sourceUrl != null) {
                                TextButton(onClick = {
                                    scope.launch { graph.localModelGateway.startDownload(artifact.id, allowMeteredDownload) }
                                }) { Text("继续下载") }
                            }
                            if (artifact.state == com.lhy.yumerin.core.model.LocalModelState.DOWNLOADING) {
                                TextButton(onClick = { scope.launch { graph.localModelGateway.pauseDownload(artifact.id) } }) { Text("暂停") }
                                TextButton(onClick = {
                                    scope.launch {
                                        graph.localModelGateway.cancelDownload(artifact.id)
                                        modelActionMessage = "下载已取消，已保留目录项。"
                                    }
                                }) { Text("取消") }
                            }
                            if (artifact.state == com.lhy.yumerin.core.model.LocalModelState.INSTALLED) {
                                TextButton(onClick = { scope.launch { graph.localModelGateway.activate(artifact.id) } }) { Text("激活") }
                            }
                            if (artifact.state == com.lhy.yumerin.core.model.LocalModelState.ACTIVE) {
                                TextButton(onClick = {
                                    scope.launch {
                                        val now = nowMillis()
                                        graph.models.saveProfile(
                                            ModelProfile(
                                                id = newEntityId("model"),
                                                displayName = "${artifact.displayName} ${artifact.version}",
                                                providerKind = com.lhy.yumerin.core.model.ModelProviderKind.LITERT_LM,
                                                providerName = "LiteRT-LM",
                                                baseUrl = null,
                                                modelId = artifact.logicalModelId,
                                                credentialAlias = null,
                                                contextLength = artifact.contextLength,
                                                contextLengthMode = if (artifact.contextLength == null) {
                                                    com.lhy.yumerin.core.model.ContextLengthMode.AUTO
                                                } else com.lhy.yumerin.core.model.ContextLengthMode.KNOWN,
                                                generationParameters = com.lhy.yumerin.core.model.GenerationParameters(),
                                                localModelId = artifact.logicalModelId,
                                                localModelVersion = null,
                                                enabled = true,
                                                createdAt = now,
                                                updatedAt = now,
                                                localBackend = artifact.backend
                                            )
                                        )
                                        graph.models.saveLocalArtifact(
                                            artifact.copy(assignedProfileCount = artifact.assignedProfileCount + 1, updatedAt = now)
                                        )
                                        modelActionMessage = "已创建本地模型配置，可在角色资料中绑定。"
                                    }
                                }) { Text("绑定为模型") }
                            }
                            if (assignmentCount == 0) {
                                TextButton(onClick = {
                                    scope.launch {
                                        runCatching { graph.localModelGateway.remove(artifact.id) }
                                            .onFailure { modelActionMessage = it.message ?: "删除失败" }
                                    }
                                }) { Text("删除") }
                            }
                        }
                    }
                }
                SwitchRow("允许移动网络下载", allowMeteredDownload) { allowMeteredDownload = it }
                modelActionMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}
