package com.lhy.yumerin

import com.lhy.yumerin.designsystem.YumerinSpacing

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.lhy.yumerin.core.model.AppSettings
import com.lhy.yumerin.core.model.ContextLengthMode
import com.lhy.yumerin.core.model.GenerationParameters
import com.lhy.yumerin.core.model.ModelProfile
import com.lhy.yumerin.core.model.ModelProviderKind
import com.lhy.yumerin.core.model.newEntityId
import com.lhy.yumerin.designsystem.OverlayDropdownMenu
import com.lhy.yumerin.designsystem.YumerinButton as MiuixButton
import com.lhy.yumerin.domain.AppGraph
import com.lhy.yumerin.domain.repository.ModelConfigurationTestResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.HorizontalDivider as HorizontalDivider
import top.yukonga.miuix.kmp.basic.DropdownEntry as MiuixDropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem as MiuixDropdownItem
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.preference.ArrowPreference as MiuixArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ModelsPage(
    graph: AppGraph,
    profiles: List<ModelProfile>,
    localArtifactCount: Int,
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onOpenLocalModels: () -> Unit,
    onBack: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }

    MiuixScaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { _ ->
        PageColumn {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Field)
            ) {
                MiuixBackButton(onClick = onBack)
                MiuixText("模型", modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.title2)
                if (editing) {
                    MiuixTextButton(
                        text = "取消",
                        onClick = { editing = false },
                        enabled = true
                    )
                } else {
                    MiuixGlassIconButton(
                        icon = YumerinAddIcon,
                        contentDescription = "添加远端模型",
                        onClick = { editing = true }
                    )
                }
            }

            if (editing) {
                RemoteModelProfileForm(
                    graph = graph,
                    shouldSetAsDefault = settings.defaultModelProfileId == null,
                    onSettingsChanged = onSettingsChanged,
                    settings = settings,
                    onSaved = { editing = false },
                    onCancel = { editing = false }
                )
            } else {
                RemoteModelsOverview(
                    profiles = profiles,
                    localArtifactCount = localArtifactCount,
                    settings = settings,
                    onSettingsChanged = onSettingsChanged,
                    onAddModel = { editing = true },
                    onOpenLocalModels = onOpenLocalModels
                )
            }
        }
    }
}

