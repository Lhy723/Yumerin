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
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults as MiuixTextFieldDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry as MiuixDropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem as MiuixDropdownItem
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import kotlin.time.Clock

@Composable
internal fun ChatDetailPage(
    chatId: EntityId,
    chats: List<ChatThread>,
    characters: List<CharacterProfile>,
    groups: List<GroupProfile>,
    modelProfiles: List<ModelProfile>,
    graph: AppGraph,
    sendMessage: com.lhy.yumerin.domain.usecase.SendChatMessageUseCase,
    retryMessage: suspend (EntityId, AppSettings) -> SendChatMessageResult,
    settings: AppSettings,
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenChatSettings: () -> Unit,
    onOpenCharacterProfile: (EntityId) -> Unit,
    onOpenUserProfile: () -> Unit
) {
    val messages by graph.chats.observeMessages(chatId).collectAsState(emptyList())
    val messageScrollState = rememberScrollState()
    var followNewMessages by remember(chatId) { mutableStateOf(true) }
    var didInitialScroll by remember(chatId) { mutableStateOf(false) }
    val chat = chats.firstOrNull { it.id == chatId }
    val character = characters.firstOrNull { it.id == chat?.characterId }
    val group = groups.firstOrNull { it.id == chat?.groupId }
    val groupMembers = group?.memberCharacterIds.orEmpty().mapNotNull { id -> characters.firstOrNull { it.id == id } }
    val conversationTitle = chat?.title?.takeIf { it.isNotBlank() }
        ?: character?.name
        ?: group?.name
        ?: "聊天"
    val conversationSubtitle = when {
        group != null -> "群聊 · ${groupMembers.size} 位角色"
        chat?.kind == ChatKind.GROUP -> "群聊"
        else -> character?.relationshipFraming?.takeIf { it.isNotBlank() } ?: "角色对话"
    }
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var showModelGate by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var sendJob by remember { mutableStateOf<Job?>(null) }
    var recallText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(messageScrollState) {
        snapshotFlow { messageScrollState.value to messageScrollState.maxValue }
            .collect { (scrollValue, maxScrollValue) ->
                followNewMessages = maxScrollValue - scrollValue <= 48
            }
    }

    val latestMessageKey = messages.lastOrNull()?.let { message ->
        "${message.id}:${message.text}:${message.deliveryState}:${message.responseState}"
    } ?: "empty"
    LaunchedEffect(chatId, latestMessageKey, messageScrollState.maxValue) {
        withFrameNanos { }
        if (!didInitialScroll && messages.isNotEmpty()) {
            messageScrollState.scrollTo(messageScrollState.maxValue)
            didInitialScroll = true
            followNewMessages = true
        } else if (followNewMessages) {
            messageScrollState.animateScrollTo(messageScrollState.maxValue)
        }
    }

    fun send() {
        if (sending) {
            sendJob?.cancel()
            return
        }
        val text = draft.trim()
        if (text.isEmpty()) return
        followNewMessages = true
        sending = true
        sendJob = scope.launch {
            try {
                when (sendMessage(SendChatMessageCommand(chatId, text, settings))) {
                    SendChatMessageResult.MissingModel -> showModelGate = true
                    SendChatMessageResult.Empty -> Unit
                    is SendChatMessageResult.Completed -> draft = ""
                    is SendChatMessageResult.Failed -> draft = text
                }
            } finally {
                sending = false
                sendJob = null
            }
        }
    }

    fun retryUserMessage(message: ChatMessage) {
        if (sending) return
        followNewMessages = true
        sending = true
        sendJob = scope.launch {
            try {
                when (sendMessage(SendChatMessageCommand(chatId, message.text, settings, message.id))) {
                    SendChatMessageResult.MissingModel -> showModelGate = true
                    is SendChatMessageResult.Completed -> draft = ""
                    else -> Unit
                }
            } finally {
                sending = false
                sendJob = null
            }
        }
    }

    fun retry(message: ChatMessage) {
        if (sending) return
        followNewMessages = true
        sending = true
        sendJob = scope.launch {
            try {
                when (retryMessage(message.id, settings)) {
                    SendChatMessageResult.MissingModel -> showModelGate = true
                    else -> Unit
                }
            } finally {
                sending = false
                sendJob = null
            }
        }
    }

    MiuixScaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { _ ->
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiuixBackButton(onClick = onBack)
            Spacer(Modifier.width(10.dp))
            Avatar(
                text = (group?.name ?: character?.name ?: conversationTitle).take(1).ifBlank { "聊" },
                uri = chat?.avatarUri ?: group?.avatarUri ?: character?.avatarUri,
                onClick = character?.id?.let { id -> { onOpenCharacterProfile(id) } },
                contentDescription = when {
                    group != null -> "群聊头像"
                    character != null -> "查看${character.name}详情"
                    else -> "聊天头像"
                }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    conversationTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    conversationSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MiuixMoreButton(onClick = onOpenChatSettings)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(messageScrollState)
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    messages.forEach { message ->
                        val isMine = message.senderType == MessageSenderType.USER
                        val senderCharacter = message.senderId?.let { senderId ->
                            characters.firstOrNull { it.id == senderId }
                        }
                        MessageBubble(
                            message = message,
                            isMine = isMine,
                            avatarText = if (isMine) {
                                settings.userName.take(1).ifBlank { "Y" }
                            } else {
                                message.senderName.take(1).ifBlank { "?" }
                            },
                            avatarUri = if (isMine) {
                                settings.userAvatarUri
                            } else {
                                message.senderAvatarUri ?: senderCharacter?.avatarUri
                            },
                            onAvatarClick = if (isMine) {
                                onOpenUserProfile
                            } else {
                                message.senderId?.let { senderId ->
                                    { onOpenCharacterProfile(senderId) }
                                }
                            },
                            onRecall = if (message.usedMemoryIds.isEmpty()) null else {
                                {
                                    scope.launch {
                                        val memories = character?.let { graph.memories.observeMemories(it.id).first() }.orEmpty()
                                        recallText = message.usedMemoryIds.mapNotNull { id -> memories.firstOrNull { it.id == id }?.content }
                                            .joinToString("\n")
                                            .ifBlank { "本次回复没有可显示的记忆内容。" }
                                    }
                                }
                            },
                            onRetry = if (message.responseState == com.lhy.yumerin.core.model.AiResponseState.RETRYABLE ||
                                message.responseState == com.lhy.yumerin.core.model.AiResponseState.FAILED ||
                                message.responseState == com.lhy.yumerin.core.model.AiResponseState.STOPPED
                            ) { { retry(message) } } else null,
                            onRetryUser = if (message.senderType == MessageSenderType.USER && message.deliveryState != com.lhy.yumerin.core.model.MessageDeliveryState.SENT) {
                                { retryUserMessage(message) }
                            } else null
                        )
                    }
                    if (messages.isEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text("开始对话", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "发送第一条消息，让对话从这里开始。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                MiuixCardSurface(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 9.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (character != null && group == null) {
                                val globalDefaultModel = modelProfiles.firstOrNull {
                                    it.id == settings.defaultModelProfileId
                                }
                                val followsGlobalDefault = character.modelProfileId == null
                                val selectedModel = modelProfiles.firstOrNull {
                                    it.id == character.modelProfileId
                                }
                                val modelLabel = when {
                                    selectedModel != null -> selectedModel.displayName
                                    followsGlobalDefault && globalDefaultModel != null ->
                                        "默认 · ${globalDefaultModel.displayName}"
                                    followsGlobalDefault -> "未设置模型"
                                    else -> "模型不可用"
                                }
                                OverlayDropdownCapsule(
                                    entry = MiuixDropdownEntry(
                                        items = listOf(
                                            MiuixDropdownItem(
                                                text = globalDefaultModel?.let {
                                                    "跟随全局默认（${it.displayName}）"
                                                } ?: "跟随全局默认（未设置）",
                                                selected = followsGlobalDefault,
                                                onClick = {
                                                    scope.launch {
                                                        graph.characters.saveCharacter(
                                                            character.copy(modelProfileId = null, updatedAt = nowMillis())
                                                        )
                                                    }
                                                }
                                            )
                                        ) + modelProfiles.map { profile ->
                                            MiuixDropdownItem(
                                                text = profile.displayName,
                                                selected = profile.id == character.modelProfileId,
                                                onClick = {
                                                    scope.launch {
                                                        graph.characters.saveCharacter(
                                                            character.copy(modelProfileId = profile.id, updatedAt = nowMillis())
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    ),
                                    label = modelLabel,
                                    renderInRootScaffold = false
                                )
                            } else {
                                MiuixText(
                                    "群聊按各角色模型设置响应",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                                )
                            }
                            Box(Modifier.weight(1f))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MiuixTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                modifier = Modifier.weight(1f),
                                label = "输入消息",
                                useLabelAsPlaceholder = true,
                                cornerRadius = 18.dp,
                                minLines = 1,
                                maxLines = 4,
                                colors = MiuixTextFieldDefaults.textFieldColors(
                                    backgroundColor = Color.Transparent,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    borderColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            MiuixSendButton(
                                                                sending = sending,
                                enabled = sending || draft.isNotBlank(),
                                onClick = ::send
                            )
                        }
                    }
                }
            }
            }
        }
    }

    if (showModelGate) {
        AlertDialog(
            onDismissRequest = { showModelGate = false },
            title = { Text("还没有选择模型") },
            text = { Text("先在模型页添加一个远端或本地模型，再开始对话。刚才的消息已保留在本地并标记为失败，不会在未配置时调用模型。") },
            confirmButton = {
                TextButton(onClick = {
                    showModelGate = false
                    onOpenModels()
                }) { Text("去设置模型") }
            },
            dismissButton = { TextButton(onClick = { showModelGate = false }) { Text("稍后") } }
        )
    }
    recallText?.let { text ->
        AlertDialog(
            onDismissRequest = { recallText = null },
            title = { Text("本次回复使用的记忆") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { recallText = null }) { Text("关闭") } }
        )
    }
    }
}


