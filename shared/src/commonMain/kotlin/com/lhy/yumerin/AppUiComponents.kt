package com.lhy.yumerin

import androidx.compose.animation.*
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
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.preference.ArrowPreference as MiuixArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference as MiuixSwitchPreference

internal val LocalGlassButtonBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
internal fun PageColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = YumerinSpacing.PageHorizontal, vertical = YumerinSpacing.PageVertical),
        verticalArrangement = Arrangement.spacedBy(YumerinSpacing.Field),
        content = content
    )
}


@Composable
internal fun MiuixBackButton(
    onClick: () -> Unit
) {
    MiuixGlassIconButton(
        icon = YumerinBackArrow,
        contentDescription = "返回",
        onClick = onClick
    )
}

@Composable
internal fun MiuixGlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val backdrop = LocalGlassButtonBackdrop.current
    val isLightTheme = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val containerColor = YumerinGlassTokens.controlSurface(isLightTheme)
    val edgeLight = YumerinGlassTokens.edgeLight(isLightTheme)
    Box(
        modifier = Modifier
            .size(YumerinSize.ToolbarControl),
        contentAlignment = Alignment.Center
    ) {
        val glassModifier = Modifier
            .size(YumerinSize.TouchTarget)
            .shadow(
                elevation = YumerinGlassTokens.ShadowElevation,
                shape = CircleShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = YumerinGlassTokens.ShadowAlpha),
                spotColor = Color.Black.copy(alpha = YumerinGlassTokens.ShadowAlpha)
            )
            .then(
                if (backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            blur(YumerinGlassTokens.BlurRadius.toPx())
                            lens(YumerinGlassTokens.BlurRadius.toPx(), YumerinGlassTokens.LensRadius.toPx())
                        },
                        highlight = null,
                        shadow = null,
                        innerShadow = null,
                        onDrawSurface = {
                            drawRect(containerColor)
                        }
                    )
                } else {
                    Modifier
                        .clip(CircleShape)
                        .background(containerColor)
                }
            )
            .edgeLight(shape = CircleShape, edgeLight = edgeLight)

        MiuixIconButton(onClick = onClick, modifier = glassModifier, enabled = enabled) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(YumerinSize.Icon)
            )
        }
    }
}

@Composable
internal fun MiuixGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    backdrop: Backdrop? = LocalGlassButtonBackdrop.current,
    content: @Composable BoxScope.() -> Unit
) {
    val isLightTheme = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val containerColor = YumerinGlassTokens.controlSurface(isLightTheme)
    val edgeLight = YumerinGlassTokens.edgeLight(isLightTheme)

    Box(
        modifier = modifier
            .shadow(
                elevation = YumerinGlassTokens.ShadowElevation,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = YumerinGlassTokens.ShadowAlpha),
                spotColor = Color.Black.copy(alpha = YumerinGlassTokens.ShadowAlpha)
            )
            .then(
                if (backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur(YumerinGlassTokens.BlurRadius.toPx())
                            lens(YumerinGlassTokens.BlurRadius.toPx(), YumerinGlassTokens.LensRadius.toPx())
                        },
                        highlight = null,
                        shadow = null,
                        innerShadow = null,
                        onDrawSurface = { drawRect(containerColor) }
                    )
                } else {
                    Modifier.clip(shape).background(containerColor)
                }
            )
            .edgeLight(shape = shape, edgeLight = edgeLight),
        content = content
    )
}

@Composable
internal fun MiuixMoreButton(
    onClick: () -> Unit
) {
    MiuixGlassIconButton(
        icon = YumerinMoreIcon,
        contentDescription = "更多设置",
        onClick = onClick
    )
}

@Composable
internal fun MiuixActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MiuixIconButton(onClick = onClick, modifier = modifier.size(YumerinSize.TouchTarget)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(YumerinSize.Icon)
        )
    }
}

@Composable
internal fun MiuixSendButton(
    sending: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    MiuixIconButton(
        onClick = onClick,
        modifier = Modifier.size(YumerinSize.ToolbarControl),
        enabled = enabled
    ) {
        Icon(
            imageVector = if (sending) YumerinStopIcon else YumerinSendIcon,
            contentDescription = if (sending) "停止生成" else "发送",
            tint = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            modifier = Modifier.size(YumerinSize.SendIcon)
        )
    }
}

private val YumerinBackArrow: ImageVector = ImageVector.Builder(
    name = "YumerinBackArrow",
    defaultWidth = YumerinSize.IconViewport,
    defaultHeight = YumerinSize.IconViewport,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(20f, 11f)
        lineTo(7.83f, 11f)
        lineTo(13.42f, 5.41f)
        lineTo(12f, 4f)
        lineTo(4f, 12f)
        lineTo(12f, 20f)
        lineTo(13.41f, 18.59f)
        lineTo(7.83f, 13f)
        lineTo(20f, 13f)
        close()
    }
}.build()

