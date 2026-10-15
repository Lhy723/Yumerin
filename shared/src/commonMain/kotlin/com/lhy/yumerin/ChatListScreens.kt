package com.lhy.yumerin

import androidx.compose.animation.*
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
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
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Checkbox as MiuixCheckbox
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.overlay.OverlayDialog as MiuixOverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference as MiuixSwitchPreference
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton as MiuixFloatingActionButton

private enum class ChatListFilter(val label: String) {
    ALL("全部"),
    DIRECT("私聊"),
    GROUP("群聊")
}

@Composable
internal fun ChatListPage(
    chats: List<ChatThread>,
    characters: List<CharacterProfile>,
    groups: List<GroupProfile>,
    reducedMotion: Boolean,
    onOpenChat: (EntityId) -> Unit,
    onTogglePinned: (ChatThread) -> Unit,
    onToggleMuted: (ChatThread) -> Unit,
    onMarkUnread: (ChatThread) -> Unit,
    onDeleteChat: (ChatThread) -> Unit,
    onCreateGroup: () -> Unit,
    onEditGroup: (EntityId) -> Unit
) {
    val orderedChats = remember(chats) {
        chats.sortedWith(compareByDescending<ChatThread> { it.pinned }.thenByDescending { it.lastActivityAt })
    }
    var selectedFilter by remember { mutableStateOf(ChatListFilter.ALL) }
    val directCount = orderedChats.count { it.kind == ChatKind.DIRECT }
    val groupCount = orderedChats.count { it.kind == ChatKind.GROUP }
    val filteredChats = remember(orderedChats, selectedFilter) {
        when (selectedFilter) {
            ChatListFilter.ALL -> orderedChats
            ChatListFilter.DIRECT -> orderedChats.filter { it.kind == ChatKind.DIRECT }
            ChatListFilter.GROUP -> orderedChats.filter { it.kind == ChatKind.GROUP }
        }
    }

    Box(Modifier.fillMaxSize()) {
        PageColumn {
            Column(
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "聊天",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "和你的角色继续对话",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ChatListFilterBar(
                                selectedFilter = selectedFilter,
                totalCount = orderedChats.size,
                directCount = directCount,
                groupCount = groupCount,
                reducedMotion = reducedMotion,
                onSelected = { selectedFilter = it }
            )
            if (filteredChats.isEmpty()) {
                MiuixCardSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (orderedChats.isEmpty()) "还没有聊天" else "这里还没有${selectedFilter.label}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (orderedChats.isEmpty()) "创建角色后，就可以在这里开始对话。" else "切换分类，或创建一个新的群聊。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                MiuixCardSurface(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        filteredChats.forEachIndexed { index, chat ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 80.dp, end = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                                )
                            }
                            val character = characters.firstOrNull { it.id == chat.characterId }
                            ChatListRow(
                                chat = chat,
                                avatarText = character?.name?.take(1) ?: "群",
                                avatarUri = character?.avatarUri ?: chat.avatarUri,
                                avatarMembers = chat.groupId?.let { groupId ->
                                    groups.firstOrNull { it.id == groupId }?.memberCharacterIds
                                        ?.mapNotNull { id -> characters.firstOrNull { it.id == id } }
                                }.orEmpty(),
                                onClick = { onOpenChat(chat.id) },
                                onTogglePinned = { onTogglePinned(chat) },
                                onToggleMuted = { onToggleMuted(chat) },
                                onMarkUnread = { onMarkUnread(chat) },
                                onDelete = { onDeleteChat(chat) },
                                onEditGroup = chat.groupId?.let { groupId -> { onEditGroup(groupId) } }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }

        MiuixFloatingActionButton(
            onClick = onCreateGroup,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 106.dp)
        ) {
            Icon(
                YumerinAddIcon,
                contentDescription = "创建群聊",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun ChatListFilterBar(
    selectedFilter: ChatListFilter,
    totalCount: Int,
    directCount: Int,
    groupCount: Int,
    reducedMotion: Boolean,
    onSelected: (ChatListFilter) -> Unit
) {
    val filters = ChatListFilter.entries
    val counts = mapOf(
        ChatListFilter.ALL to totalCount,
        ChatListFilter.DIRECT to directCount,
        ChatListFilter.GROUP to groupCount
    )
    val selectedIndex = filters.indexOf(selectedFilter).coerceAtLeast(0)
    val animationScope = rememberCoroutineScope()
    val selectorAnimation = remember(animationScope, filters.size, reducedMotion) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..filters.lastIndex.toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.035f,
            reducedMotion = reducedMotion,
            onDragStarted = {},
            onDragStopped = {},
            onDrag = { _, _ -> }
        )
    }
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current

    LaunchedEffect(selectorAnimation, selectedIndex) {
        if (selectorAnimation.targetValue != selectedIndex.toFloat()) {
            selectorAnimation.animateToValue(selectedIndex.toFloat())
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer {
                val scale = 1f + 8.dp.toPx() / size.width * selectorAnimation.pressProgress
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 0.8.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                shape = CircleShape
            )
    ) {
        val horizontalInsetPx = with(density) { 4.dp.toPx() }
        val tabWidthPx = ((constraints.maxWidth - horizontalInsetPx * 2f) / filters.size)
            .coerceAtLeast(1f)
        val tabWidth = with(density) { tabWidthPx.toDp() }

        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX = selectorAnimation.value * tabWidthPx
                    scaleX = selectorAnimation.scaleX
                    scaleY = selectorAnimation.scaleY
                }
                .width(tabWidth)
                .fillMaxHeight()
                .padding(vertical = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        )

        Row(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            filters.forEach { filter ->
                val selected = selectedFilter == filter
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable { onSelected(filter) }
                        .semantics {
                            role = Role.Tab
                            this.selected = selected
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            filter.label,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1
                        )
                        Text(
                            counts.getValue(filter).toString(),
                            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(filters.size, tabWidthPx, selectedIndex) {
                    val touchSlop = viewConfiguration.touchSlop
                    val maxIndex = filters.lastIndex.toFloat()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downX = down.position.x
                        val tabIndex = floor((downX - horizontalInsetPx) / tabWidthPx)
                            .toInt()
                            .coerceIn(0, filters.lastIndex)
                        val currentThumbCenter = horizontalInsetPx +
                            (selectorAnimation.value + 0.5f) * tabWidthPx
                        var dragging = abs(downX - currentThumbCenter) <= tabWidthPx * 0.55f
                        selectorAnimation.press()
                        if (!dragging) selectorAnimation.animateToValueKeepingPress(tabIndex.toFloat())
                        var lastX = downX

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (dragging) {
                                    val target = selectorAnimation.targetValue
                                        .roundToInt()
                                        .coerceIn(0, filters.lastIndex)
                                    if (target != selectedIndex) onSelected(filters[target])
                                    selectorAnimation.animateToValue(target.toFloat())
                                } else if (
                                    change.position.x < 0f ||
                                    change.position.x > size.width ||
                                    change.position.y < 0f ||
                                    change.position.y > size.height
                                ) {
                                    selectorAnimation.animateToValue(selectedIndex.toFloat())
                                } else {
                                    onSelected(filters[tabIndex])
                                    selectorAnimation.release()
                                }
                                break
                            }

                            val dx = change.position.x - lastX
                            val totalDx = change.position.x - downX
                            if (!dragging && abs(totalDx) > touchSlop) dragging = true
                            if (dragging && abs(dx) > 0.01f) {
                                lastX = change.position.x
                                selectorAnimation.updateValue(
                                    (selectorAnimation.targetValue + dx / tabWidthPx)
                                        .coerceIn(0f, maxIndex)
                                )
                                change.consume()
                            } else {
                                lastX = change.position.x
                            }
                        }
                    }
                }
        )
    }
}

private fun formatChatActivityTime(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return ""
    val elapsedMillis = (Clock.System.now().toEpochMilliseconds() - timestampMillis).coerceAtLeast(0L)
    return when {
        elapsedMillis < 60_000L -> "刚刚"
        elapsedMillis < 3_600_000L -> "${elapsedMillis / 60_000L} 分钟前"
        elapsedMillis < 86_400_000L -> "${elapsedMillis / 3_600_000L} 小时前"
        else -> "${elapsedMillis / 86_400_000L} 天前"
    }
}

@Composable
private fun ChatListRow(
    chat: ChatThread,
    avatarText: String,
    avatarUri: String?,
    avatarMembers: List<CharacterProfile>,
    onClick: () -> Unit,
    onTogglePinned: () -> Unit,
    onToggleMuted: () -> Unit,
    onMarkUnread: () -> Unit,
    onDelete: () -> Unit,
    onEditGroup: (() -> Unit)?
) {
    var menuExpanded by remember(chat.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (chat.kind == ChatKind.GROUP && avatarUri == null) {
            CompositeAvatar(avatarMembers, avatarText)
        } else {
            Avatar(avatarText, avatarUri)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (chat.pinned) {
                    Icon(
                        YumerinPinIcon,
                        contentDescription = "已置顶",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                if (chat.muted) {
                    Icon(
                        YumerinMutedIcon,
                        contentDescription = "已免打扰",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Text(
                text = chat.lastMessagePreview.ifBlank { "开始一段新的对话" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            formatChatActivityTime(chat.lastActivityAt).takeIf { it.isNotEmpty() }?.let { activityTime ->
                Text(
                    activityTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    maxLines = 1
                )
            }
            if (chat.unreadCount > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text(if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString())
                }
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = YumerinMoreIcon,
                    contentDescription = "聊天选项",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(if (chat.pinned) "取消置顶" else "置顶") },
                    onClick = { menuExpanded = false; onTogglePinned() }
                )
                DropdownMenuItem(
                    text = { Text(if (chat.muted) "取消免打扰" else "免打扰") },
                    onClick = { menuExpanded = false; onToggleMuted() }
                )
                DropdownMenuItem(
                    text = { Text("标记未读") },
                    onClick = { menuExpanded = false; onMarkUnread() }
                )
                onEditGroup?.let {
                    DropdownMenuItem(
                        text = { Text("管理群成员") },
                        onClick = { menuExpanded = false; it() }
                    )
                }
                DropdownMenuItem(
                    text = { Text("删除聊天") },
                    onClick = { menuExpanded = false; onDelete() }
                )
            }
        }
    }
}

@Composable
internal fun GroupEditorPage(
    graph: AppGraph,
    existing: GroupProfile?,
    characters: List<CharacterProfile>,
    onPickAvatar: ((onSelected: (String?) -> Unit) -> Unit),
    onBack: () -> Unit,
    onSave: (GroupProfile) -> Unit
) {
    val membershipWindows by existing?.let { graph.groupMemberships.observeWindows(it.id) }?.collectAsState(emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val chats by graph.chats.observeChats().collectAsState(emptyList())
    val groupChat = chats.firstOrNull { it.groupId == existing?.id }
    val groupMessages by groupChat?.let { graph.chats.observeMessages(it.id) }?.collectAsState(emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var avatarUri by remember(existing?.id) { mutableStateOf(existing?.avatarUri) }
    var scenario by remember(existing?.id) { mutableStateOf(existing?.scenario.orEmpty()) }
    var storySetting by remember(existing?.id) { mutableStateOf(existing?.storySetting.orEmpty()) }
    var interactionStyle by remember(existing?.id) { mutableStateOf(existing?.interactionStyle ?: "多角色依次回应，避免并行打断。") }
    var turnLimit by remember(existing?.id) { mutableStateOf((existing?.autonomousTurnLimit ?: 3).toString()) }
    var summary by remember(existing?.id) { mutableStateOf(existing?.summary.orEmpty()) }
    var summaryMode by remember(existing?.id) { mutableStateOf(existing?.summaryMaintenanceMode ?: SummaryMaintenanceMode.AUTO) }
    var selectedIds by remember(existing?.id, membershipWindows) {
        mutableStateOf(
            existing?.let { group ->
                membershipWindows.filter { it.leftAt == null }.map { it.characterId }.toSet()
                    .ifEmpty { group.memberCharacterIds.toSet() }
            } ?: emptySet()
        )
    }
    val canSave = name.isNotBlank() && selectedIds.size >= 2

    MiuixScaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { _ ->
    PageColumn {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MiuixBackButton(onClick = onBack)
            Text(if (existing == null) "创建群聊" else "管理群聊", style = MaterialTheme.typography.headlineLarge)
        }
        MiuixCardSurface(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("群名称") },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(name.ifBlank { "群" }, avatarUri)
                    Column(Modifier.weight(1f)) {
                        Text(if (avatarUri == null) "默认使用成员头像合成" else "已设置自定义群头像")
                        TextButton(onClick = { onPickAvatar { it?.let { selected -> avatarUri = selected } } }) { Text("选择自定义头像") }
                    }
                }
                OutlinedTextField(scenario, { scenario = it }, Modifier.fillMaxWidth(), label = { Text("群聊场景") })
                OutlinedTextField(storySetting, { storySetting = it }, Modifier.fillMaxWidth(), label = { Text("故事设定") })
                OutlinedTextField(interactionStyle, { interactionStyle = it }, Modifier.fillMaxWidth(), label = { Text("互动风格") })
                OutlinedTextField(turnLimit, { turnLimit = it }, Modifier.fillMaxWidth(), label = { Text("自动对话回合（1-3）") }, singleLine = true)
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("群聊上下文摘要") }
                )
                TextButton(
                    onClick = { summary = summarizeMessages(groupMessages) },
                    enabled = groupMessages.any { it.text.isNotBlank() }
                ) { Text("从群消息重建摘要") }
                OverlayDropdownMenu(
                    entry = DropdownEntry(
                        items = SummaryMaintenanceMode.entries.map { mode ->
                            DropdownItem(
                                text = if (mode == SummaryMaintenanceMode.AUTO) "自动" else "固定",
                                selected = summaryMode == mode,
                                onClick = { summaryMode = mode }
                            )
                        }
                    ),
                    title = "群摘要维护",
                    modifier = Modifier.fillMaxWidth(),
                    renderInRootScaffold = false
                )
                Text("选择至少两个角色。未参与期间的消息不会补发，重新加入后可以看到此前参与窗口的历史。")
                characters.forEach { character ->
                    val isSelected = character.id in selectedIds
                    val toggleSelection = {
                        selectedIds = if (isSelected) selectedIds - character.id else selectedIds + character.id
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = toggleSelection),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiuixCheckbox(
                            state = if (isSelected) ToggleableState.On else ToggleableState.Off,
                            onClick = toggleSelection
                        )
                        Text(character.name)
                    }
                }
                Button(
                    onClick = {
                        val now = nowMillis()
                        onSave(
                            GroupProfile(
                                id = existing?.id ?: newEntityId("group"),
                                name = name.trim(),
                                avatarUri = avatarUri,
                                memberCharacterIds = selectedIds.toList(),
                                scenario = scenario,
                                storySetting = storySetting,
                                interactionStyle = interactionStyle,
                                autonomousTurnLimit = turnLimit.toIntOrNull()?.coerceIn(1, 3) ?: 3,
                                summary = summary.trim(),
                                summaryMaintenanceMode = summaryMode,
                                archived = existing?.archived ?: false,
                                createdAt = existing?.createdAt ?: now,
                                updatedAt = now
                            )
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (existing == null) "创建" else "保存群聊") }
            }
        }
    }
    }
}


@Composable
internal fun ChatSettingsPage(
    graph: AppGraph,
    chatId: EntityId,
    chat: ChatThread?,
    characters: List<CharacterProfile>,
    groups: List<GroupProfile>,
    onChatChanged: (ChatThread) -> Unit,
    onBack: () -> Unit
) {
    val messages by graph.chats.observeMessages(chatId).collectAsState(emptyList())
    val character = chat?.characterId?.let { id -> characters.firstOrNull { it.id == id } }
    val group = chat?.groupId?.let { id -> groups.firstOrNull { it.id == id } }
    val groupMembers = group?.memberCharacterIds.orEmpty().mapNotNull { id -> characters.firstOrNull { it.id == id } }
    var showSummary by remember { mutableStateOf(false) }
    var summaryDraft by remember(chat?.id, chat?.shortTermSummary) {
        mutableStateOf(chat?.shortTermSummary.orEmpty())
    }
    var summaryMode by remember(chat?.id, chat?.summaryMaintenanceMode) {
        mutableStateOf(chat?.summaryMaintenanceMode ?: SummaryMaintenanceMode.AUTO)
    }

    MiuixScaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { _ ->
    PageColumn {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MiuixBackButton(onClick = onBack)
            Text("聊天设置", style = MaterialTheme.typography.headlineLarge)
        }
        if (chat == null) {
            MiuixCardSurface(Modifier.fillMaxWidth()) {
                Text("找不到这个聊天", modifier = Modifier.padding(18.dp))
            }
        } else {
            MiuixCardSurface(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (group != null && chat.avatarUri == null) {
                        CompositeAvatar(groupMembers, group.name)
                    } else {
                        Avatar(
                            character?.name?.take(1) ?: chat.title.take(1),
                            chat.avatarUri ?: character?.avatarUri
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(chat.title, style = MaterialTheme.typography.titleMedium)
                        Text(if (group == null) "私聊" else "群聊 · ${groupMembers.size} 位角色")
                    }
                }
            }
            MiuixCardSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    SettingRow("聊天类型", if (group == null) "私聊" else "群聊")
                    SettingRow("消息数量", "${messages.size} 条")
                    SettingRow(
                        "聊天摘要",
                        if (chat.shortTermSummary.isBlank()) "未设置" else "已设置",
                        onClick = { showSummary = true }
                    )
                    MiuixSwitchPreference(
                        title = "置顶聊天",
                        summary = "将聊天固定在列表顶部",
                        checked = chat.pinned,
                        onCheckedChange = { onChatChanged(chat.copy(pinned = it)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    MiuixSwitchPreference(
                        title = "消息免打扰",
                        summary = "关闭该聊天的提醒通知",
                        checked = chat.muted,
                        onCheckedChange = { onChatChanged(chat.copy(muted = it)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    SettingRow(
                        "标记为未读",
                        if (chat.unreadCount > 0) "有未读消息" else "当前已读",
                        onClick = { onChatChanged(chat.copy(unreadCount = maxOf(1, chat.unreadCount))) },
                        showArrow = false
                    )
                }
            }
        }
    }
    if (showSummary && chat != null) {
        MiuixOverlayDialog(
            show = true,
            title = "聊天摘要",
            summary = "摘要只属于当前聊天线程，不会写入角色长期记忆。",
            onDismissRequest = { showSummary = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = summaryDraft,
                    onValueChange = { summaryDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("摘要内容") }
                )
                OverlayDropdownMenu(
                    entry = DropdownEntry(
                        items = SummaryMaintenanceMode.entries.map { mode ->
                            DropdownItem(
                                text = if (mode == SummaryMaintenanceMode.AUTO) "自动维护" else "固定",
                                selected = summaryMode == mode,
                                onClick = { summaryMode = mode }
                            )
                        }
                    ),
                    title = "摘要维护方式",
                    modifier = Modifier.fillMaxWidth(),
                    renderInRootScaffold = true
                )
                TextButton(onClick = { summaryDraft = summarizeMessages(messages) }) {
                    Text("从近期消息重建")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showSummary = false }) { Text("取消") }
                    TextButton(onClick = {
                        onChatChanged(
                            chat.copy(
                                shortTermSummary = summaryDraft.trim(),
                                summaryMaintenanceMode = summaryMode,
                                lastActivityAt = nowMillis()
                            )
                        )
                        showSummary = false
                    }) { Text("保存") }
                }
            }
        }
    }
    }
}
