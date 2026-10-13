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
import kotlin.time.Clock

@Composable
internal fun SchedulesPage(
    graph: AppGraph,
    characters: List<CharacterProfile>,
    chats: List<ChatThread>,
    onBack: () -> Unit
) {
    val schedules by graph.schedules.observeSchedules().collectAsState(emptyList())
    val agentGoals by graph.agentGoals.observeGoals().collectAsState(emptyList())
    val delegatedGoals by graph.delegatedAgentGoals.observeGoals().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf("9") }
    var minute by remember { mutableStateOf("0") }
    var characterId by remember { mutableStateOf(characters.firstOrNull()?.id) }
    var chatId by remember {
        mutableStateOf(chats.firstOrNull { it.kind == ChatKind.DIRECT && it.characterId == characters.firstOrNull()?.id }?.id)
    }
    var goalTitle by remember { mutableStateOf("") }
    var goalPrompt by remember { mutableStateOf("") }
    var goalDelayMinutes by remember { mutableStateOf("60") }
    var goalFeedback by remember { mutableStateOf<String?>(null) }
    var delegatedTitle by remember { mutableStateOf("") }
    var delegatedOutcome by remember { mutableStateOf("") }
    var delegatedConstraints by remember { mutableStateOf("") }
    var delegatedFeedback by remember { mutableStateOf<String?>(null) }
    var delegatedAnswers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    PageColumn {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MiuixBackButton(onClick = onBack)
            Text("计划与目标", style = MaterialTheme.typography.headlineLarge)
        }
        Text("周期计划需要开启全局和角色主动开关；一次性互动需要开启全局开关。明确委托的目标独立运行。静默时段或关闭通知权限只会隐藏提醒，不删除聊天消息。")
        MiuixCardSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("新建计划", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("计划名称") }, singleLine = true)
                OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth(), label = { Text("互动提示") })
                OutlinedTextField(hour, { hour = it }, Modifier.fillMaxWidth(), label = { Text("小时（0-23）") }, singleLine = true)
                OutlinedTextField(minute, { minute = it }, Modifier.fillMaxWidth(), label = { Text("分钟（0-59）") }, singleLine = true)
                Text("角色：${characters.firstOrNull { it.id == characterId }?.name ?: "未选择"}")
                characters.forEach { character ->
                    val characterChat = chats.firstOrNull { it.kind == ChatKind.DIRECT && it.characterId == character.id }
                    Row(Modifier.fillMaxWidth().clickable {
                        characterId = character.id
                        chatId = characterChat?.id
                    }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(character.id == characterId, {
                            characterId = character.id
                            chatId = characterChat?.id
                        })
                        Text(character.name)
                    }
                }
                val directChats = chats.filter { it.kind == ChatKind.DIRECT && it.characterId == characterId }
                Text("聊天：${directChats.firstOrNull { it.id == chatId }?.title ?: "未选择"}")
                directChats.forEach { chat ->
                    Row(Modifier.fillMaxWidth().clickable { chatId = chat.id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(chat.id == chatId, { chatId = chat.id })
                        Text(chat.title)
                    }
                }
                Button(
                    onClick = {
                        val now = nowMillis()
                        val selectedCharacter = characters.firstOrNull { it.id == characterId }
                        val selectedChat = chats.firstOrNull { it.id == chatId }
                        if (selectedCharacter != null && selectedChat != null) {
                            scope.launch {
                                graph.characters.saveCharacter(selectedCharacter.copy(proactiveEnabled = true, updatedAt = now))
                                graph.schedules.saveSchedule(
                                    com.lhy.yumerin.core.model.CompanionSchedule(
                                        id = newEntityId("schedule"), characterId = selectedCharacter.id, chatId = selectedChat.id,
                                        title = title.ifBlank { "陪伴提醒" }, prompt = prompt.ifBlank { "自然地问候用户，关心今天的状态。" },
                                        hour = hour.toIntOrNull()?.coerceIn(0, 23) ?: 9,
                                        minute = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0,
                                        enabled = true, lastTriggeredAt = null, dailyTriggerCount = 0, dailyCountStamp = null,
                                        createdAt = now, updatedAt = now
                                    )
                                )
                                title = ""; prompt = ""
                            }
                        }
                    },
                    enabled = characterId != null && chatId != null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("保存计划") }
            }
        }
        MiuixCardSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("一次性目标", style = MaterialTheme.typography.titleLarge)
                Text("安排角色在指定延迟后主动完成一次轻量互动。目标只生成普通聊天消息，不会执行外部操作。")
                OutlinedTextField(goalTitle, { goalTitle = it }, Modifier.fillMaxWidth(), label = { Text("目标名称") }, singleLine = true)
                OutlinedTextField(goalPrompt, { goalPrompt = it }, Modifier.fillMaxWidth(), label = { Text("到期时的互动内容") })
                OutlinedTextField(
                    goalDelayMinutes,
                    { goalDelayMinutes = it.filter(Char::isDigit).take(6) },
                    Modifier.fillMaxWidth(),
                    label = { Text("多少分钟后执行（1 到 525600）") },
                    singleLine = true
                )
                goalFeedback?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                Button(
                    onClick = {
                        val selectedCharacterId = characterId
                        val selectedChatId = chatId
                        val delay = goalDelayMinutes.toIntOrNull()
                        if (selectedCharacterId != null && selectedChatId != null && delay != null) {
                            scope.launch {
                                when (val result = graph.useCases.scheduleAgentGoal(
                                    characterId = selectedCharacterId,
                                    chatId = selectedChatId,
                                    title = goalTitle,
                                    prompt = goalPrompt,
                                    delayMinutes = delay
                                )) {
                                    is ScheduleAgentGoalResult.Scheduled -> {
                                        goalTitle = ""
                                        goalPrompt = ""
                                        goalFeedback = "已安排：${result.goal.title}"
                                    }
                                    ScheduleAgentGoalResult.ProactiveMessagesDisabled -> goalFeedback = "请先在设置中开启主动消息。"
                                    ScheduleAgentGoalResult.InvalidGoal -> goalFeedback = "请填写有效的目标名称和互动内容。"
                                    ScheduleAgentGoalResult.InvalidDeadline -> goalFeedback = "执行延迟需在 1 到 525600 分钟之间。"
                                    ScheduleAgentGoalResult.MissingCharacter -> goalFeedback = "所选角色不可用。"
                                    ScheduleAgentGoalResult.InvalidChat -> goalFeedback = "目标必须关联所选角色的单人聊天。"
                                    ScheduleAgentGoalResult.UnsafePrompt -> goalFeedback = "这条目标内容无法安排。"
                                    ScheduleAgentGoalResult.SchedulerUnavailable -> goalFeedback = "系统暂时无法安排该目标，请稍后重试。"
                                }
                            }
                        }
                    },
                    enabled = characterId != null && chatId != null && goalTitle.isNotBlank() &&
                        goalPrompt.isNotBlank() && (goalDelayMinutes.toIntOrNull() ?: 0) in 1..525_600,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("安排一次性目标") }
            }
        }
        agentGoals.filter { it.status != AgentGoalStatus.CANCELLED }.forEach { goal ->
            MiuixCardSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(goal.title, style = MaterialTheme.typography.titleMedium)
                    val remainingMinutes = ((goal.dueAt - nowMillis()).coerceAtLeast(0L) / 60_000L)
                    Text(
                        when (goal.status) {
                            AgentGoalStatus.PENDING -> "约 $remainingMinutes 分钟后 · ${goal.prompt}"
                            AgentGoalStatus.COMPLETED -> "已完成 · ${goal.prompt}"
                            AgentGoalStatus.FAILED -> "未完成：${goal.failureReason ?: "执行失败"}"
                            AgentGoalStatus.CANCELLED -> "已取消"
                        }
                    )
                    if (goal.status == AgentGoalStatus.PENDING) {
                        TextButton(onClick = { scope.launch { graph.useCases.cancelAgentGoal(goal.id) } }) { Text("取消目标") }
                    }
                }
            }
        }
        MiuixCardSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("委托目标", style = MaterialTheme.typography.titleLarge)
                Text("描述你希望达成的结果。Agent 会把它拆成最多 6 步，后台逐步处理；需要你补充信息时会暂停等待。明确委托的目标不受全局主动消息开关限制，后台执行尽力而为；当前可用能力以应用内已接入的模块为限。")
                OutlinedTextField(delegatedTitle, { delegatedTitle = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("目标名称") }, singleLine = true)
                OutlinedTextField(delegatedOutcome, { delegatedOutcome = it.take(1_000) }, Modifier.fillMaxWidth(), label = { Text("希望达成的结果") })
                OutlinedTextField(delegatedConstraints, { delegatedConstraints = it.take(1_500) }, Modifier.fillMaxWidth(), label = { Text("约束或偏好（可选）") })
                delegatedFeedback?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                Button(
                    onClick = {
                        val selectedCharacterId = characterId
                        val selectedChatId = chatId
                        if (selectedCharacterId != null && selectedChatId != null) {
                            scope.launch {
                                when (val result = graph.useCases.createDelegatedAgentGoal(
                                    characterId = selectedCharacterId,
                                    chatId = selectedChatId,
                                    title = delegatedTitle,
                                    outcome = delegatedOutcome,
                                    constraints = delegatedConstraints
                                )) {
                                    is CreateDelegatedAgentGoalResult.Created -> {
                                        delegatedTitle = ""
                                        delegatedOutcome = ""
                                        delegatedConstraints = ""
                                        delegatedFeedback = "已开始处理：${result.goal.title}"
                                    }
                                    CreateDelegatedAgentGoalResult.InvalidGoal -> delegatedFeedback = "请填写目标名称和希望达成的结果。"
                                    CreateDelegatedAgentGoalResult.MissingCharacter -> delegatedFeedback = "所选角色不可用。"
                                    CreateDelegatedAgentGoalResult.InvalidChat -> delegatedFeedback = "目标必须关联所选角色的单人聊天。"
                                    CreateDelegatedAgentGoalResult.UnsafeGoal -> delegatedFeedback = "这条目标暂时无法安排。"
                                    CreateDelegatedAgentGoalResult.SchedulerUnavailable -> delegatedFeedback = "系统暂时无法安排后台任务，请稍后重试。"
                                }
                            }
                        }
                    },
                    enabled = characterId != null && chatId != null && delegatedTitle.isNotBlank() && delegatedOutcome.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("委托目标") }
            }
        }
        delegatedGoals.filter { it.status != DelegatedAgentGoalStatus.CANCELLED }.forEach { goal ->
            MiuixCardSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(goal.title, style = MaterialTheme.typography.titleMedium)
                    Text("${delegatedGoalStatusLabel(goal.status)} · ${goal.outcome}")
                    goal.latestSummary?.takeIf(String::isNotBlank)?.let { Text(it) }
                    goal.steps.forEach { step ->
                        Text("${step.order + 1}. ${step.title} · ${delegatedGoalStepStatusLabel(step.status)}")
                        step.summary?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    goal.pendingQuestion?.let { question ->
                        Text(question, color = MaterialTheme.colorScheme.primary)
                        val answer = delegatedAnswers[goal.id].orEmpty()
                        OutlinedTextField(
                            value = answer,
                            onValueChange = { value -> delegatedAnswers = delegatedAnswers + (goal.id to value.take(1_000)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("补充信息") }
                        )
                    }
                    goal.failureReason?.let { Text("未完成：$it") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (goal.status) {
                            DelegatedAgentGoalStatus.QUEUED,
                            DelegatedAgentGoalStatus.RUNNING -> TextButton(onClick = { scope.launch { graph.useCases.pauseDelegatedAgentGoal(goal.id) } }) { Text("暂停") }
                            DelegatedAgentGoalStatus.PAUSED,
                            DelegatedAgentGoalStatus.WAITING_FOR_USER -> {
                                val answer = delegatedAnswers[goal.id]
                                val needsAnswer = goal.pendingQuestion != null
                                TextButton(
                                    enabled = !needsAnswer || !answer.isNullOrBlank(),
                                    onClick = {
                                        scope.launch {
                                            if (graph.useCases.resumeDelegatedAgentGoal(goal.id, answer)) {
                                                delegatedAnswers = delegatedAnswers - goal.id
                                            }
                                        }
                                    }
                                ) { Text(if (needsAnswer) "提交并继续" else "继续") }
                            }
                            else -> Unit
                        }
                        if (goal.status !in setOf(DelegatedAgentGoalStatus.COMPLETED, DelegatedAgentGoalStatus.CANCELLED, DelegatedAgentGoalStatus.FAILED)) {
                            TextButton(onClick = { scope.launch { graph.useCases.cancelDelegatedAgentGoal(goal.id) } }) { Text("取消目标") }
                        }
                    }
                }
            }
        }
        schedules.forEach { schedule ->
            MiuixCardSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(schedule.title, style = MaterialTheme.typography.titleMedium)
                    Text("每天 ${schedule.hour.pad2()}:${schedule.minute.pad2()} · ${schedule.prompt}")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = schedule.enabled, onCheckedChange = { enabled ->
                            scope.launch { graph.schedules.saveSchedule(schedule.copy(enabled = enabled, updatedAt = nowMillis())) }
                        })
                        Text(if (schedule.enabled) "已启用" else "已暂停")
                        TextButton(onClick = { scope.launch { graph.schedules.deleteSchedule(schedule.id) } }) { Text("删除") }
                    }
                }
            }
        }
    }
}

private fun delegatedGoalStatusLabel(status: DelegatedAgentGoalStatus): String = when (status) {
    DelegatedAgentGoalStatus.QUEUED -> "排队中"
    DelegatedAgentGoalStatus.RUNNING -> "执行中"
    DelegatedAgentGoalStatus.PAUSED -> "已暂停"
    DelegatedAgentGoalStatus.WAITING_FOR_USER -> "等待你补充信息"
    DelegatedAgentGoalStatus.COMPLETED -> "已完成"
    DelegatedAgentGoalStatus.CANCELLED -> "已取消"
    DelegatedAgentGoalStatus.FAILED -> "未完成"
}

private fun delegatedGoalStepStatusLabel(status: DelegatedAgentGoalStepStatus): String = when (status) {
    DelegatedAgentGoalStepStatus.PENDING -> "待处理"
    DelegatedAgentGoalStepStatus.RUNNING -> "处理中"
    DelegatedAgentGoalStepStatus.COMPLETED -> "已完成"
    DelegatedAgentGoalStepStatus.FAILED -> "失败"
}
