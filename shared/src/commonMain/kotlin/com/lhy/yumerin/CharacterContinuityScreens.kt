package com.lhy.yumerin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lhy.yumerin.core.model.CharacterProfile
import com.lhy.yumerin.core.model.CharacterRuntimeState
import com.lhy.yumerin.core.model.EntityId

@Composable
internal fun CharacterContinuityListPage(
    characters: List<CharacterProfile>,
    onOpenCharacter: (EntityId) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("‹ 返回")
            }
            Text("角色连续性", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            "查看和调整每个角色保留的关系与情绪语境。这里的状态只用于后续对话，不会显示为聊天分数。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (characters.isEmpty()) {
            Text("还没有可设置的角色。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            characters.sortedBy { it.name }.forEach { character ->
                Card(
                    onClick = { onOpenCharacter(character.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(character.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "查看和调整近期心情、信任感与亲近感",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CharacterContinuityPage(
    character: CharacterProfile,
    state: CharacterRuntimeState?,
    onSave: (moodValence: Float, trust: Float, closeness: Float) -> Unit,
    onBack: () -> Unit
) {
    var moodPosition by remember(character.id, state) {
        mutableFloatStateOf(((state?.moodValence ?: 0f) + 1f) / 2f)
    }
    var trust by remember(character.id, state) { mutableFloatStateOf(state?.trust ?: 0.5f) }
    var closeness by remember(character.id, state) { mutableFloatStateOf(state?.closeness ?: 0.5f) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("‹ 返回")
            }
            Text(character.name, style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            "调整后续对话中使用的关系与情绪语境。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state == null) {
            Text(
                "这位角色还没有累积连续性记录，将从平和、适中的关系状态开始。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ContinuityCard("情绪语境") {
            ContinuitySlider(
                title = "近期心情",
                value = moodPosition,
                description = "${moodPosition.moodDescription()} · 低落到愉快",
                onValueChange = { moodPosition = it }
            )
        }
        ContinuityCard("关系语境") {
            ContinuitySlider(
                title = "信任感",
                value = trust,
                description = "${trust.trustDescription()} · 谨慎到信任",
                onValueChange = { trust = it }
            )
            ContinuitySlider(
                title = "亲近感",
                value = closeness,
                description = "${closeness.closenessDescription()} · 保持距离到亲近",
                onValueChange = { closeness = it }
            )
        }
        Button(
            onClick = { onSave(moodPosition * 2f - 1f, trust, closeness) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存连续性")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ContinuityCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ContinuitySlider(
    title: String,
    value: Float,
    description: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = value.coerceIn(0f, 1f), onValueChange = onValueChange, valueRange = 0f..1f)
    }
}

private fun Float.moodDescription(): String = when {
    this < 0.3f -> "偏低落"
    this > 0.7f -> "偏愉快"
    else -> "比较平和"
}

private fun Float.trustDescription(): String = when {
    this < 0.33f -> "较谨慎"
    this > 0.67f -> "信任较深"
    else -> "逐步建立"
}

private fun Float.closenessDescription(): String = when {
    this < 0.33f -> "更独立"
    this > 0.67f -> "更亲近"
    else -> "适度亲近"
}
