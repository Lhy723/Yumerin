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
internal fun SchedulesPage(
    backdrop: Backdrop,
    graph: AppGraph,
    characters: List<CharacterProfile>,
    chats: List<ChatThread>,
    onBack: () -> Unit
) {
    val schedules by graph.schedules.observeSchedules().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf("9") }
    var minute by remember { mutableStateOf("0") }
    var characterId by remember { mutableStateOf(characters.firstOrNull()?.id) }
    var chatId by remember { mutableStateOf(chats.firstOrNull { it.kind == ChatKind.DIRECT }?.id) }

    PageColumn {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassBackButton(backdrop = backdrop, onClick = onBack)
            Text("主动消息计划", style = MaterialTheme.typography.headlineLarge)
        }
        Text("只有全局主动消息、角色主动消息和通知权限都开启时，计划才会生成普通聊天消息。静默时段只抑制通知，不删除消息。")
        GlassSurface(backdrop, Modifier.fillMaxWidth()) {
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
                val directChats = chats.filter { it.kind == ChatKind.DIRECT }
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
        schedules.forEach { schedule ->
            GlassSurface(backdrop, Modifier.fillMaxWidth()) {
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
