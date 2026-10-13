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
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.lhy.yumerin.core.model.*
import com.lhy.yumerin.data.memory.*
import com.lhy.yumerin.designsystem.*
import com.lhy.yumerin.domain.*
import com.lhy.yumerin.domain.agent.*
import com.lhy.yumerin.domain.memory.*
import com.lhy.yumerin.domain.repository.*
import com.lhy.yumerin.domain.usecase.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Clock

@Composable
internal fun SettingsPage(
    backdrop: Backdrop,
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onOpenModels: () -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenDataManagement: () -> Unit,
    onOpenCharacterContinuity: () -> Unit,
    onBack: () -> Unit
) {
    var quietStartText by remember(settings.quietStartMinutes) { mutableStateOf(settings.quietStartMinutes.toClockText()) }
    var quietEndText by remember(settings.quietEndMinutes) { mutableStateOf(settings.quietEndMinutes.toClockText()) }
    var dailyLimitText by remember(settings.dailyProactiveLimit) { mutableStateOf(settings.dailyProactiveLimit.toString()) }
    var compressionText by remember(settings.contextCompressionThreshold) {
        mutableStateOf((settings.contextCompressionThreshold * 100).toInt().toString())
    }
    PageColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassBackButton(backdrop = backdrop, onClick = onBack)
            Column(
                modifier = Modifier.padding(start = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("设置", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "按类别调整 Yumerin 的行为",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f)
                )
            }
        }

        SettingsSection(
            backdrop = backdrop,
            title = "外观",
            description = "主题和页面动效"
        ) {
            SettingsLabeledContent(
                title = "深色模式",
                description = "选择应用的颜色主题"
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { onSettingsChanged(settings.copy(themeMode = mode)) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size)
                        ) { Text(mode.label()) }
                    }
                }
            }
            SettingsSwitchRow(
                title = "减少动效",
                description = "降低页面切换和交互动画",
                checked = settings.reducedMotionEnabled,
                onCheckedChange = { onSettingsChanged(settings.copy(reducedMotionEnabled = it)) }
            )
        }

        SettingsSection(
            backdrop = backdrop,
            title = "陪伴与智能体",
            description = "记忆维护和回复呈现方式"
        ) {
            SettingsSwitchRow(
                title = "自动维护长期记忆",
                description = "按角色分别整理重要偏好、关系和事件",
                checked = settings.autoMemoryEnabled,
                onCheckedChange = { onSettingsChanged(settings.copy(autoMemoryEnabled = it)) }
            )
            SettingsLabeledContent(
                title = "智能体步骤可见程度",
                description = "默认只显示最终回复"
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AgentVisibility.entries.forEachIndexed { index, visibility ->
                        SegmentedButton(
                            selected = settings.agentVisibility == visibility,
                            onClick = { onSettingsChanged(settings.copy(agentVisibility = visibility)) },
                            shape = SegmentedButtonDefaults.itemShape(index, AgentVisibility.entries.size)
                        ) { Text(visibility.label()) }
                    }
                }
            }
            SettingsSwitchRow(
                title = "流式回复",
                description = "边生成边显示角色回复",
                checked = settings.streamingEnabled,
                onCheckedChange = { onSettingsChanged(settings.copy(streamingEnabled = it)) }
            )
        }

        SettingsSection(
            backdrop = backdrop,
            title = "通知与后台",
            description = "主动消息、保活和静默时间"
        ) {
            SettingsSwitchRow(
                title = "主动消息",
                description = "允许角色按计划主动发起对话",
                checked = settings.proactiveMessagesEnabled,
                onCheckedChange = { onSettingsChanged(settings.copy(proactiveMessagesEnabled = it)) }
            )
            SettingsSwitchRow(
                title = "后台陪伴",
                description = "允许后台任务维持提醒和主动互动",
                checked = settings.backgroundCompanionEnabled,
                onCheckedChange = { onSettingsChanged(settings.copy(backgroundCompanionEnabled = it)) }
            )
            SettingsSwitchRow(
                title = "静默时段",
                description = "静默期间不通知，但仍保存消息",
                checked = settings.quietTimeEnabled,
                onCheckedChange = { onSettingsChanged(settings.copy(quietTimeEnabled = it)) }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    quietStartText,
                    { quietStartText = it },
                    Modifier.weight(1f),
                    label = { Text("开始 HH:mm") },
                    singleLine = true,
                    enabled = settings.quietTimeEnabled
                )
                OutlinedTextField(
                    quietEndText,
                    { quietEndText = it },
                    Modifier.weight(1f),
                    label = { Text("结束 HH:mm") },
                    singleLine = true,
                    enabled = settings.quietTimeEnabled
                )
            }
            OutlinedTextField(
                dailyLimitText,
                { dailyLimitText = it },
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                label = { Text("每日主动消息上限") },
                supportingText = { Text("范围 1–20，当前默认 3 条") },
                singleLine = true
            )
            Button(
                onClick = {
                    val start = quietStartText.toClockMinutes()
                    val end = quietEndText.toClockMinutes()
                    if (start != null && end != null) {
                        onSettingsChanged(
                            settings.copy(
                                quietStartMinutes = start,
                                quietEndMinutes = end,
                                dailyProactiveLimit = dailyLimitText.toIntOrNull()
                                    ?.coerceIn(1, 20)
                                    ?: settings.dailyProactiveLimit
                            )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) { Text("保存通知设置") }
            TextButton(
                onClick = onOpenSchedules,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
            ) { Text("管理主动消息计划") }
            TextButton(
                onClick = onOpenCharacterContinuity,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
            ) { Text("查看角色连续性") }
        }

        SettingsSection(
            backdrop = backdrop,
            title = "上下文",
            description = "长对话中压缩较早内容，保留近期上下文"
        ) {
            OutlinedTextField(
                compressionText,
                { compressionText = it },
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                label = { Text("压缩阈值（百分比）") },
                supportingText = { Text("可设置 50–95，默认 80") },
                singleLine = true
            )
            Button(
                onClick = {
                    onSettingsChanged(
                        settings.copy(
                            contextCompressionThreshold = (compressionText.toFloatOrNull()?.div(100f)
                                ?: settings.contextCompressionThreshold).coerceIn(0.5f, 0.95f)
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) { Text("保存上下文设置") }
        }

        SettingsSection(
            backdrop = backdrop,
            title = "模型与数据",
            description = "模型配置和本地数据管理"
        ) {
            SettingRow("模型", "配置和本地模型", onClick = onOpenModels)
            SettingRow("数据管理", "导出、导入、清除", onClick = onOpenDataManagement)
        }
    }
}

@Composable
private fun SettingsSection(
    backdrop: Backdrop,
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        GlassSurface(
            backdrop,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            surfaceAlpha = 0.62f
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsLabeledContent(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
