package com.lhy.yumerin

import com.lhy.yumerin.designsystem.YumerinSpacing

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
import com.lhy.yumerin.designsystem.YumerinAlertDialog as AlertDialog
import com.lhy.yumerin.designsystem.YumerinButton as Button
import com.lhy.yumerin.designsystem.YumerinOutlinedButton as OutlinedButton
import com.lhy.yumerin.designsystem.YumerinOutlinedTextField as OutlinedTextField
import com.lhy.yumerin.designsystem.YumerinTextButton as TextButton
import top.yukonga.miuix.kmp.basic.HorizontalDivider as HorizontalDivider
import com.lhy.yumerin.domain.*
import com.lhy.yumerin.domain.memory.*
import com.lhy.yumerin.domain.repository.*
import com.lhy.yumerin.domain.usecase.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Clock
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold

@Composable
internal fun CharactersPage(
    characters: List<CharacterProfile>,
    archivedCharacters: List<CharacterProfile>,
    onCreate: () -> Unit,
    onOpenProfile: (EntityId) -> Unit,
    onEdit: (EntityId) -> Unit,
    onCreateChat: (CharacterProfile) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }
    val filteredCharacters = remember(characters, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) characters
        else characters.filter { character ->
            character.name.contains(normalizedQuery, ignoreCase = true) ||
                character.description.contains(normalizedQuery, ignoreCase = true)
        }
    }
    val filteredArchived = remember(archivedCharacters, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) archivedCharacters
        else archivedCharacters.filter { character ->
            character.name.contains(normalizedQuery, ignoreCase = true) ||
                character.description.contains(normalizedQuery, ignoreCase = true)
        }
    }
    PageColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("角色", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "独立的记忆、语气与关系设定",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MiuixActionIconButton(
                                icon = YumerinAddIcon,
                contentDescription = "新建角色",
                onClick = onCreate
            )
        }

        if (characters.size + archivedCharacters.size > 5) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = "搜索角色",
                placeholder = "按名称或介绍查找",
                singleLine = true
            )
        }

        if (filteredCharacters.isEmpty() && characters.isEmpty()) {
            MiuixCardSurface(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = YumerinSpacing.CardInset, vertical = YumerinSpacing.PageSection),
                    verticalArrangement = Arrangement.spacedBy(YumerinSpacing.FormField)
                ) {
                    Text("从一个角色开始", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "为角色设定关系、语气和背景；聊天记录与长期记忆会分别保存。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onCreate) { Text("创建角色") }
                }
            }
        } else if (filteredCharacters.isEmpty()) {
            Text(
                "没有找到匹配的角色",
                modifier = Modifier.padding(horizontal = YumerinSpacing.Compact, vertical = YumerinSpacing.PageVertical),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            filteredCharacters.forEach { character ->
                CharacterCard(character, false, onOpenProfile, onEdit, onCreateChat)
            }
        }

        if (filteredArchived.isNotEmpty()) {
            TextButton(
                onClick = { showArchived = !showArchived },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (showArchived) "收起已归档（${filteredArchived.size}）" else "已归档（${filteredArchived.size}）")
            }
            AnimatedVisibility(visible = showArchived) {
                Column(verticalArrangement = Arrangement.spacedBy(YumerinSpacing.Field)) {
                    filteredArchived.forEach { character ->
                        CharacterCard(character, true, onOpenProfile, onEdit, onCreateChat)
                    }
                }
            }
        } else if (archivedCharacters.isNotEmpty()) {
            Text("没有匹配的已归档角色", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun CharacterCard(
    character: CharacterProfile,
    archived: Boolean,
    onOpenProfile: (EntityId) -> Unit,
    onEdit: (EntityId) -> Unit,
    onCreateChat: (CharacterProfile) -> Unit
) {
    MiuixCardSurface(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(YumerinSpacing.CardVertical),
            verticalArrangement = Arrangement.spacedBy(YumerinSpacing.Row)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenProfile(character.id) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Row)
            ) {
                Avatar(character.name.take(1), character.avatarUri)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(YumerinSpacing.Compact)) {
                    Text(character.name, style = MaterialTheme.typography.titleLarge)
                    if (archived) {
                        Text("已归档", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                character.description.ifBlank { "还没有添加介绍。" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onEdit(character.id) }) {
                    Text(if (archived) "管理角色" else "编辑资料")
                }
                if (!archived) {
                    Spacer(Modifier.width(YumerinSpacing.Item))
                    Button(onClick = { onCreateChat(character) }) { Text("开始聊天") }
                }
            }
        }
    }
}