@Composable
private fun RemoteModelsOverview(
    profiles: List<ModelProfile>,
    localArtifactCount: Int,
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onAddModel: () -> Unit,
    onOpenLocalModels: () -> Unit
) {
    MiuixCardSurface(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(YumerinSpacing.CardInset),
            verticalArrangement = Arrangement.spacedBy(YumerinSpacing.FormField)
        ) {
            MiuixText("远端模型", style = MiuixTheme.textStyles.title2)
            MiuixText(
                "连接 OpenAI-compatible 服务。API Key 由你提供，只保存在此设备的安全存储中。",
                color = MiuixTheme.colorScheme.onSurfaceVariantActions
            )

            if (profiles.isEmpty()) {
                MiuixText("还没有远端模型配置。添加的第一个模型会设为全局默认。")
                MiuixButton(
                    onClick = onAddModel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MiuixText("添加远端模型", style = MiuixTheme.textStyles.button)
                }
            } else {
                val selectedDefault = profiles.firstOrNull { it.id == settings.defaultModelProfileId }
                OverlayDropdownMenu(
                    entry = MiuixDropdownEntry(
                        items = profiles.map { profile ->
                            MiuixDropdownItem(
                                text = profile.displayName,
                                selected = profile.id == settings.defaultModelProfileId,
                                onClick = {
                                    onSettingsChanged(settings.copy(defaultModelProfileId = profile.id))
                                }
                            )
                        }
                    ),
                    title = "全局默认模型",
                    summary = if (selectedDefault == null) {
                        "请选择；未单独指定模型的角色会使用这里的模型"
                    } else {
                        "未单独指定模型的角色会使用这里的模型"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    renderInRootScaffold = false
                )
                HorizontalDivider()
                MiuixText("已配置的模型", style = MiuixTheme.textStyles.title3)
                profiles.forEach { profile ->
                    Column(verticalArrangement = Arrangement.spacedBy(YumerinSpacing.CompactRow)) {
                        MiuixText(profile.displayName, style = MiuixTheme.textStyles.subtitle)
                        MiuixText(
                            "${profile.providerName} · ${profile.modelId}",
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                        if (settings.defaultModelProfileId == profile.id) {
                            MiuixText("全局默认", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (profile != profiles.last()) HorizontalDivider()
                }
            }
        }
    }

    MiuixCardSurface(Modifier.fillMaxWidth()) {
        MiuixText(
            text = "角色可单独指定模型；未指定时跟随全局默认。",
            modifier = Modifier.padding(YumerinSpacing.CardInset),
            color = MiuixTheme.colorScheme.onSurfaceVariantActions
        )
    }

    MiuixArrowPreference(
        title = "本地模型",
        summary = if (localArtifactCount == 0) {
            "管理导入、下载和设备端模型"
        } else {
            "$localArtifactCount 个模型条目 · 管理导入、下载和设备端模型"
        },
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenLocalModels
    )
}

@Composable
private fun RemoteModelProfileForm(
    graph: AppGraph,
    settings: AppSettings,
    shouldSetAsDefault: Boolean,
    onSettingsChanged: (AppSettings) -> Unit,
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
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
    var showAdvanced by remember { mutableStateOf(false) }
    var modelTestRunning by remember { mutableStateOf(false) }
    var modelTestResult by remember { mutableStateOf<ModelConfigurationTestResult?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }

    val validBaseUrl = baseUrl.trim().startsWith("https://", ignoreCase = true) ||
        baseUrl.trim().startsWith("http://", ignoreCase = true)
    val canSubmit = !modelTestRunning && validBaseUrl && modelId.isNotBlank() && apiKey.isNotBlank()

    MiuixCardSurface(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(YumerinSpacing.CardInset),
            verticalArrangement = Arrangement.spacedBy(YumerinSpacing.FormField)
        ) {
            MiuixText("新增远端模型", style = MiuixTheme.textStyles.title2)
            MiuixText("先填写连接信息。测试连接可选；保存后可用于全局默认或角色单独指定。")
            MiuixText("连接信息", style = MiuixTheme.textStyles.subtitle)

            MiuixTextField(
                value = displayName,
                onValueChange = { displayName = it },
                modifier = Modifier.fillMaxWidth(),
                label = "显示名称（可选）",
                singleLine = true
            )
            MiuixTextField(
                value = providerName,
                onValueChange = { providerName = it },
                modifier = Modifier.fillMaxWidth(),
                label = "提供商名称（可选）",
                singleLine = true
            )
            MiuixTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it; modelTestResult = null; saveError = null },
                modifier = Modifier.fillMaxWidth(),
                label = "Base URL",
                singleLine = true
            )
            MiuixTextField(
                value = modelId,
                onValueChange = { modelId = it; modelTestResult = null; saveError = null },
                modifier = Modifier.fillMaxWidth(),
                label = "模型 ID",
                singleLine = true
            )
            MiuixTextField(
                value = apiKey,
                onValueChange = { apiKey = it; modelTestResult = null; saveError = null },
                modifier = Modifier.fillMaxWidth(),
                label = "API Key",
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            if (baseUrl.isNotBlank() && !validBaseUrl) {
                MiuixText(
                    "Base URL 需要以 http:// 或 https:// 开头。",
                    color = MaterialTheme.colorScheme.error
                )
            }

            MiuixTextButton(
                text = if (showAdvanced) "收起高级参数" else "高级参数",
                onClick = { showAdvanced = !showAdvanced },
                modifier = Modifier.fillMaxWidth()
            )
            if (showAdvanced) {
                MiuixText(
                    "这些参数可留空，模型会使用服务端或运行时默认值。",
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                MiuixTextField(contextLength, { contextLength = it }, Modifier.fillMaxWidth(), label = "上下文长度", singleLine = true)
                MiuixTextField(temperature, { temperature = it }, Modifier.fillMaxWidth(), label = "Temperature", singleLine = true)
                MiuixTextField(topP, { topP = it }, Modifier.fillMaxWidth(), label = "Top P", singleLine = true)
                MiuixTextField(maxOutputTokens, { maxOutputTokens = it }, Modifier.fillMaxWidth(), label = "最大输出 tokens", singleLine = true)
                MiuixTextField(stopSequences, { stopSequences = it }, Modifier.fillMaxWidth(), label = "Stop sequences（逗号分隔）")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.FormField)) {
                MiuixTextButton(
                    text = if (modelTestRunning) "测试中" else "测试连接",
                    onClick = {
                        val testBaseUrl = baseUrl.trim()
                        val testModelId = modelId.trim()
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
                    modifier = Modifier.weight(1f),
                    enabled = canSubmit
                )
                MiuixButton(
                    onClick = {
                        val profileId = newEntityId("model")
                        val credentialAlias = newEntityId("credential")
                        val now = nowMillis()
                        val customContextLength = contextLength.toIntOrNull()
                        val profile = ModelProfile(
                            id = profileId,
                            displayName = displayName.trim().ifBlank { modelId.trim() },
                            providerKind = ModelProviderKind.OPENAI_COMPATIBLE,
                            providerName = providerName.trim().ifBlank { "OpenAI-compatible" },
                            baseUrl = baseUrl.trim().trimEnd('/'),
                            modelId = modelId.trim(),
                            credentialAlias = credentialAlias,
                            contextLength = customContextLength,
                            contextLengthMode = if (customContextLength == null) ContextLengthMode.AUTO else ContextLengthMode.CUSTOM,
                            generationParameters = GenerationParameters(
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
                        scope.launch {
                            runCatching {
                                graph.credentials.save(credentialAlias, apiKey)
                                graph.models.saveProfile(profile)
                                if (shouldSetAsDefault) {
                                    onSettingsChanged(settings.copy(defaultModelProfileId = profileId))
                                }
                            }.onSuccess {
                                onSaved()
                            }.onFailure {
                                saveError = it.message ?: "保存模型配置失败。"
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = canSubmit
                ) {
                    MiuixText(
                        if (shouldSetAsDefault) "保存并设为默认" else "保存模型",
                        style = MiuixTheme.textStyles.button
                    )
                }
            }

            MiuixText(
                "测试会发送两条短请求，可能产生少量费用；测试不会保存配置，也可以跳过。API Key 仅保存到设备安全存储。",
                color = MiuixTheme.colorScheme.onSurfaceVariantActions
            )
            modelTestResult?.let { result ->
                val resultColor = when {
                    result.connectionSucceeded && result.toolCallsSupported == true -> MaterialTheme.colorScheme.primary
                    result.connectionSucceeded -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                }
                MiuixText(result.message, color = resultColor)
            }
            saveError?.let { MiuixText(it, color = MaterialTheme.colorScheme.error) }
            MiuixTextButton(text = "返回模型列表", onClick = onCancel, modifier = Modifier.fillMaxWidth())
        }
    }
}