internal val YumerinMoreIcon: ImageVector = ImageVector.Builder(
    name = "YumerinMoreIcon",
    defaultWidth = YumerinSize.IconViewport,
    defaultHeight = YumerinSize.IconViewport,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(7f, 12f)
        curveTo(7f, 13.105f, 6.105f, 14f, 5f, 14f)
        curveTo(3.895f, 14f, 3f, 13.105f, 3f, 12f)
        curveTo(3f, 10.895f, 3.895f, 10f, 5f, 10f)
        curveTo(6.105f, 10f, 7f, 10.895f, 7f, 12f)
        close()
        moveTo(14f, 12f)
        curveTo(14f, 13.105f, 13.105f, 14f, 12f, 14f)
        curveTo(10.895f, 14f, 10f, 13.105f, 10f, 12f)
        curveTo(10f, 10.895f, 10.895f, 10f, 12f, 10f)
        curveTo(13.105f, 10f, 14f, 10.895f, 14f, 12f)
        close()
        moveTo(21f, 12f)
        curveTo(21f, 13.105f, 20.105f, 14f, 19f, 14f)
        curveTo(17.895f, 14f, 17f, 13.105f, 17f, 12f)
        curveTo(17f, 10.895f, 17.895f, 10f, 19f, 10f)
        curveTo(20.105f, 10f, 21f, 10.895f, 21f, 12f)
        close()
    }
}.build()

internal val YumerinChevronRight: ImageVector = ImageVector.Builder(
    name = "YumerinChevronRight",
    defaultWidth = YumerinSize.IconViewport,
    defaultHeight = YumerinSize.IconViewport,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(8.5f, 5.4f)
        lineTo(15.1f, 12f)
        lineTo(8.5f, 18.6f)
        lineTo(7.1f, 17.2f)
        lineTo(12.3f, 12f)
        lineTo(7.1f, 6.8f)
        close()
    }
}.build()

internal val YumerinPinIcon: ImageVector = ImageVector.Builder(
    name = "YumerinPinIcon",
    defaultWidth = YumerinSize.IconViewport,
    defaultHeight = YumerinSize.IconViewport,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 2f)
        curveTo(8.134f, 2f, 5f, 5.134f, 5f, 9f)
        curveTo(5f, 14.25f, 12f, 22f, 12f, 22f)
        curveTo(12f, 22f, 19f, 14.25f, 19f, 9f)
        curveTo(19f, 5.134f, 15.866f, 2f, 12f, 2f)
        close()
    }
}.build()

internal val YumerinMutedIcon: ImageVector = ImageVector.Builder(
    name = "YumerinMutedIcon",
    defaultWidth = YumerinSize.IconViewport,
    defaultHeight = YumerinSize.IconViewport,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(3f, 9f)
        lineTo(3f, 15f)
        lineTo(7f, 15f)
        lineTo(12f, 19f)
        lineTo(12f, 5f)
        lineTo(7f, 9f)
        close()
        moveTo(16f, 9f)
        lineTo(14.6f, 10.4f)
        lineTo(16.2f, 12f)
        lineTo(14.6f, 13.6f)
        lineTo(16f, 15f)
        lineTo(17.6f, 13.4f)
        lineTo(19.2f, 15f)
        lineTo(20.6f, 13.6f)
        lineTo(19f, 12f)
        lineTo(20.6f, 10.4f)
        lineTo(19.2f, 9f)
        lineTo(17.6f, 10.6f)
        close()
    }
}.build()

internal val YumerinAddIcon: ImageVector = ImageVector.Builder(
    name = "YumerinAddIcon",
    defaultWidth = YumerinSize.IconViewport,
    defaultHeight = YumerinSize.IconViewport,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(19f, 13f)
        lineTo(13f, 13f)
        lineTo(13f, 19f)
        lineTo(11f, 19f)
        lineTo(11f, 13f)
        lineTo(5f, 13f)
        lineTo(5f, 11f)
        lineTo(11f, 11f)
        lineTo(11f, 5f)
        lineTo(13f, 5f)
        lineTo(13f, 11f)
        lineTo(19f, 11f)
        close()
    }
}.build()

internal val YumerinEditIcon: ImageVector = ImageVector.Builder(
    name = "YumerinEditIcon",
    defaultWidth = YumerinSize.IconViewport,
    defaultHeight = YumerinSize.IconViewport,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(3f, 17.25f)
        lineTo(3f, 21f)
        lineTo(6.75f, 21f)
        lineTo(17.81f, 9.94f)
        lineTo(14.06f, 6.19f)
        close()
        moveTo(20.71f, 7.04f)
        curveTo(21.1f, 6.65f, 21.1f, 6.02f, 20.71f, 5.63f)
        lineTo(18.37f, 3.29f)
        curveTo(17.98f, 2.9f, 17.35f, 2.9f, 16.96f, 3.29f)
        lineTo(15.13f, 5.12f)
        lineTo(18.88f, 8.87f)
        close()
    }
}.build()