@Composable
internal fun CharacterDetailPage(
    character: CharacterProfile?,
    groups: List<GroupProfile>,
    chats: List<ChatThread>,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenMemory: () -> Unit,
    onEdit: () -> Unit,
    onOpenGroup: (EntityId) -> Unit
) {
    val relatedGroups = remember(character?.id, groups) {
        character?.let { profile ->
            groups.filter { profile.id in it.memberCharacterIds && it.deletedAt == null }
        }.orEmpty()
    }
    val directChat = remember(character?.id, chats) {
        character?.let { profile ->
            chats.filter { it.characterId == profile.id && it.deletedAt == null }
                .maxByOrNull { it.lastActivityAt }
        }
    }

    PageColumn {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Field)
        ) {
            MiuixBackButton(onClick = onBack)
            Text("角色详情", style = MaterialTheme.typography.titleLarge)
        }

        if (character == null) {
            MiuixCardSurface(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(horizontal = YumerinSpacing.LargeInset, vertical = YumerinSpacing.PageSection),
                    verticalArrangement = Arrangement.spacedBy(YumerinSpacing.Item)
                ) {
                    Text("角色不存在", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "这个角色可能已被删除。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(YumerinSpacing.FormField)
            ) {
                Avatar(
                    text = character.name.take(1).ifBlank { "?" },
                    uri = character.avatarUri,
                    size = 104.dp,
                    contentDescription = "${character.name}的头像"
                )
                Text(character.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (character.archived) "已归档角色" else "Yumerin 角色",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Item)
            ) {
                ProfileActionTile(
                                        icon = YumerinChatIcon,
                    title = if (directChat == null) "开始聊天" else "继续聊天",
                    modifier = Modifier.weight(1f),
                    enabled = directChat != null || !character.archived,
                    onClick = onOpenChat
                )
                ProfileActionTile(
                                        icon = YumerinMemoryIcon,
                    title = "记忆",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenMemory
                )
                ProfileActionTile(
                                        icon = YumerinEditIcon,
                    title = "编辑资料",
                    modifier = Modifier.weight(1f),
                    onClick = onEdit
                )
            }

            Text("角色资料", style = MaterialTheme.typography.titleMedium)
            MiuixCardSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    CharacterDetailInfoRow(
                        label = "角色介绍",
                        value = character.description,
                        fallback = "还没有添加介绍。"
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = YumerinSpacing.CardInset),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                    CharacterDetailInfoRow(
                        label = "关系设定",
                        value = character.relationshipFraming,
                        fallback = "尚未设置"
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = YumerinSpacing.CardInset),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                    CharacterDetailInfoRow(
                        label = "性格与语气",
                        value = listOf(character.personality, character.tone)
                            .filter { it.isNotBlank() }
                            .joinToString("\n")
                            .ifBlank { "尚未设置" }
                    )
                    val settingDetails = listOf(character.setting, character.backstory, character.interactionStyle)
                        .filter { it.isNotBlank() }
                        .joinToString("\n\n")
                    if (settingDetails.isNotBlank()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = YumerinSpacing.CardInset),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )
                        CharacterDetailInfoRow(label = "背景与互动方式", value = settingDetails)
                    }
                }
            }

            Text("群组", style = MaterialTheme.typography.titleMedium)
            if (relatedGroups.isEmpty()) {
                MiuixCardSurface(Modifier.fillMaxWidth()) {
                    Text(
                        "还没有加入群聊",
                        modifier = Modifier.padding(horizontal = YumerinSpacing.CardInset, vertical = YumerinSpacing.CardInset),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                relatedGroups.forEach { group ->
                    val groupChatExists = chats.any { it.groupId == group.id && it.deletedAt == null }
                    CharacterGroupCard(
                                                group = group,
                        enabled = groupChatExists,
                        onClick = { onOpenGroup(group.id) }
                    )
                }
            }
        }
        Spacer(Modifier.height(YumerinSpacing.PageSection))
    }
}

