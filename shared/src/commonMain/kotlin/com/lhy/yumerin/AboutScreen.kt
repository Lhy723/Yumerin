package com.lhy.yumerin

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import com.lhy.yumerin.core.model.AppReleaseMetadata
import com.lhy.yumerin.designsystem.YumerinButton
import com.lhy.yumerin.designsystem.YumerinSize
import com.lhy.yumerin.designsystem.YumerinShape
import com.lhy.yumerin.designsystem.YumerinSpacing
import com.lhy.yumerin.designsystem.YumerinTextButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

private sealed interface UpdateCheckStatus {
    data object NotChecked : UpdateCheckStatus
    data object Checking : UpdateCheckStatus
    data class UpToDate(val version: String) : UpdateCheckStatus
    data class UpdateAvailable(val release: AppReleaseMetadata) : UpdateCheckStatus
    data class CurrentBuildIsNewer(val version: String) : UpdateCheckStatus
    data class UnrecognizedVersion(val release: AppReleaseMetadata) : UpdateCheckStatus
    data object Failed : UpdateCheckStatus
    data object Unsupported : UpdateCheckStatus
}

@Composable
internal fun AboutPage(
    appIcon: ImageBitmap?,
    currentVersionName: String,
    onCheckForUpdates: (suspend () -> AppReleaseMetadata)?,
    onOpenExternalUrl: ((String) -> Unit)?,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var updateStatus by remember(currentVersionName) {
        mutableStateOf<UpdateCheckStatus>(
            if (onCheckForUpdates == null) UpdateCheckStatus.Unsupported else UpdateCheckStatus.NotChecked
        )
    }

    PageColumn {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Field)
        ) {
            MiuixBackButton(onClick = onBack)
            MiuixText("关于", style = MiuixTheme.textStyles.title2)
        }
        MiuixCardSurface(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(YumerinSpacing.LargeInset),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(YumerinSpacing.Item)
            ) {
                appIcon?.let {
                    Image(
                        bitmap = it,
                        contentDescription = "Yumerin 应用图标",
                        modifier = Modifier
                            .size(YumerinSize.LargeAvatar)
                            .clip(RoundedCornerShape(YumerinShape.HeroRadius)),
                        contentScale = ContentScale.Crop
                    )
                }
                MiuixText("Yumerin", style = MiuixTheme.textStyles.title2)
                MiuixText(
                    "AI 情感陪伴",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            }
        }
        MiuixCardSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                SettingRow("应用", "Yumerin")
                SettingRow("版本", currentVersionName)
                SettingRow("组件库", "Miuix")
                SettingRow("开发框架", "Kotlin Multiplatform + Compose")
                SettingRow("许可证总览", "Apache License 2.0")
            }
        }
        UpdateCheckCard(
            currentVersionName = currentVersionName,
            status = updateStatus,
            onCheck = onCheckForUpdates?.let { check ->
                {
                    scope.launch {
                        updateStatus = UpdateCheckStatus.Checking
                        updateStatus = try {
                            val release = check()
                            when (compareVersionTags(release.versionTag, currentVersionName)) {
                                1 -> UpdateCheckStatus.UpdateAvailable(release)
                                0 -> UpdateCheckStatus.UpToDate(release.versionTag.withoutPrefix())
                                -1 -> UpdateCheckStatus.CurrentBuildIsNewer(release.versionTag.withoutPrefix())
                                else -> UpdateCheckStatus.UnrecognizedVersion(release)
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            UpdateCheckStatus.Failed
                        }
                    }
                }
            },
            onOpenExternalUrl = onOpenExternalUrl
        )
        MiuixCardSurface(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(YumerinSpacing.CardInset),
                verticalArrangement = Arrangement.spacedBy(YumerinSpacing.Item)
            ) {
                MiuixText("开源许可证", style = MiuixTheme.textStyles.title3)
                MiuixText("本应用不把用户 API Key 或本地模型文件放进导出包；以下是运行时主要开源组件。")
                listOf(
                    "Miuix" to "Apache License 2.0",
                    "Kotlin / Compose Multiplatform" to "Apache License 2.0",
                    "LiteRT-LM" to "Apache License 2.0",
                    "Room / WorkManager" to "Apache License 2.0",
                    "Koin" to "Apache License 2.0"
                ).forEach { (name, license) -> SettingRow(name, license) }
            }
        }
    }
}

