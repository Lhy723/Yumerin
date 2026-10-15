package com.lhy.yumerin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lhy.yumerin.core.model.AppSettings
import com.lhy.yumerin.core.model.ContextLengthMode
import com.lhy.yumerin.core.model.GenerationParameters
import com.lhy.yumerin.core.model.LocalModelArtifact
import com.lhy.yumerin.core.model.LocalModelState
import com.lhy.yumerin.core.model.ModelProfile
import com.lhy.yumerin.core.model.ModelProviderKind
import com.lhy.yumerin.core.model.newEntityId
import com.lhy.yumerin.domain.AppGraph
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LocalModelsPage(
    graph: AppGraph,
    profiles: List<ModelProfile>,
    localArtifacts: List<LocalModelArtifact>,
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onPickLocalModel: ((onSelected: (String?) -> Unit) -> Unit),
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
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
    var showAdvancedMetadata by remember { mutableStateOf(false) }
    var allowMeteredDownload by remember { mutableStateOf(false) }
    var modelActionMessage by remember { mutableStateOf<String?>(null) }

    MiuixScaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { _ ->
        PageColumn {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MiuixBackButton(onClick = onBack)
                MiuixText("本地模型", style = MiuixTheme.textStyles.title2)
            }

            MiuixCardSurface(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiuixText("设备端模型", style = MiuixTheme.textStyles.title2)
                    MiuixText(
                        "先登记模型信息，再确认许可并导入文件或下载来源。模型文件只保存在本设备。",
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
            }

            MiuixTextButton(
                text = if (editing) "取消添加" else "添加本地模型信息",
                onClick = { editing = !editing },
                modifier = Modifier.fillMaxWidth()
            )

            if (editing) {
                MiuixCardSurface(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MiuixText("模型信息", style = MiuixTheme.textStyles.subtitle)
                        MiuixTextField(artifactName, { artifactName = it }, Modifier.fillMaxWidth(), label = "模型名称", singleLine = true)
                        MiuixTextField(artifactVersion, { artifactVersion = it }, Modifier.fillMaxWidth(), label = "版本", singleLine = true)
                        MiuixTextField(artifactLicense, { artifactLicense = it }, Modifier.fillMaxWidth(), label = "许可证", singleLine = true)
                        MiuixTextField(artifactSourceUrl, { artifactSourceUrl = it }, Modifier.fillMaxWidth(), label = "官方来源地址（可选）", singleLine = true)

                        MiuixTextButton(
                            text = if (showAdvancedMetadata) "收起校验与运行信息" else "校验与运行信息",
                            onClick = { showAdvancedMetadata = !showAdvancedMetadata },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (showAdvancedMetadata) {
                            MiuixText(
                                "SHA-256 用于确认导入文件与模型来源一致；其余信息可用于运行时选择。",
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                            MiuixTextField(artifactLogicalId, { artifactLogicalId = it }, Modifier.fillMaxWidth(), label = "逻辑模型 ID（可选）", singleLine = true)
                            MiuixTextField(artifactFileName, { artifactFileName = it }, Modifier.fillMaxWidth(), label = "文件名（默认 model.litertlm）", singleLine = true)
                            MiuixTextField(artifactSize, { artifactSize = it }, Modifier.fillMaxWidth(), label = "文件大小（字节，可选）", singleLine = true)
                            MiuixTextField(artifactSha256, { artifactSha256 = it }, Modifier.fillMaxWidth(), label = "SHA-256（64 位）", singleLine = true)
                            MiuixTextField(artifactBackend, { artifactBackend = it }, Modifier.fillMaxWidth(), label = "运行后端（auto / cpu / gpu）", singleLine = true)
                            MiuixTextField(artifactContextLength, { artifactContextLength = it }, Modifier.fillMaxWidth(), label = "上下文长度（可选）", singleLine = true)
                            MiuixTextField(artifactRequirements, { artifactRequirements = it }, Modifier.fillMaxWidth(), label = "设备要求（可选）")
                        }

                        MiuixButton(
                            onClick = {
                                val now = nowMillis()
                                val logicalId = artifactLogicalId.trim().ifBlank {
                                    artifactName.trim().lowercase().replace(Regex("[^a-z0-9._-]+"), "-")
                                }
                                scope.launch {
                                    graph.localModelGateway.registerCatalogEntry(
                                        LocalModelArtifact(
                                            id = newEntityId("local-model"),
                                            logicalModelId = logicalId,
                                            displayName = artifactName.trim(),
                                            version = artifactVersion.trim(),
                                            fileName = artifactFileName.trim().ifBlank { "model.litertlm" },
                                            fileSizeBytes = artifactSize.toLongOrNull() ?: 0L,
                                            sha256 = artifactSha256.trim(),
                                            license = artifactLicense.trim(),
                                            sourceUrl = artifactSourceUrl.trim().ifBlank { null },
                                            backend = artifactBackend.trim().ifBlank { "auto" },
                                            contextLength = artifactContextLength.toIntOrNull(),
                                            state = LocalModelState.CATALOGUED,
                                            localPath = null,
                                            assignedProfileCount = 0,
                                            createdAt = now,
                                            updatedAt = now,
                                            deviceRequirements = artifactRequirements.trim()
                                        )
                                    )
                                    editing = false
                                    modelActionMessage = "已添加模型信息；接下来可确认许可并导入文件或下载。"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = artifactName.isNotBlank() && artifactVersion.isNotBlank() && artifactSha256.length == 64,
                            minWidth = 0.dp,
                            minHeight = 48.dp,
                            colors = MiuixButtonDefaults.buttonColorsPrimary()
                        ) {
                            MiuixText("保存模型信息", style = MiuixTheme.textStyles.button)
                        }
                        if (artifactSha256.length != 64) {
                            MiuixText("需要 64 位 SHA-256 才能保存并校验模型文件。", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (localArtifacts.isEmpty()) {
                MiuixCardSurface(Modifier.fillMaxWidth()) {
                    MiuixText(
                        "当前没有本地模型。添加模型信息后，可以导入 .litertlm 文件或下载官方来源。",
                        modifier = Modifier.padding(18.dp)
                    )
                }
            } else {
                localArtifacts.forEach { artifact ->
                    val assignmentCount = profiles.count {
                        it.enabled && it.localModelId != null &&
                            (it.localModelId == artifact.logicalModelId || it.localModelId == artifact.id)
                    }
                    MiuixCardSurface(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MiuixText(artifact.displayName, style = MiuixTheme.textStyles.subtitle)
                            MiuixText(
                                "${artifact.version} · ${artifact.state.toUiLabel()} · ${artifact.fileSizeBytes.toSizeLabel()} · ${artifact.backend.uppercase()}",
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                            if (artifact.license.isNotBlank()) {
                                SwitchRow("我已确认许可证与来源", artifact.licenseAcknowledged) { checked ->
                                    scope.launch {
                                        graph.models.saveLocalArtifact(
                                            artifact.copy(licenseAcknowledged = checked, updatedAt = nowMillis())
                                        )
                                    }
                                }
                            }
                            artifact.diagnosticReason?.let {
                                MiuixText(it, color = MaterialTheme.colorScheme.error)
                            }

                            when (artifact.state) {
                                LocalModelState.CATALOGUED, LocalModelState.FAILED -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        MiuixTextButton(
                                            text = "导入文件",
                                            onClick = {
                                                onPickLocalModel { path ->
                                                    if (path != null) scope.launch {
                                                        runCatching {
                                                            graph.localModelGateway.importArtifact(artifact, path)
                                                        }.onFailure { modelActionMessage = it.message ?: "导入失败。" }
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (artifact.sourceUrl != null && artifact.licenseAcknowledged) {
                                            MiuixTextButton(
                                                text = "下载",
                                                onClick = {
                                                    scope.launch {
                                                        runCatching {
                                                            graph.localModelGateway.startDownload(artifact.id, allowMeteredDownload)
                                                        }.onFailure { modelActionMessage = it.message ?: "下载失败。" }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }

                                LocalModelState.DOWNLOADING -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        MiuixTextButton(
                                            text = "继续下载",
                                            onClick = {
                                                scope.launch {
                                                    runCatching {
                                                        graph.localModelGateway.startDownload(artifact.id, allowMeteredDownload)
                                                    }.onFailure { modelActionMessage = it.message ?: "继续下载失败。" }
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        MiuixTextButton(
                                            text = "暂停",
                                            onClick = { scope.launch { graph.localModelGateway.pauseDownload(artifact.id) } },
                                            modifier = Modifier.weight(1f)
                                        )
                                        MiuixTextButton(
                                            text = "取消下载",
                                            onClick = {
                                                scope.launch {
                                                    graph.localModelGateway.cancelDownload(artifact.id)
                                                    modelActionMessage = "下载已取消，模型信息已保留。"
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                LocalModelState.INSTALLED -> MiuixTextButton(
                                    text = "激活模型",
                                    onClick = {
                                        scope.launch {
                                            runCatching { graph.localModelGateway.activate(artifact.id) }
                                                .onFailure { modelActionMessage = it.message ?: "激活失败。" }
                                        }
                                    }
                                )

                                LocalModelState.ACTIVE -> {
                                    if (assignmentCount == 0) {
                                        val shouldSetAsDefault = settings.defaultModelProfileId == null
                                        MiuixButton(
                                            onClick = {
                                                val profileId = newEntityId("model")
                                                val now = nowMillis()
                                                val profile = ModelProfile(
                                                    id = profileId,
                                                    displayName = "${artifact.displayName} ${artifact.version}",
                                                    providerKind = ModelProviderKind.LITERT_LM,
                                                    providerName = "LiteRT-LM",
                                                    baseUrl = null,
                                                    modelId = artifact.logicalModelId,
                                                    credentialAlias = null,
                                                    contextLength = artifact.contextLength,
                                                    contextLengthMode = if (artifact.contextLength == null) {
                                                        ContextLengthMode.AUTO
                                                    } else ContextLengthMode.KNOWN,
                                                    generationParameters = GenerationParameters(),
                                                    localModelId = artifact.logicalModelId,
                                                    localModelVersion = null,
                                                    enabled = true,
                                                    createdAt = now,
                                                    updatedAt = now,
                                                    localBackend = artifact.backend
                                                )
                                                scope.launch {
                                                    graph.models.saveProfile(profile)
                                                    graph.models.saveLocalArtifact(
                                                        artifact.copy(
                                                            assignedProfileCount = artifact.assignedProfileCount + 1,
                                                            updatedAt = now
                                                        )
                                                    )
                                                    if (shouldSetAsDefault) {
                                                        onSettingsChanged(settings.copy(defaultModelProfileId = profileId))
                                                    }
                                                    modelActionMessage = if (shouldSetAsDefault) {
                                                        "已添加模型配置，并设为全局默认。"
                                                    } else {
                                                        "已添加模型配置，可在角色资料中指定。"
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            minWidth = 0.dp,
                                            minHeight = 48.dp,
                                            colors = MiuixButtonDefaults.buttonColorsPrimary()
                                        ) {
                                            MiuixText(
                                                if (shouldSetAsDefault) "添加并设为默认" else "添加到模型配置",
                                                style = MiuixTheme.textStyles.button
                                            )
                                        }
                                    } else {
                                        MiuixText("已添加到 $assignmentCount 个模型配置")
                                    }
                                }

                                LocalModelState.VERIFYING, LocalModelState.REMOVING -> Unit
                            }

                            if (assignmentCount == 0) {
                                HorizontalDivider()
                                MiuixTextButton(
                                    text = "删除模型信息",
                                    onClick = {
                                        scope.launch {
                                            runCatching { graph.localModelGateway.remove(artifact.id) }
                                                .onFailure { modelActionMessage = it.message ?: "删除失败。" }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            SwitchRow("允许移动网络下载", allowMeteredDownload) { allowMeteredDownload = it }
            modelActionMessage?.let { MiuixText(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

private fun LocalModelState.toUiLabel(): String = when (this) {
    LocalModelState.CATALOGUED -> "待导入"
    LocalModelState.DOWNLOADING -> "下载中"
    LocalModelState.VERIFYING -> "校验中"
    LocalModelState.INSTALLED -> "已安装"
    LocalModelState.ACTIVE -> "已激活"
    LocalModelState.FAILED -> "失败"
    LocalModelState.REMOVING -> "移除中"
}

private fun Long.toSizeLabel(): String = when {
    this <= 0L -> "大小未知"
    this >= 1024L * 1024 * 1024 -> "${this / (1024L * 1024 * 1024)} GB"
    this >= 1024L * 1024 -> "${this / (1024L * 1024)} MB"
    else -> "${this / 1024L} KB"
}
