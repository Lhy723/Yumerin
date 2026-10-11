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
internal fun ChatListPage(
    backdrop: Backdrop,
    chats: List<ChatThread>,
    characters: List<CharacterProfile>,
    groups: List<GroupProfile>,
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

    PageColumn {
        Row(
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("聊天", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "继续和角色对话",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(
                onClick = onCreateGroup,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(YumerinAddIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("群聊")
            }
        }
        if (chats.isEmpty()) {
            GlassSurface(backdrop, Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("还没有聊天", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "创建角色后，就可以在这里开始对话。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            GlassSurface(
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                surfaceAlpha = 0.86f
            ) {
                Column {
                    orderedChats.forEachIndexed { index, chat ->
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
        if (chat.unreadCount > 0) {
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text(if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString())
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
    backdrop: Backdrop,
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

    PageColumn {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassBackButton(backdrop = backdrop, onClick = onBack)
            Text(if (existing == null) "创建群聊" else "管理群聊", style = MaterialTheme.typography.headlineLarge)
        }
        GlassSurface(backdrop, Modifier.fillMaxWidth()) {
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
                Text("群摘要维护")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SummaryMaintenanceMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = summaryMode == mode,
                            onClick = { summaryMode = mode },
                            shape = SegmentedButtonDefaults.itemShape(index, SummaryMaintenanceMode.entries.size)
                        ) { Text(if (mode == SummaryMaintenanceMode.AUTO) "自动" else "固定") }
                    }
                }
                Text("选择至少两个角色。未参与期间的消息不会补发，重新加入后可以看到此前参与窗口的历史。")
                characters.forEach { character ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedIds = if (character.id in selectedIds) {
                                    selectedIds - character.id
                                } else {
                                    selectedIds + character.id
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = character.id in selectedIds,
                            onClick = {
                                selectedIds = if (character.id in selectedIds) {
                                    selectedIds - character.id
                                } else {
                                    selectedIds + character.id
                                }
                            }
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


@Composable
internal fun ChatSettingsPage(
    backdrop: Backdrop,
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

    PageColumn {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassBackButton(backdrop = backdrop, onClick = onBack)
            Text("聊天设置", style = MaterialTheme.typography.headlineLarge)
        }
        if (chat == null) {
            GlassSurface(backdrop, Modifier.fillMaxWidth()) {
                Text("找不到这个聊天", modifier = Modifier.padding(18.dp))
            }
        } else {
            GlassSurface(backdrop, Modifier.fillMaxWidth()) {
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
            GlassSurface(backdrop, Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    SettingRow("聊天类型", if (group == null) "私聊" else "群聊")
                    SettingRow("消息数量", "${messages.size} 条")
                    SettingRow(
                        "聊天摘要",
                        if (chat.shortTermSummary.isBlank()) "未设置" else "已设置",
                        onClick = { showSummary = true }
                    )
                    SettingRow(
                        "置顶聊天",
                        if (chat.pinned) "已开启" else "未开启",
                        onClick = { onChatChanged(chat.copy(pinned = !chat.pinned)) }
                    )
                    SettingRow(
                        "消息免打扰",
                        if (chat.muted) "已开启" else "未开启",
                        onClick = { onChatChanged(chat.copy(muted = !chat.muted)) }
                    )
                    SettingRow(
                        "标记为未读",
                        if (chat.unreadCount > 0) "有未读消息" else "当前已读",
                        onClick = { onChatChanged(chat.copy(unreadCount = maxOf(1, chat.unreadCount))) }
                    )
                }
            }
        }
    }
    if (showSummary && chat != null) {
        AlertDialog(
            onDismissRequest = { showSummary = false },
            title = { Text("聊天摘要") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("摘要只属于当前聊天线程，不会写入角色长期记忆。")
                    OutlinedTextField(
                        value = summaryDraft,
                        onValueChange = { summaryDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("摘要内容") }
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SummaryMaintenanceMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = summaryMode == mode,
                                onClick = { summaryMode = mode },
                                shape = SegmentedButtonDefaults.itemShape(index, SummaryMaintenanceMode.entries.size)
                            ) { Text(if (mode == SummaryMaintenanceMode.AUTO) "自动维护" else "固定") }
                        }
                    }
                    TextButton(onClick = { summaryDraft = summarizeMessages(messages) }) {
                        Text("从近期消息重建")
                    }
                }
            },
            confirmButton = {
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
            },
            dismissButton = { TextButton(onClick = { showSummary = false }) { Text("取消") } }
        )
    }
}
