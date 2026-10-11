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
internal fun ProfilePage(
    backdrop: Backdrop,
    settings: AppSettings,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenUserProfile: () -> Unit
) {
    PageColumn {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("我的", style = MaterialTheme.typography.headlineLarge)
            Text(
                "个人空间与应用偏好",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LiquidGlassSurface(
            backdrop = backdrop,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onOpenUserProfile),
            shape = RoundedCornerShape(24.dp),
            surfaceAlpha = 0.46f
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Avatar(settings.userName.take(1).ifBlank { "Y" }, settings.userAvatarUri)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(settings.userName.ifBlank { "Yumerin" }, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "个人资料",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = YumerinChevronRight,
                    contentDescription = "编辑个人资料",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text("偏好与应用", style = MaterialTheme.typography.titleMedium)
        GlassSurface(backdrop, Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                SettingRow(
                    title = "设置",
                    value = settings.themeMode.label(),
                    onClick = onOpenSettings,
                    description = "主题、陪伴方式与通知"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                SettingRow(
                    title = "关于",
                    value = "版本与组件",
                    onClick = onOpenAbout,
                    description = "版本信息和开源许可证"
                )
            }
        }
    }
}

@Composable
internal fun UserProfilePage(
    backdrop: Backdrop,
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
            GlassBackButton(backdrop = backdrop, onClick = onBack)
            Text("个人资料", style = MaterialTheme.typography.headlineLarge)
        }
        GlassSurface(backdrop, Modifier.fillMaxWidth()) {
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