@Composable
private fun CharacterDetailInfoRow(
    label: String,
    value: String,
    fallback: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = YumerinSpacing.CardInset, vertical = YumerinSpacing.Row),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value.ifBlank { fallback.orEmpty() },
            style = MaterialTheme.typography.bodyLarge,
            color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CharacterGroupCard(
    group: GroupProfile,
    enabled: Boolean,
    onClick: () -> Unit
) {
    MiuixCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = YumerinSpacing.CardInset, vertical = YumerinSpacing.CardVertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Row)
        ) {
            Avatar(group.name.take(1).ifBlank { "群" }, group.avatarUri, size = YumerinSize.Avatar)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(YumerinSpacing.Compact)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (group.archived) "已归档 · ${group.memberCharacterIds.size} 位角色" else "群聊 · ${group.memberCharacterIds.size} 位角色",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (enabled) {
                Icon(
                    imageVector = YumerinChevronRight,
                    contentDescription = "打开群聊",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(YumerinSize.SmallIcon)
                )
            }
        }
    }
}


@Composable
internal fun CharacterEditorPage(
    existing: CharacterProfile?,
    modelProfiles: List<ModelProfile>,
    defaultModelProfileId: EntityId?,
    onBack: () -> Unit,
    onPickAvatar: ((onSelected: (String?) -> Unit) -> Unit),
    onOpenMemory: (() -> Unit)?,
    onArchive: ((Boolean) -> Unit)?,
    onDelete: (() -> Unit)?,
    onSave: (CharacterProfile) -> Unit
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var avatarUri by remember(existing?.id) { mutableStateOf(existing?.avatarUri) }
    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var personality by remember(existing?.id) { mutableStateOf(existing?.personality.orEmpty()) }
    var tone by remember(existing?.id) { mutableStateOf(existing?.tone.orEmpty()) }
    var relationship by remember(existing?.id) { mutableStateOf(existing?.relationshipFraming.orEmpty()) }
    var setting by remember(existing?.id) { mutableStateOf(existing?.setting.orEmpty()) }
    var backstory by remember(existing?.id) { mutableStateOf(existing?.backstory.orEmpty()) }
    var interactionStyle by remember(existing?.id) { mutableStateOf(existing?.interactionStyle.orEmpty()) }
    var selectedModelId by remember(existing?.id) { mutableStateOf(existing?.modelProfileId) }
    var proactiveEnabled by remember(existing?.id) { mutableStateOf(existing?.proactiveEnabled ?: false) }
    var proactiveDailyLimit by remember(existing?.id) { mutableStateOf((existing?.proactiveDailyLimit ?: 3).toString()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val canSave = name.isNotBlank()
    val selectedModel = modelProfiles.firstOrNull { it.id == selectedModelId }
    val globalDefaultModel = modelProfiles.firstOrNull { it.id == defaultModelProfileId }

    MiuixScaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { _ ->
    PageColumn {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Field)
        ) {
            MiuixBackButton(onClick = onBack)
            Text(if (existing == null) "创建角色" else "编辑角色", style = MaterialTheme.typography.headlineLarge)
        }
        CharacterEditorSection("角色资料") {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = "名称", singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Field)) {
                Avatar(name.ifBlank { "?" }, avatarUri)
                Column(Modifier.weight(1f)) {
                    Text(if (avatarUri == null) "使用首字母头像" else "已设置本地头像")
                    TextButton(onClick = { onPickAvatar { it?.let { selected -> avatarUri = selected } } }) { Text("选择头像") }
                }
            }
            OutlinedTextField(
                description,
                { description = it },
                Modifier.fillMaxWidth(),
                label = "角色介绍",
                minLines = 2,
                maxLines = 4
            )
        }

        CharacterEditorSection("个性与背景") {
            OutlinedTextField(relationship, { relationship = it }, Modifier.fillMaxWidth(), label = "关系设定", minLines = 2)
            OutlinedTextField(personality, { personality = it }, Modifier.fillMaxWidth(), label = "人格", minLines = 2)
            OutlinedTextField(tone, { tone = it }, Modifier.fillMaxWidth(), label = "语气", minLines = 2)
            OutlinedTextField(setting, { setting = it }, Modifier.fillMaxWidth(), label = "世界观 / 场景", minLines = 2)
            OutlinedTextField(backstory, { backstory = it }, Modifier.fillMaxWidth(), label = "背景故事", minLines = 2)
            OutlinedTextField(interactionStyle, { interactionStyle = it }, Modifier.fillMaxWidth(), label = "互动风格", minLines = 2)
        }

        CharacterEditorSection("对话与主动互动") {
            OverlayDropdownMenu(
                entry = DropdownEntry(
                    items = listOf(
                        DropdownItem(
                            text = globalDefaultModel?.let { "跟随全局默认（${it.displayName}）" }
                                ?: "跟随全局默认（未设置）",
                            selected = selectedModelId == null,
                            onClick = { selectedModelId = null }
                        )
                    ) + modelProfiles.map { profile ->
                        DropdownItem(
                            text = profile.displayName,
                            selected = selectedModelId == profile.id,
                            onClick = { selectedModelId = profile.id }
                        )
                    }
                ),
                title = "使用模型",
                summary = selectedModel?.let { "${it.providerName} · ${it.modelId}" }
                    ?: globalDefaultModel?.let { "默认：${it.providerName} · ${it.modelId}" }
                    ?: "尚未设置全局默认模型",
                modifier = Modifier.fillMaxWidth(),
                renderInRootScaffold = false
            )
            if (modelProfiles.isEmpty()) {
                Text("还没有模型配置。先在设置的模型页面添加可用模型。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SwitchRow("允许主动消息", proactiveEnabled) { proactiveEnabled = it }
            OutlinedTextField(
                proactiveDailyLimit,
                { proactiveDailyLimit = it },
                Modifier.fillMaxWidth(),
                label = "每日主动消息上限",
                supportingText = { Text("范围 1–20，默认 3 条") },
                singleLine = true
            )
        }

        if (onOpenMemory != null || onArchive != null || onDelete != null) {
            Column(verticalArrangement = Arrangement.spacedBy(YumerinSpacing.Compact)) {
                onOpenMemory?.let {
                    OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text("管理长期记忆") }
                }
                onArchive?.let {
                    TextButton(
                        onClick = { it(existing?.archived != true) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (existing?.archived == true) "恢复角色" else "归档角色")
                    }
                }
                onDelete?.let {
                    TextButton(
                        onClick = { showDeleteConfirmation = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("永久删除角色", color = MaterialTheme.colorScheme.error) }
                }
            }
        }

        Button(
            onClick = {
                val now = nowMillis()
                onSave(
                    CharacterProfile(
                        id = existing?.id ?: newEntityId("character"),
                        name = name.trim(),
                        avatarUri = avatarUri,
                        relationshipFraming = relationship,
                        setting = setting,
                        description = description,
                        personality = personality,
                        tone = tone,
                        backstory = backstory,
                        interactionStyle = interactionStyle,
                        modelProfileId = selectedModelId,
                        archived = existing?.archived ?: false,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now,
                        proactiveEnabled = proactiveEnabled,
                        proactiveDailyLimit = proactiveDailyLimit.toIntOrNull()?.coerceIn(1, 20) ?: 3,
                        deletedAt = existing?.deletedAt
                    )
                )
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth()
        ) { Text("保存角色") }
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("永久删除角色？") },
            text = { Text("这会删除角色资料、私聊、独立记忆和本地头像；群聊历史会保留，但历史消息会显示为“已删除角色”。此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirmation = false; onDelete?.invoke() }) { Text("永久删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("取消") } }
        )
    }
    }
}

