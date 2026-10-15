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
import top.yukonga.miuix.kmp.basic.SmallTitle as MiuixSmallTitle

@Composable
internal fun ProfilePage(
    settings: AppSettings,
    onPickAvatar: ((onSelected: (String?) -> Unit) -> Unit),
    onSettingsChanged: (AppSettings) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenUserProfile: () -> Unit
) {
    val displayName = settings.userName.ifBlank { "Yumerin" }
    PageColumn {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Avatar(
                text = displayName.take(1),
                uri = settings.userAvatarUri,
                size = 104.dp,
                contentDescription = "个人头像"
            )
            Text(displayName, style = MaterialTheme.typography.headlineSmall)
            Text(
                "个人资料 · 仅用于本机界面显示",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProfileActionTile(
                                icon = YumerinAddIcon,
                title = "设置照片",
                modifier = Modifier.weight(1f),
                onClick = {
                    onPickAvatar { selected ->
                        if (selected != null) {
                            onSettingsChanged(settings.copy(userAvatarUri = selected))
                        }
                    }
                }
            )
            ProfileActionTile(
                                icon = YumerinEditIcon,
                title = "编辑资料",
                modifier = Modifier.weight(1f),
                onClick = onOpenUserProfile
            )
            ProfileActionTile(
                                icon = YumerinSettingsIcon,
                title = "设置",
                modifier = Modifier.weight(1f),
                onClick = onOpenSettings
            )
        }

        MiuixSmallTitle(text = "个人信息")
        MiuixCardSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                SettingRow(
                    title = "账号",
                    value = "头像和显示名称",
                    onClick = onOpenUserProfile,
                    description = "管理个人头像和显示名称"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
                SettingRow(
                    title = "头像",
                    value = if (settings.userAvatarUri == null) "默认" else "已设置",
                    description = "头像只用于 Yumerin 内的个人资料和消息显示"
                )
            }
        }

        MiuixSmallTitle(text = "应用信息")
        MiuixCardSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                SettingRow(
                    title = "设置",
                    value = "${settings.themeMode.label()} · ${settings.themeColor.label()}",
                    onClick = onOpenSettings,
                    description = "主题和页面动效在设置的“外观”中调整"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
                SettingRow(
                    title = "关于 Yumerin",
                    value = "版本与组件",
                    onClick = onOpenAbout,
                    description = "版本信息和开源许可证"
                )
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
internal fun ProfileActionTile(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    MiuixCardSurface(
        modifier = modifier
            .height(64.dp)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun UserProfilePage(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onPickAvatar: ((onSelected: (String?) -> Unit) -> Unit),
    onBack: () -> Unit
) {
    var name by remember(settings.userName) { mutableStateOf(settings.userName) }
    var avatarUri by remember(settings.userAvatarUri) { mutableStateOf(settings.userAvatarUri) }
    val normalizedName = name.trim()

    PageColumn {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MiuixBackButton(onClick = onBack)
            Text("个人资料", style = MaterialTheme.typography.headlineLarge)
        }
        MiuixCardSurface(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Avatar(
                        normalizedName.take(1).ifBlank { "Y" },
                        avatarUri,
                        contentDescription = "当前个人头像"
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("个人头像", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "头像仅用于本机界面显示。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = {
                        onPickAvatar { selected ->
                            if (selected != null) avatarUri = selected
                        }
                    }) {
                        Text("更换")
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("显示名称") },
                    supportingText = { Text("这个名称会显示在个人资料中。") },
                    singleLine = true
                )
                Button(
                    onClick = {
                        onSettingsChanged(
                            settings.copy(
                                userName = normalizedName,
                                userAvatarUri = avatarUri
                            )
                        )
                        onBack()
                    },
                    enabled = normalizedName.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存资料")
                }
            }
        }
    }
}
