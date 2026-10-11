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
internal fun CharactersPage(
    backdrop: Backdrop,
    characters: List<CharacterProfile>,
    archivedCharacters: List<CharacterProfile>,
    onCreate: () -> Unit,
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
            GlassIconButton(
                backdrop = backdrop,
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
                label = { Text("搜索角色") },
                placeholder = { Text("按名称或介绍查找") },
                singleLine = true
            )
        }

        if (filteredCharacters.isEmpty() && characters.isEmpty()) {
            GlassSurface(
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                surfaceAlpha = 0.6f
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            filteredCharacters.forEach { character ->
                CharacterCard(backdrop, character, false, onEdit, onCreateChat)
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    filteredArchived.forEach { character ->
                        CharacterCard(backdrop, character, true, onEdit, onCreateChat)
                    }
                }
            }
        } else if (archivedCharacters.isNotEmpty()) {
            Text("没有匹配的已归档角色", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CharacterCard(
    backdrop: Backdrop,
    character: CharacterProfile,
    archived: Boolean,
    onEdit: (EntityId) -> Unit,
    onCreateChat: (CharacterProfile) -> Unit
) {
    GlassSurface(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        surfaceAlpha = 0.62f
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Avatar(character.name.take(1), character.avatarUri)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onCreateChat(character) }) { Text("开始聊天") }
                }
            }
        }
    }
}


@Composable
internal fun CharacterEditorPage(
    backdrop: Backdrop,
    existing: CharacterProfile?,
    modelProfiles: List<ModelProfile>,
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

    PageColumn {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassBackButton(backdrop = backdrop, onClick = onBack)
            Text(if (existing == null) "创建角色" else "编辑角色", style = MaterialTheme.typography.headlineLarge)
        }
        CharacterEditorSection(backdrop, "角色资料") {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("名称") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                label = { Text("角色介绍") },
                minLines = 2,
                maxLines = 4
            )
        }

        CharacterEditorSection(backdrop, "个性与背景") {
            OutlinedTextField(relationship, { relationship = it }, Modifier.fillMaxWidth(), label = { Text("关系设定") }, minLines = 2)
            OutlinedTextField(personality, { personality = it }, Modifier.fillMaxWidth(), label = { Text("人格") }, minLines = 2)
            OutlinedTextField(tone, { tone = it }, Modifier.fillMaxWidth(), label = { Text("语气") }, minLines = 2)
            OutlinedTextField(setting, { setting = it }, Modifier.fillMaxWidth(), label = { Text("世界观 / 场景") }, minLines = 2)
            OutlinedTextField(backstory, { backstory = it }, Modifier.fillMaxWidth(), label = { Text("背景故事") }, minLines = 2)
            OutlinedTextField(interactionStyle, { interactionStyle = it }, Modifier.fillMaxWidth(), label = { Text("互动风格") }, minLines = 2)
        }

        CharacterEditorSection(backdrop, "对话与主动互动") {
            Text("使用模型", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { selectedModelId = null },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selectedModelId == null, onClick = { selectedModelId = null })
                Text("使用全局默认模型")
            }
            modelProfiles.forEach { profile ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable { selectedModelId = profile.id },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedModelId == profile.id,
                        onClick = { selectedModelId = profile.id }
                    )
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(profile.displayName)
                        Text(
                            "${profile.providerName} · ${profile.modelId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (modelProfiles.isEmpty()) {
                Text("还没有模型配置。先在设置的模型页面添加可用模型。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SwitchRow("允许主动消息", proactiveEnabled) { proactiveEnabled = it }
            OutlinedTextField(
                proactiveDailyLimit,
                { proactiveDailyLimit = it },
                Modifier.fillMaxWidth(),
                label = { Text("每日主动消息上限") },
                supportingText = { Text("范围 1–20，默认 3 条") },
                singleLine = true
            )
        }

        if (onOpenMemory != null || onArchive != null || onDelete != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

@Composable
private fun CharacterEditorSection(
    backdrop: Backdrop,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassSurface(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        surfaceAlpha = 0.62f
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}


@Composable
internal fun MemoryPage(
    backdrop: Backdrop,
    character: CharacterProfile?,
    graph: AppGraph,
    onBack: () -> Unit
) {
    if (character == null) {
        PageColumn {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassBackButton(backdrop = backdrop, onClick = onBack)
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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassBackButton(backdrop = backdrop, onClick = onBack)
            Text("${character.name}的记忆", style = MaterialTheme.typography.headlineLarge)
        }
        Text("只属于这个角色。删除聊天不会删除这里的长期记忆。")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            GlassSurface(backdrop, Modifier.fillMaxWidth()) {
                Text("还没有稳定记忆。普通聊天中的明确偏好和资料会在开启自动维护时写入。", Modifier.padding(18.dp))
            }
        } else {
            visibleMemories.forEach { memory ->
                GlassSurface(backdrop, Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            text = { OutlinedTextField(editingText, { editingText = it }, Modifier.fillMaxWidth(), label = { Text("内容") }) },
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
