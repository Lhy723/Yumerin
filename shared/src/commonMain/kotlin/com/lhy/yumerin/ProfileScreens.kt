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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.dp
import com.lhy.yumerin.core.model.*
import com.lhy.yumerin.data.memory.*
import com.lhy.yumerin.designsystem.*
import com.lhy.yumerin.designsystem.YumerinButton as Button
import com.lhy.yumerin.designsystem.YumerinOutlinedTextField as OutlinedTextField
import com.lhy.yumerin.designsystem.YumerinTextButton as TextButton
import com.lhy.yumerin.domain.*
import com.lhy.yumerin.domain.memory.*
import com.lhy.yumerin.domain.repository.*
import com.lhy.yumerin.domain.usecase.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@Composable
internal fun ProfilePage(
    settings: AppSettings,
    onPickAvatar: ((onSelected: (String?) -> Unit) -> Unit),
    onSettingsChanged: (AppSettings) -> Unit,
    onOpenUserProfile: () -> Unit
) {
    val displayName = settings.userName.ifBlank { "Yumerin" }
    PageColumn {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = YumerinSpacing.Field, bottom = YumerinSpacing.PageVertical),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(YumerinSpacing.FormField)
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
            horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Item)
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
                modifier = Modifier.size(YumerinSize.SmallIcon)
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
            horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Field)
        ) {
            MiuixBackButton(onClick = onBack)
            Text("个人资料", style = MaterialTheme.typography.headlineLarge)
        }
        MiuixCardSurface(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(YumerinSpacing.CardInset),
                verticalArrangement = Arrangement.spacedBy(YumerinSpacing.CardVertical)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Row)
                ) {
                    Avatar(
                        normalizedName.take(1).ifBlank { "Y" },
                        avatarUri,
                        contentDescription = "当前个人头像"
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(YumerinSpacing.Compact)
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
                    label = "显示名称",
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
