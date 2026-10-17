package com.lhy.yumerin

import com.lhy.yumerin.designsystem.YumerinSpacing

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
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
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry as MiuixDropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem as MiuixDropdownItem
import top.yukonga.miuix.kmp.basic.NumberPicker as MiuixNumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTitle as MiuixSmallTitle
import top.yukonga.miuix.kmp.preference.SliderPreference as MiuixSliderPreference
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.preference.ArrowPreference as MiuixArrowPreference
import top.yukonga.miuix.kmp.overlay.OverlayDialog as MiuixOverlayDialog
import kotlin.math.roundToInt

internal enum class SettingsCategory(val title: String) {
    APPEARANCE("外观"),
    COMPANION("陪伴与智能体"),
    NOTIFICATIONS("通知与后台"),
    CONTEXT("上下文")
}

@Composable
internal fun SettingsPage(
    settings: AppSettings,
    onOpenCategory: (SettingsCategory) -> Unit,
    onOpenCharacterContinuity: () -> Unit,
    onOpenUserProfile: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenDataManagement: () -> Unit,
    onOpenAbout: () -> Unit
) {
    PageColumn {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = YumerinSpacing.PageVertical, bottom = YumerinSpacing.PageVertical),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(YumerinSpacing.Dense)
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Avatar(
                    text = settings.userName.take(1).ifBlank { "Y" },
                    uri = settings.userAvatarUri,
                    modifier = Modifier.size(88.dp),
                    contentDescription = "个人头像"
                )
                Box(
                    modifier = Modifier
                        .offset(x = 3.dp, y = 3.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onOpenUserProfile),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = YumerinEditIcon,
                        contentDescription = "编辑个人资料",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Text(
                settings.userName.ifBlank { "Yumerin" },
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                "Yumerin · 本地陪伴空间",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        MiuixSmallTitle(
            text = "偏好设置"
        )
        SettingsPreferenceCard {
            SettingsOverviewRow(
                "外观", "浅色/深色、自动切换、主题色与动效", YumerinPaletteIcon
            ) {
                onOpenCategory(SettingsCategory.APPEARANCE)
            }
            SettingsOverviewRow(
                "陪伴与智能体", "长期记忆和回复显示", YumerinCharacterIcon
            ) {
                onOpenCategory(SettingsCategory.COMPANION)
            }
            SettingsOverviewRow(
                "角色连续性", "查看和调整关系与情绪语境", YumerinCharacterIcon,
                onOpenCharacterContinuity
            )
            SettingsOverviewRow(
                "通知与后台", "主动消息、静默时段和计划", YumerinNotificationIcon
            ) {
                onOpenCategory(SettingsCategory.NOTIFICATIONS)
            }
            SettingsOverviewRow(
                "上下文", "长对话内容压缩", YumerinMemoryIcon
            ) {
                onOpenCategory(SettingsCategory.CONTEXT)
            }
            SettingsOverviewRow(
                "模型", "远端配置、全局默认与本地模型管理", YumerinModelIcon, onOpenModels
            )
            SettingsOverviewRow(
                "数据与存储", "导出、导入和本地数据管理", YumerinStorageIcon, onOpenDataManagement
            )
            SettingsOverviewRow(
                "关于 Yumerin", "版本信息、检查更新和开源组件", YumerinInfoIcon, onOpenAbout
            )
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
internal fun SettingsCategoryPage(
    category: SettingsCategory,
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onOpenSchedules: () -> Unit,
    onBack: () -> Unit
) {
    var quietStartText by remember(settings.quietStartMinutes) { mutableStateOf(settings.quietStartMinutes.toClockText()) }
    var quietEndText by remember(settings.quietEndMinutes) { mutableStateOf(settings.quietEndMinutes.toClockText()) }
    var showQuietTimePicker by remember { mutableStateOf(false) }
    var quietPickerStartHour by remember { mutableIntStateOf(settings.quietStartMinutes / 60) }
    var quietPickerStartMinute by remember { mutableIntStateOf(settings.quietStartMinutes % 60) }
    var quietPickerEndHour by remember { mutableIntStateOf(settings.quietEndMinutes / 60) }
    var quietPickerEndMinute by remember { mutableIntStateOf(settings.quietEndMinutes % 60) }
    var dailyLimitText by remember(settings.dailyProactiveLimit) { mutableStateOf(settings.dailyProactiveLimit.toString()) }
    var compressionPercent by remember(settings.contextCompressionThreshold) {
        mutableIntStateOf((settings.contextCompressionThreshold * 100).roundToInt())
    }

    MiuixScaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { _ ->
        PageColumn {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiuixBackButton(onClick = onBack)
                MiuixText(
                    category.title,
                    modifier = Modifier.padding(start = YumerinSpacing.Field),
                    style = YumerinTypography.SettingsTitle
                )
            }

            when (category) {
                SettingsCategory.APPEARANCE -> {
                    SettingsSection("外观") {
                        SettingsChoicePreference(
                            title = "主题模式",
                            options = ThemeMode.entries.map { SettingsChoiceOption(it.label()) },
                            selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
                            onOptionSelected = { index ->
                                onSettingsChanged(settings.copy(themeMode = ThemeMode.entries[index]))
                            }
                        )
                    }
                    SettingsSection("显示效果") {
                        SettingsChoicePreference(
                            title = "主题色",
                            options = ThemeColor.entries.map { SettingsChoiceOption(it.label(), it.swatch()) },
                            selectedIndex = ThemeColor.entries.indexOf(settings.themeColor),
                            onOptionSelected = { index ->
                                onSettingsChanged(settings.copy(themeColor = ThemeColor.entries[index]))
                            }
                        )
                        SettingsChoicePreference(
                            title = "组件材质",
                            options = ComponentMaterial.entries.map { SettingsChoiceOption(it.label()) },
                            selectedIndex = ComponentMaterial.entries.indexOf(settings.componentMaterial),
                            onOptionSelected = { index ->
                                onSettingsChanged(settings.copy(componentMaterial = ComponentMaterial.entries[index]))
                            }
                        )
                        SettingsSwitchRow(
                            title = "减少动效",
                            description = "降低页面切换和交互动画",
                            checked = settings.reducedMotionEnabled,
                            onCheckedChange = { onSettingsChanged(settings.copy(reducedMotionEnabled = it)) }
                        )
                    }
                }

                SettingsCategory.COMPANION -> SettingsSection("陪伴与回复") {
                    SettingsSwitchRow(
                        title = "自动维护长期记忆",
                        description = "按角色分别整理重要偏好、关系和事件",
                        checked = settings.autoMemoryEnabled,
                        onCheckedChange = { onSettingsChanged(settings.copy(autoMemoryEnabled = it)) }
                    )
                    SettingsChoicePreference(
                        title = "智能体步骤可见程度",
                        options = AgentVisibility.entries.map { SettingsChoiceOption(it.label()) },
                        selectedIndex = AgentVisibility.entries.indexOf(settings.agentVisibility),
                        onOptionSelected = { index ->
                            onSettingsChanged(settings.copy(agentVisibility = AgentVisibility.entries[index]))
                        }
                    )
                    SettingsSwitchRow(
                        title = "流式回复",
                        description = "边生成边显示角色回复",
                        checked = settings.streamingEnabled,
                        onCheckedChange = { onSettingsChanged(settings.copy(streamingEnabled = it)) }
                    )
                }

                SettingsCategory.NOTIFICATIONS -> SettingsSection("通知与后台") {
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
                    MiuixArrowPreference(
                        title = "时间范围",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = settings.quietTimeEnabled,
                        holdDownState = showQuietTimePicker,
                        endActions = {
                            MiuixText(
                                "$quietStartText – $quietEndText",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        },
                        onClick = {
                            val start = quietStartText.toClockMinutes() ?: settings.quietStartMinutes
                            val end = quietEndText.toClockMinutes() ?: settings.quietEndMinutes
                            quietPickerStartHour = start / 60
                            quietPickerStartMinute = start % 60
                            quietPickerEndHour = end / 60
                            quietPickerEndMinute = end % 60
                            showQuietTimePicker = true
                        }
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = YumerinSpacing.CardInset),
                        verticalArrangement = Arrangement.spacedBy(YumerinSpacing.Compact)
                    ) {
                        MiuixTextField(
                            dailyLimitText,
                            { value ->
                                dailyLimitText = value
                                value.toIntOrNull()
                                    ?.takeIf { it in 1..20 }
                                    ?.takeIf { it != settings.dailyProactiveLimit }
                                    ?.let { onSettingsChanged(settings.copy(dailyProactiveLimit = it)) }
                            },
                            Modifier.fillMaxWidth(),
                            label = "每日主动消息上限",
                            singleLine = true
                        )
                        MiuixText(
                            "范围 1–20；输入有效数值后立即生效",
                            modifier = Modifier.padding(start = YumerinSpacing.CardVertical, bottom = YumerinSpacing.PageVertical),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                    MiuixArrowPreference(
                        title = "管理主动消息计划",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenSchedules
                    )
                }

                SettingsCategory.CONTEXT -> SettingsSection("上下文") {
                    MiuixSliderPreference(
                        value = compressionPercent.toFloat(),
                        onValueChange = { compressionPercent = it.roundToInt().coerceIn(50, 95) },
                        modifier = Modifier.fillMaxWidth(),
                        title = "压缩阈值",
                        summary = "50–95%；数值越低，越早压缩上下文",
                        valueText = "$compressionPercent%",
                        valueRange = 50f..95f,
                        steps = 44,
                        onValueChangeFinished = {
                            val threshold = compressionPercent / 100f
                            if (threshold != settings.contextCompressionThreshold) {
                                onSettingsChanged(settings.copy(contextCompressionThreshold = threshold))
                            }
                        }
                    )
                }
            }
        }
        MiuixOverlayDialog(
            show = showQuietTimePicker,
            title = "设置静默时段",
            onDismissRequest = { showQuietTimePicker = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    MiuixNumberPicker(
                        value = quietPickerStartHour,
                        onValueChange = { quietPickerStartHour = it },
                        range = 0..23,
                        visibleItemCount = 3,
                        itemHeight = 60.dp,
                        label = { it.toString().padStart(2, '0') },
                        wrapAround = true,
                        textStyle = MiuixTheme.textStyles.title2,
                        modifier = Modifier.weight(1f)
                    )
                    MiuixText(":", style = MiuixTheme.textStyles.paragraph)
                    MiuixNumberPicker(
                        value = quietPickerStartMinute,
                        onValueChange = { quietPickerStartMinute = it },
                        range = 0..59,
                        visibleItemCount = 3,
                        itemHeight = 60.dp,
                        label = { it.toString().padStart(2, '0') },
                        wrapAround = true,
                        textStyle = MiuixTheme.textStyles.title2,
                        modifier = Modifier.weight(1f)
                    )
                    MiuixText("–", style = MiuixTheme.textStyles.title2)
                    MiuixNumberPicker(
                        value = quietPickerEndHour,
                        onValueChange = { quietPickerEndHour = it },
                        range = 0..23,
                        visibleItemCount = 3,
                        itemHeight = 60.dp,
                        label = { it.toString().padStart(2, '0') },
                        wrapAround = true,
                        textStyle = MiuixTheme.textStyles.title2,
                        modifier = Modifier.weight(1f)
                    )
                    MiuixText(":", style = MiuixTheme.textStyles.paragraph)
                    MiuixNumberPicker(
                        value = quietPickerEndMinute,
                        onValueChange = { quietPickerEndMinute = it },
                        range = 0..59,
                        visibleItemCount = 3,
                        itemHeight = 60.dp,
                        label = { it.toString().padStart(2, '0') },
                        wrapAround = true,
                        textStyle = MiuixTheme.textStyles.title2,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = YumerinSpacing.Field),
                    horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Field)
                ) {
                    MiuixTextButton(
                        text = "取消",
                        onClick = { showQuietTimePicker = false },
                        modifier = Modifier.weight(1f)
                    )
                    MiuixTextButton(
                        text = "确定",
                        onClick = {
                            val start = quietPickerStartHour * 60 + quietPickerStartMinute
                            val end = quietPickerEndHour * 60 + quietPickerEndMinute
                            quietStartText = start.toClockText()
                            quietEndText = end.toClockText()
                            onSettingsChanged(
                                settings.copy(
                                    quietStartMinutes = start,
                                    quietEndMinutes = end
                                )
                            )
                            showQuietTimePicker = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = MiuixButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    }
}

private data class SettingsChoiceOption(
    val label: String,
    val swatch: Color? = null
)

@Composable
private fun SettingsChoicePreference(
    title: String,
    options: List<SettingsChoiceOption>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit
) {
    val currentOnOptionSelected by rememberUpdatedState(onOptionSelected)
    val entry = remember(options, selectedIndex) {
        MiuixDropdownEntry(
            items = options.mapIndexed { index, option ->
                MiuixDropdownItem(
                    text = option.label,
                    selected = index == selectedIndex,
                    onClick = { currentOnOptionSelected(index) },
                    icon = option.swatch?.let { swatch ->
                        { modifier ->
                            Box(
                                modifier = modifier
                                    .size(YumerinSize.Icon)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .then(
                                        if (index == selectedIndex) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                            )
                        }
                    }
                )
            }
        )
    }

    OverlayDropdownMenu(
        entry = entry,
        title = title,
        modifier = Modifier.fillMaxWidth(),
        collapseOnSelection = true
    )
}

@Composable
private fun SettingsOverviewRow(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    MiuixArrowPreference(
        title = title,
        summary = description,
        modifier = Modifier.fillMaxWidth(),
        startAction = {
            Box(
                modifier = Modifier
                    .size(YumerinSize.PreferenceIconContainer)
                    .clip(RoundedCornerShape(YumerinShape.ControlRadius))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(YumerinSize.Icon)
                )
            }
        },
        onClick = onClick
    )
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    MiuixSmallTitle(text = title)
    SettingsPreferenceCard(content)
}

@Composable
private fun SettingsPreferenceCard(
    content: @Composable ColumnScope.() -> Unit
) {
    MiuixCardSurface(Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) = SwitchRow(title, checked, description, onCheckedChange)