@Composable
private fun CharacterEditorSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    MiuixCardSurface(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(YumerinSpacing.CardInset),
            verticalArrangement = Arrangement.spacedBy(YumerinSpacing.Field)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}


@Composable
internal fun MemoryPage(
    character: CharacterProfile?,
    graph: AppGraph,
    onBack: () -> Unit
) {
    if (character == null) {
        PageColumn {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Field)
            ) {
                MiuixBackButton(onClick = onBack)
                Text("找不到角色", style = MaterialTheme.typography.headlineLarge)
            }
        }
        return
    }
    val memories by graph.memories.observeMemories(character.id).collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var editingMemory by remember { mutableStateOf<MemoryEntry?>(null) }
    var editingText by remember { mutableStateOf("") }
    var showPendingOnly by remember { mutableStateOf(false) }
    PageColumn {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Field)
        ) {
            MiuixBackButton(onClick = onBack)
            Text("${character.name}的记忆", style = MaterialTheme.typography.headlineLarge)
        }
        Text("只属于这个角色。删除聊天不会删除这里的长期记忆。")
        Row(horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Item)) {
            TextButton(onClick = { showPendingOnly = !showPendingOnly }) {
                Text(if (showPendingOnly) "显示全部" else "只看待审核（${memories.count { it.reviewState == MemoryReviewState.PENDING_REVIEW }}）")
            }
            if (memories.any { it.reviewState == MemoryReviewState.PENDING_REVIEW }) {
                TextButton(onClick = {
                    scope.launch {
                        memories.filter { it.reviewState == MemoryReviewState.PENDING_REVIEW }
                            .forEach { graph.memories.saveMemory(it.copy(reviewState = MemoryReviewState.ACCEPTED, reviewReason = null, updatedAt = nowMillis())) }
                    }
                }) { Text("批量接受") }
                TextButton(onClick = {
                    scope.launch {
                        memories.filter { it.reviewState == MemoryReviewState.PENDING_REVIEW }
                            .forEach { graph.memories.saveMemory(it.copy(reviewState = MemoryReviewState.REJECTED, updatedAt = nowMillis())) }
                    }
                }) { Text("批量拒绝") }
            }
        }
        val visibleMemories = memories.filter { !showPendingOnly || it.reviewState == MemoryReviewState.PENDING_REVIEW }
        if (visibleMemories.isEmpty()) {
            MiuixCardSurface(Modifier.fillMaxWidth()) {
                Text("还没有稳定记忆。普通聊天中的明确偏好和资料会在开启自动维护时写入。", Modifier.padding(YumerinSpacing.CardInset))
            }
        } else {
            visibleMemories.forEach { memory ->
                MiuixCardSurface(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(YumerinSpacing.CardVertical),
                        verticalArrangement = Arrangement.spacedBy(YumerinSpacing.Item)
                    ) {
                        Text(memory.content, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${memory.category.label()} · 置信度 ${(memory.confidence * 100).toInt()}%" +
                                (if (memory.pinned) " · 已固定" else "") +
                                " · ${memory.reviewState.name}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                        )
                        memory.reviewReason?.let { Text("审核原因：$it", color = MaterialTheme.colorScheme.error) }
                        Text("来源：${memory.sourceChatId ?: "未知聊天"} · ${memory.createdAt}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Item)) {
                            TextButton(onClick = {
                                editingMemory = memory
                                editingText = memory.content
                            }) { Text("编辑") }
                            if (memory.reviewState == MemoryReviewState.PENDING_REVIEW) {
                                TextButton(onClick = {
                                    scope.launch { graph.memories.saveMemory(memory.copy(reviewState = MemoryReviewState.ACCEPTED, reviewReason = null, updatedAt = nowMillis())) }
                                }) { Text("接受") }
                                TextButton(onClick = {
                                    scope.launch { graph.memories.saveMemory(memory.copy(reviewState = MemoryReviewState.REJECTED, updatedAt = nowMillis())) }
                                }) { Text("拒绝") }
                            }
                            if (memory.reviewState == MemoryReviewState.SUPERSEDED) {
                                TextButton(onClick = {
                                    scope.launch { graph.memories.saveMemory(memory.copy(reviewState = MemoryReviewState.ACCEPTED, updatedAt = nowMillis())) }
                                }) { Text("恢复") }
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    graph.memories.saveMemory(
                                        memory.copy(
                                            pinned = !memory.pinned,
                                            updatedAt = nowMillis()
                                        )
                                    )
                                }
                            }) { Text(if (memory.pinned) "取消固定" else "固定") }
                            TextButton(onClick = {
                                scope.launch { graph.memories.deleteMemory(memory.id) }
                            }) { Text("删除") }
                        }
                    }
                }
            }
        }
    }
    editingMemory?.let { memory ->
        AlertDialog(
            onDismissRequest = { editingMemory = null },
            title = { Text("编辑记忆") },
            text = { OutlinedTextField(editingText, { editingText = it }, Modifier.fillMaxWidth(), label = "内容") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        graph.memories.saveMemory(memory.copy(content = editingText.trim(), updatedAt = nowMillis()))
                        editingMemory = null
                    }
                }, enabled = editingText.isNotBlank()) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingMemory = null }) { Text("取消") } }
        )
    }
}
