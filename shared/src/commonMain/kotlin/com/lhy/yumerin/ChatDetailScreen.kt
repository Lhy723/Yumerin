package com.lhy.yumerin

import androidx.compose.animation.*
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.dp
import com.lhy.yumerin.core.model.*
import com.lhy.yumerin.data.memory.*
import com.lhy.yumerin.designsystem.*
import com.lhy.yumerin.designsystem.YumerinAlertDialog as AlertDialog
import com.lhy.yumerin.designsystem.YumerinTextButton as TextButton
import top.yukonga.miuix.kmp.basic.Surface as Surface
import com.lhy.yumerin.domain.*
import com.lhy.yumerin.domain.memory.*
import com.lhy.yumerin.domain.repository.*
import com.lhy.yumerin.domain.usecase.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults as MiuixTextFieldDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry as MiuixDropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem as MiuixDropdownItem
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText

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
    val chatBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    var composerSurfaceHeightPx by remember(chatId) { mutableIntStateOf(0) }
    val composerBottomPadding = with(density) { composerSurfaceHeightPx.toDp() } + YumerinChatTokens.ComposerBottomClearance
    var followNewMessages by remember(chatId) { mutableStateOf(true) }
    var didInitialScroll by remember(chatId) { mutableStateOf(false) }
    var pendingAutoScroll by remember(chatId) { mutableStateOf(false) }
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
        } else if (pendingAutoScroll) {
            messageScrollState.animateScrollTo(messageScrollState.maxValue)
            pendingAutoScroll = false
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
        draft = ""
        followNewMessages = true
        pendingAutoScroll = true
        sending = true
        sendJob = scope.launch {
            try {
                when (sendMessage(SendChatMessageCommand(chatId, text, settings))) {
                    SendChatMessageResult.MissingModel -> showModelGate = true
                    SendChatMessageResult.Empty -> {
                        if (draft.isBlank()) draft = text
                        pendingAutoScroll = false
                    }
                    is SendChatMessageResult.Completed -> Unit
                    is SendChatMessageResult.Failed -> {
                        if (draft.isBlank()) draft = text
                        pendingAutoScroll = false
                    }
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
        pendingAutoScroll = true
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
        pendingAutoScroll = true
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
                .padding(horizontal = YumerinSpacing.Field, vertical = YumerinSpacing.Item),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Item)
        ) {
            MiuixBackButton(onClick = onBack)
            MiuixGlassSurface(
                modifier = Modifier
                    .weight(1f)
                    .height(YumerinChatTokens.HeaderHeight),
                shape = RoundedCornerShape(YumerinChatTokens.HeaderShapeRadius)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = YumerinChatTokens.HeaderInsetHorizontal),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(YumerinChatTokens.HeaderAvatarGap)
                ) {
                    Avatar(
                        text = (group?.name ?: character?.name ?: conversationTitle).take(1).ifBlank { "聊" },
                        uri = chat?.avatarUri ?: group?.avatarUri ?: character?.avatarUri,
                        onClick = character?.id?.let { id -> { onOpenCharacterProfile(id) } },
                        contentDescription = when {
                            group != null -> "群聊头像"
                            character != null -> "查看${character.name}详情"
                            else -> "聊天头像"
                        },
                        size = YumerinChatTokens.HeaderAvatar
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(YumerinChatTokens.HeaderTitleGap)
                    ) {
                        MiuixText(
                            conversationTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        MiuixText(
                            conversationSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            MiuixMoreButton(onClick = onOpenChatSettings)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(chatBackdrop)
                    .verticalScroll(messageScrollState)
                    .padding(
                        start = YumerinSpacing.PageHorizontal,
                        end = YumerinSpacing.PageHorizontal,
                        top = YumerinSpacing.FormField,
                        bottom = composerBottomPadding
                    ),
                verticalArrangement = Arrangement.spacedBy(YumerinChatTokens.MessageGap)
            ) {
                val activeGoal by graph.delegatedAgentGoals.observeGoals().collectAsState(null)
                activeGoal?.filter { it.chatId == chatId && it.status in ACTIVE_GOAL_STATUSES }
                    ?.maxByOrNull { it.updatedAt }
                    ?.let { goal ->
                        DelegatedGoalProgressCard(goal = goal)
                    }
                messages.forEach { message ->
                    val isMine = message.senderType == MessageSenderType.USER
                    val senderCharacter = message.senderId?.let { senderId ->
                        characters.firstOrNull { it.id == senderId }
                    }
                    MessageBubble(
                        message = message,
                        reducedMotion = settings.reducedMotionEnabled,
                        isMine = isMine,
                        avatarText = if (isMine) {
                            settings.userName.take(1).ifBlank { "Y" }
                        } else {
                            message.senderName.take(1).ifBlank { "?" }
                        },
                        avatarUri = if (isMine) {
                            settings.userAvatarUri
                        } else if (senderCharacter != null) {
                            senderCharacter.avatarUri
                        } else {
                            message.senderAvatarUri
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
                            .padding(top = YumerinSpacing.LargeInset),
                        shape = RoundedCornerShape(YumerinShape.CardRadius),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = YumerinSpacing.CardInset, vertical = YumerinSpacing.CardVertical),
                            verticalArrangement = Arrangement.spacedBy(YumerinChatTokens.EmptyStateGap)
                        ) {
                            MiuixText("开始对话", style = MaterialTheme.typography.titleMedium)
                            MiuixText(
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
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = YumerinSpacing.Field, vertical = YumerinSpacing.Item)
            ) {
                MiuixGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { composerSurfaceHeightPx = it.height },
                    shape = RoundedCornerShape(YumerinChatTokens.ComposerShapeRadius),
                    backdrop = chatBackdrop
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                        .padding(horizontal = YumerinChatTokens.ComposerInsetHorizontal, vertical = YumerinChatTokens.ComposerInsetVertical),
                        verticalArrangement = Arrangement.spacedBy(YumerinChatTokens.ComposerItemGap)
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
                            horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Item)
                        ) {
                            MiuixTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                modifier = Modifier.weight(1f),
                                label = "输入消息",
                                useLabelAsPlaceholder = true,
                                cornerRadius = YumerinChatTokens.ComposerFieldRadius,
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

    if (showModelGate) {
        AlertDialog(
            onDismissRequest = { showModelGate = false },
            title = { MiuixText("还没有选择模型") },
            text = { MiuixText("先在模型页添加一个远端或本地模型，再开始对话。刚才的消息已保留在本地并标记为失败，不会在未配置时调用模型。") },
            confirmButton = {
                TextButton(onClick = {
                    showModelGate = false
                    onOpenModels()
                }) { MiuixText("去设置模型") }
            },
            dismissButton = { TextButton(onClick = { showModelGate = false }) { MiuixText("稍后") } }
        )
    }
    recallText?.let { text ->
        AlertDialog(
            onDismissRequest = { recallText = null },
            title = { MiuixText("本次回复使用的记忆") },
            text = { MiuixText(text) },
            confirmButton = { TextButton(onClick = { recallText = null }) { MiuixText("关闭") } }
        )
    }
    }
}


@Composable
private fun MessageBubble(
    message: ChatMessage,
    reducedMotion: Boolean,
    isMine: Boolean,
    avatarText: String,
    avatarUri: String?,
    onAvatarClick: (() -> Unit)?,
    onRecall: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    onRetryUser: (() -> Unit)? = null
) {
    val isSystem = message.senderType == MessageSenderType.SYSTEM
    val bubbleShape = RoundedCornerShape(
        when {
            isSystem -> YumerinChatTokens.SystemBubbleRadius
            else -> YumerinChatTokens.BubbleRadius
        }
    )
    val bubbleColor = when {
        isSystem -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        isMine -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val bubbleContentColor = when {
        isSystem -> MaterialTheme.colorScheme.onSurfaceVariant
        isMine -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * 0.78f
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
                Spacer(Modifier.width(YumerinSpacing.Item))
            }
            Column(
                modifier = if (isSystem) {
                    Modifier.fillMaxWidth(0.92f)
                } else {
                    Modifier.widthIn(max = maxBubbleWidth)
                },
                verticalArrangement = Arrangement.spacedBy(YumerinSpacing.Compact)
            ) {
                Surface(
                    modifier = if (isSystem) Modifier.fillMaxWidth() else Modifier,
                    shape = bubbleShape,
                    color = bubbleColor,
                    contentColor = bubbleContentColor,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when {
                            isMine -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            isSystem -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
                            else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = YumerinChatTokens.BubbleInsetHorizontal,
                            vertical = YumerinChatTokens.BubbleInsetVertical
                        ),
                        verticalArrangement = Arrangement.spacedBy(YumerinChatTokens.BubbleMetaGap)
                    ) {
                        if (!isMine && !isSystem) {
                            MiuixText(
                                message.senderName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        message.parts.filterIsInstance<com.lhy.yumerin.core.model.MessagePart.Image>().forEach { image ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.72f)
                                    .clip(RoundedCornerShape(YumerinChatTokens.BubbleRadius))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                            ) {
                                com.lhy.yumerin.designsystem.MessageImageView(
                                    uri = image.uri,
                                    contentDescription = "消息图片",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 120.dp, max = 260.dp)
                                )
                            }
                        }
                        val isGenerating = message.responseState == com.lhy.yumerin.core.model.AiResponseState.GENERATING
                        val blankText = message.text.trim().isEmpty()
                        if (isGenerating && blankText) {
                            TypingIndicator(reducedMotion = reducedMotion)
                        } else {
                            MiuixText(
                                text = message.text.trim().ifBlank {
                                    when (message.responseState) {
                                        com.lhy.yumerin.core.model.AiResponseState.STOPPED -> "已停止生成"
                                        else -> ""
                                    }
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (isGenerating && !reducedMotion) {
                                StreamingCursor()
                            }
                        }
                        message.errorMessage?.let {
                            MiuixText("状态：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (isMine && message.deliveryState != com.lhy.yumerin.core.model.MessageDeliveryState.SENT) {
                            MiuixText("消息尚未成功处理", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (message.responseState == com.lhy.yumerin.core.model.AiResponseState.RETRYABLE) {
                            MiuixText("可重试此回复", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (onRetry != null || onRetryUser != null || onRecall != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(YumerinChatTokens.BubbleActionGap)) {
                                onRetry?.let { TextButton(onClick = it) { MiuixText("重试") } }
                                onRetryUser?.let { TextButton(onClick = it) { MiuixText("重试发送") } }
                                onRecall?.let { TextButton(onClick = it) { MiuixText("查看召回记忆") } }
                            }
                        }
                    }
                }
            }
            if (isMine && !isSystem) {
                Spacer(Modifier.width(YumerinSpacing.Item))
                Avatar(
                    text = avatarText,
                    uri = avatarUri,
                    onClick = onAvatarClick,
                    contentDescription = "查看用户详情"
                )
            }
        }
    }
}


private val ACTIVE_GOAL_STATUSES = setOf(
    com.lhy.yumerin.core.model.DelegatedAgentGoalStatus.QUEUED,
    com.lhy.yumerin.core.model.DelegatedAgentGoalStatus.RUNNING,
    com.lhy.yumerin.core.model.DelegatedAgentGoalStatus.PAUSED,
    com.lhy.yumerin.core.model.DelegatedAgentGoalStatus.WAITING_FOR_USER
)

@Composable
private fun DelegatedGoalProgressCard(goal: com.lhy.yumerin.core.model.DelegatedAgentGoal) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val done = goal.steps.count { it.status == com.lhy.yumerin.core.model.DelegatedAgentGoalStepStatus.COMPLETED }
    val total = goal.steps.size
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YumerinChatTokens.BubbleRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = YumerinChatTokens.BubbleInsetHorizontal, vertical = YumerinChatTokens.BubbleInsetVertical),
            verticalArrangement = Arrangement.spacedBy(YumerinChatTokens.BubbleMetaGap)
        ) {
            MiuixText("【" + goal.title + "】 " + goal.status.name.lowercase().replace('_', ' ') + (if (total > 0) " · $done/$total 步" else ""), style = MaterialTheme.typography.labelLarge)
            goal.latestSummary?.takeIf { it.isNotBlank() }?.let {
                MiuixText(it, style = MaterialTheme.typography.bodySmall, maxLines = if (expanded) Int.MAX_VALUE else 2)
            }
            if (expanded && goal.steps.isNotEmpty()) {
                goal.steps.sortedBy { it.order }.forEach { step ->
                    val mark = when (step.status) {
                        com.lhy.yumerin.core.model.DelegatedAgentGoalStepStatus.COMPLETED -> "✓"
                        com.lhy.yumerin.core.model.DelegatedAgentGoalStepStatus.RUNNING -> "▸"
                        com.lhy.yumerin.core.model.DelegatedAgentGoalStepStatus.FAILED -> "✗"
                        else -> "·"
                    }
                    MiuixText("$mark ${step.title}", style = MaterialTheme.typography.bodySmall)
                }
            }
            goal.pendingQuestion?.takeIf { it.isNotBlank() }?.let {
                MiuixText("等待你的输入：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}


/**
 * Three-dot pulse shown while the model is thinking (no streamed text yet).
 * Honors the reduced-motion setting by falling back to a static ellipsis.
 */
@Composable
private fun TypingIndicator(reducedMotion: Boolean) {
    if (reducedMotion) {
        MiuixText("正在输入…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        return
    }
    val transition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 520, easing = androidx.compose.animation.core.EaseInOut),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 170)
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .graphicsLayer(alpha = alpha)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f), CircleShape)
            )
        }
        MiuixText(
            " 正在输入…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
    }
}

/** Blinking caret appended under streamed text so output feels alive. */
@Composable
private fun StreamingCursor() {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 480), repeatMode = RepeatMode.Reverse),
        label = "caret"
    )
    Box(
        modifier = Modifier
            .padding(top = 3.dp)
            .size(width = 2.dp, height = 15.dp)
            .graphicsLayer(alpha = alpha)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp))
    )
}