internal val YumerinMemoryIcon: ImageVector = ImageVector.Builder(
    name = "YumerinMemoryIcon",
    defaultWidth = YumerinSize.IconViewport,
    defaultHeight = YumerinSize.IconViewport,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
        moveTo(6f, 3f)
        lineTo(18f, 3f)
        curveTo(19.1f, 3f, 20f, 3.9f, 20f, 5f)
        lineTo(20f, 21f)
        lineTo(12f, 17f)
        lineTo(4f, 21f)
        lineTo(4f, 5f)
        curveTo(4f, 3.9f, 4.9f, 3f, 6f, 3f)
        close()
        moveTo(8f, 7f)
        lineTo(16f, 7f)
        lineTo(16f, 9f)
        lineTo(8f, 9f)
        close()
        moveTo(8f, 11f)
        lineTo(16f, 11f)
        lineTo(16f, 13f)
        lineTo(8f, 13f)
        close()
    }
}.build()

private val YumerinSendIcon: ImageVector = ImageVector.Builder(
    name = "YumerinSendIcon",
    defaultWidth = YumerinSize.IconViewport,
    defaultHeight = YumerinSize.IconViewport,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(3f, 4f)
        lineTo(21f, 12f)
        lineTo(3f, 20f)
        lineTo(3f, 14f)
        lineTo(15f, 12f)
        lineTo(3f, 10f)
        close()
    }
}.build()

private val YumerinStopIcon: ImageVector = ImageVector.Builder(
    name = "YumerinStopIcon",
    defaultWidth = YumerinSize.IconViewport,
    defaultHeight = YumerinSize.IconViewport,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(7f, 7f)
        lineTo(17f, 7f)
        lineTo(17f, 17f)
        lineTo(7f, 17f)
        close()
    }
}.build()


@Composable
internal fun Avatar(
    text: String,
    uri: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    size: androidx.compose.ui.unit.Dp = 52.dp
) {
    Box(
        modifier = Modifier
            .then(modifier)
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        PlatformAvatar(uri = uri, fallback = text, modifier = Modifier.fillMaxSize())
    }
}

@Composable
internal fun CompositeAvatar(members: List<CharacterProfile>, fallback: String) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val visible = members.take(4)
        if (visible.isEmpty()) {
            Text(fallback.take(1), style = MaterialTheme.typography.titleLarge)
        } else {
            visible.forEachIndexed { index, member ->
                val x = when (index % 2) { 0 -> -YumerinSpacing.Item; else -> YumerinSpacing.Item }
                val y = when (index / 2) { 0 -> -YumerinSpacing.Item; else -> YumerinSpacing.Item }
                Box(
                    modifier = Modifier
                        .offset(x, y)
                        .size(26.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    PlatformAvatar(member.avatarUri, member.name, Modifier.fillMaxSize())
                }
            }
        }
    }
}


@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    description: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    MiuixSwitchPreference(
        title = title,
        summary = description,
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun SettingRow(
    title: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    description: String? = null,
    showArrow: Boolean = true
) {
    if (onClick != null && showArrow) {
        MiuixArrowPreference(
            title = title,
            summary = description,
            modifier = Modifier.fillMaxWidth(),
            endActions = {
                value?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 132.dp)
                    )
                }
            },
            onClick = onClick
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .heightIn(min = YumerinSize.PreferenceRow)
            .padding(horizontal = YumerinSpacing.CardInset, vertical = YumerinSpacing.Field),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Field)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(YumerinSpacing.Tight)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Compact)) {
            value?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 132.dp)
                )
            }
        }
    }
}

internal fun ThemeMode.label(): String = when (this) {
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
    ThemeMode.SYSTEM -> "跟随系统"
}

internal fun AgentVisibility.label(): String = when (this) {
    AgentVisibility.FINAL_ONLY -> "只显示回复"
    AgentVisibility.CONCISE_STATUS -> "简要状态"
    AgentVisibility.DETAILED_STEPS -> "详细步骤"
}

internal fun MemoryCategory.label(): String = when (this) {
    MemoryCategory.LONG_TERM -> "长期资料"
    MemoryCategory.USER_PREFERENCE -> "用户偏好"
    MemoryCategory.RELATIONSHIP -> "角色关系"
    MemoryCategory.IMPORTANT_EVENT -> "重要事件"
}

internal fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

internal fun Int.pad2(): String = toString().padStart(2, '0')

internal fun Int.toClockText(): String = (this.coerceIn(0, 23 * 60 + 59) / 60).pad2() + ":" + (this % 60).pad2()

internal fun String.toClockMinutes(): Int? {
    val parts = trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
}

internal fun summarizeMessages(messages: List<ChatMessage>): String = messages
    .filter { it.text.isNotBlank() }
    .takeLast(24)
    .joinToString("\n") { "${it.senderName}: ${it.text}" }
    .takeLast(4_000)