@Composable
private fun MessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    avatarText: String,
    avatarUri: String?,
    onAvatarClick: (() -> Unit)?,
    onRecall: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    onRetryUser: (() -> Unit)? = null
) {
    val isSystem = message.senderType == MessageSenderType.SYSTEM
    val bubbleShape = when {
        isSystem -> RoundedCornerShape(16.dp)
        isMine -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
        else -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
    }
    val bubbleColor = when {
        isSystem -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        isMine -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    }
    val bubbleContentColor = when {
        isSystem -> MaterialTheme.colorScheme.onSurfaceVariant
        isMine -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = when {
            isSystem -> Arrangement.Center
            isMine -> Arrangement.End
            else -> Arrangement.Start
        },
        verticalAlignment = Alignment.Top
    ) {
        if (!isMine && !isSystem) {
            Avatar(
                text = avatarText,
                uri = avatarUri,
                onClick = onAvatarClick,
                contentDescription = "查看${message.senderName}详情"
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isSystem) 0.92f else 0.75f)
                .widthIn(max = 340.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = bubbleShape,
                color = bubbleColor,
                contentColor = bubbleContentColor,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isMine) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!isMine && !isSystem) {
                        Text(
                            message.senderName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = message.text.ifBlank {
                            when (message.responseState) {
                                com.lhy.yumerin.core.model.AiResponseState.GENERATING -> "正在输入…"
                                com.lhy.yumerin.core.model.AiResponseState.STOPPED -> "已停止生成"
                                else -> ""
                            }
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    message.errorMessage?.let {
                        Text("状态：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (isMine && message.deliveryState != com.lhy.yumerin.core.model.MessageDeliveryState.SENT) {
                        Text("消息尚未成功处理", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (message.responseState == com.lhy.yumerin.core.model.AiResponseState.RETRYABLE) {
                        Text("可重试此回复", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (onRetry != null || onRetryUser != null || onRecall != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            onRetry?.let { TextButton(onClick = it) { Text("重试") } }
                            onRetryUser?.let { TextButton(onClick = it) { Text("重试发送") } }
                            onRecall?.let { TextButton(onClick = it) { Text("查看召回记忆") } }
                        }
                    }
                }
            }
        }
        if (isMine && !isSystem) {
            Spacer(Modifier.width(8.dp))
            Avatar(
                text = avatarText,
                uri = avatarUri,
                onClick = onAvatarClick,
                contentDescription = "查看用户详情"
            )
        }
    }
}
