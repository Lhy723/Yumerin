package com.lhy.yumerin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lhy.yumerin.designsystem.YumerinSpacing
import com.lhy.yumerin.domain.agent.AgentRunDiagnostic
import com.lhy.yumerin.domain.persona.PromptAssembler
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton

/**
 * Debug panel for recent Agent runs: prompt slot composition with estimated
 * tokens, world-info hits, capability calls, and the memory recall trace.
 * Process-local only; nothing here leaves the device.
 */
@Composable
fun AgentDebugPage(
    diagnostics: com.lhy.yumerin.domain.agent.AgentRunDiagnostics,
    onBack: () -> Unit
) {
    val runs by diagnostics.observeRuns.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        SmallTitle(text = "Agent 调试")
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = YumerinSpacing.PageHorizontal),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MiuixText("最近 ${runs.size} 次运行")
            MiuixTextButton(
                text = "清空",
                onClick = { diagnostics.clear(); onBack() }
            )
        }
        if (runs.isEmpty()) {
            MiuixText(
                "还没有可展示的运行记录。发送一条消息后再来看提示词组成、世界书命中与能力调用。",
                modifier = Modifier.padding(YumerinSpacing.PageHorizontal)
            )
            return
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(runs, key = { it.runId }) { run ->
                RunDiagnosticCard(run)
            }
        }
    }
}

@Composable
private fun RunDiagnosticCard(run: AgentRunDiagnostic) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = YumerinSpacing.PageHorizontal)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MiuixText(
                "运行 ${run.runId.takeLast(8)} · ${if (run.statusMessage == null) "完成" else "失败（${run.statusMessage}）"}",
                fontWeight = FontWeight.Medium
            )
            if (run.slotBudgets.isNotEmpty()) {
                MiuixText("提示词槽位", fontWeight = FontWeight.Medium)
                run.slotBudgets.forEach { slot ->
                    MiuixText("· ${slotLabel(slot.slot)}：${slot.characters} 字符 ≈ ${slot.estimatedTokens} tokens")
                }
            }
            if (run.worldInfoHits.isNotEmpty()) {
                MiuixText("世界书命中（${run.worldInfoHits.size}）", fontWeight = FontWeight.Medium)
                run.worldInfoHits.take(4).forEach { hit ->
                    MiuixText("· ${hit.take(60)}")
                }
            }
            if (run.capabilityCalls.isNotEmpty()) {
                MiuixText("能力调用", fontWeight = FontWeight.Medium)
                run.capabilityCalls.forEach { call ->
                    MiuixText("· ${call.name}：${if (call.successful) "成功" else "失败"}（观察 ${call.observationCharacters} 字符）")
                }
            }
            MiuixText("记忆追溯：${if (run.usedMemoryIds.isEmpty()) "本次未用记忆" else "${run.usedMemoryIds.size} 条"}")
        }
    }
}

private fun slotLabel(slot: PromptAssembler.Slot): String = when (slot) {
    PromptAssembler.Slot.SYSTEM_RULES -> "系统规则"
    PromptAssembler.Slot.PERSONA -> "人格"
    PromptAssembler.Slot.WORLD_INFO -> "世界书"
    PromptAssembler.Slot.MEMORY -> "记忆"
    PromptAssembler.Slot.STATE -> "关系/情绪状态"
    PromptAssembler.Slot.TURN_CONTEXT -> "回合上下文"
    PromptAssembler.Slot.DISCLOSURE -> "披露策略"
    PromptAssembler.Slot.HISTORY -> "对话历史"
    PromptAssembler.Slot.POST_HISTORY -> "历史后指令"
}