@Composable
private fun UpdateCheckCard(
    currentVersionName: String,
    status: UpdateCheckStatus,
    onCheck: (() -> Unit)?,
    onOpenExternalUrl: ((String) -> Unit)?
) {
    val statusText = when (val current = status) {
        UpdateCheckStatus.NotChecked -> "检查 GitHub 上的最新正式版。此操作只读取版本信息，不会自动下载或安装。"
        UpdateCheckStatus.Checking -> "正在检查最新正式版…"
        is UpdateCheckStatus.UpToDate -> "当前已是最新版本（${current.version}）。"
        is UpdateCheckStatus.UpdateAvailable -> "发现新版本 ${current.release.versionTag.withoutPrefix()}。"
        is UpdateCheckStatus.CurrentBuildIsNewer -> "当前版本（$currentVersionName）高于最新公开版（${current.version}）。"
        is UpdateCheckStatus.UnrecognizedVersion -> "最新公开版为 ${current.release.versionTag}，可以查看发布说明。"
        UpdateCheckStatus.Failed -> "暂时无法检查更新，请确认网络后重试。"
        UpdateCheckStatus.Unsupported -> "此构建暂不支持检查更新。"
    }
    val releaseToOpen = when (status) {
        is UpdateCheckStatus.UpdateAvailable -> status.release
        is UpdateCheckStatus.UnrecognizedVersion -> status.release
        else -> null
    }
    val canOpenLink = onOpenExternalUrl != null

    MiuixCardSurface(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(YumerinSpacing.CardInset),
            verticalArrangement = Arrangement.spacedBy(YumerinSpacing.Item)
        ) {
            MiuixText("软件更新", style = MiuixTheme.textStyles.title3)
            MiuixText(
                statusText,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions
            )
            YumerinButton(
                onClick = onCheck ?: {},
                modifier = Modifier.fillMaxWidth(),
                enabled = onCheck != null && status != UpdateCheckStatus.Checking
            ) {
                MiuixText(
                    if (status == UpdateCheckStatus.Checking) "正在检查…" else "检查更新",
                    style = MiuixTheme.textStyles.button
                )
            }
            releaseToOpen?.let { release ->
                YumerinButton(
                    onClick = {
                        val url = release.apkDownloadUrl ?: release.releasePageUrl
                        onOpenExternalUrl?.invoke(url)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canOpenLink
                ) {
                    MiuixText(
                        if (release.apkDownloadUrl != null) "下载更新" else "查看更新",
                        style = MiuixTheme.textStyles.button
                    )
                }
                if (release.apkDownloadUrl != null) {
                    YumerinTextButton(
                        onClick = { onOpenExternalUrl?.invoke(release.releasePageUrl) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canOpenLink
                    ) {
                        MiuixText("查看更新说明", style = MiuixTheme.textStyles.button)
                    }
                }
            }
        }
    }
}

private data class ParsedVersion(
    val components: List<Long>,
    val prerelease: List<String>?
)

private fun compareVersionTags(candidate: String, current: String): Int? {
    val candidateVersion = candidate.parseVersion() ?: return null
    val currentVersion = current.parseVersion() ?: return null
    val componentCount = maxOf(candidateVersion.components.size, currentVersion.components.size)
    for (index in 0 until componentCount) {
        val candidatePart = candidateVersion.components.getOrElse(index) { 0L }
        val currentPart = currentVersion.components.getOrElse(index) { 0L }
        if (candidatePart != currentPart) return candidatePart.compareTo(currentPart)
    }

    val candidatePrerelease = candidateVersion.prerelease
    val currentPrerelease = currentVersion.prerelease
    if (candidatePrerelease == null && currentPrerelease == null) return 0
    if (candidatePrerelease == null) return 1
    if (currentPrerelease == null) return -1

    val identifierCount = maxOf(candidatePrerelease.size, currentPrerelease.size)
    for (index in 0 until identifierCount) {
        val candidateIdentifier = candidatePrerelease.getOrNull(index) ?: return -1
        val currentIdentifier = currentPrerelease.getOrNull(index) ?: return 1
        val candidateNumber = candidateIdentifier.toLongOrNull()
        val currentNumber = currentIdentifier.toLongOrNull()
        val comparison = when {
            candidateNumber != null && currentNumber != null -> candidateNumber.compareTo(currentNumber)
            candidateNumber != null -> -1
            currentNumber != null -> 1
            else -> candidateIdentifier.compareTo(currentIdentifier)
        }
        if (comparison != 0) return comparison
    }
    return 0
}

private fun String.parseVersion(): ParsedVersion? {
    val withoutPrefix = withoutPrefix()
    val buildAndPrerelease = withoutPrefix.substringBefore('+')
    val prereleaseSeparator = buildAndPrerelease.indexOf('-')
    val core = if (prereleaseSeparator < 0) buildAndPrerelease else buildAndPrerelease.substring(0, prereleaseSeparator)
    if (!core.matches(Regex("\\d+(\\.\\d+)*"))) return null
    val components = core.split('.').map { it.toLongOrNull() ?: return null }
    val prerelease = if (prereleaseSeparator < 0) {
        null
    } else {
        buildAndPrerelease.substring(prereleaseSeparator + 1)
            .split('.')
            .takeIf { parts -> parts.all(String::isNotBlank) }
            ?: return null
    }
    return ParsedVersion(components, prerelease)
}

private fun String.withoutPrefix(): String = trim().removePrefix("v").removePrefix("V")
