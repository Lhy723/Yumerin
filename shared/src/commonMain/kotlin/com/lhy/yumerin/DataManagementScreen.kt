package com.lhy.yumerin

import com.lhy.yumerin.designsystem.YumerinSpacing

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import com.lhy.yumerin.core.model.*
import com.lhy.yumerin.data.memory.*
import com.lhy.yumerin.designsystem.*
import com.lhy.yumerin.designsystem.YumerinAlertDialog as AlertDialog
import com.lhy.yumerin.designsystem.YumerinButton as Button
import com.lhy.yumerin.designsystem.YumerinTextButton as TextButton
import com.lhy.yumerin.domain.*
import com.lhy.yumerin.domain.memory.*
import com.lhy.yumerin.domain.repository.*
import com.lhy.yumerin.domain.usecase.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@Composable
internal fun DataManagementPage(
    graph: AppGraph,
    onExportData: (String) -> Unit,
    onImportDataRequest: ((onPayload: (String) -> Unit) -> Unit),
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var pendingImportPayload by remember { mutableStateOf<String?>(null) }
    var pendingImportPreview by remember { mutableStateOf<ImportPreview?>(null) }
    var showWipeConfirmation by remember { mutableStateOf(false) }
    PageColumn {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Field)
        ) {
            MiuixBackButton(onClick = onBack)
            Text("数据管理", style = MaterialTheme.typography.headlineLarge)
        }
        MiuixCardSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(YumerinSpacing.CardInset), verticalArrangement = Arrangement.spacedBy(YumerinSpacing.FormField)) {
                Text("可携带数据包", style = MaterialTheme.typography.titleLarge)
                Text("导出角色、聊天、群组、记忆、模型元数据和主动计划；API Key 与大型模型文件永不进入导出包。导入采用合并方式，不默认删除已有数据。")
                Button(onClick = { scope.launch { onExportData(graph.portableData.exportData()) } }, modifier = Modifier.fillMaxWidth()) { Text("导出数据") }
                Button(onClick = {
                    onImportDataRequest { payload ->
                        pendingImportPayload = payload
                        pendingImportPreview = null
                        scope.launch {
                            pendingImportPreview = graph.portableData.previewImport(payload)
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("导入数据") }
                pendingImportPayload?.let { payload ->
                    val preview = pendingImportPreview
                    if (preview == null) {
                        Text("正在检查数据包…")
                    } else if (!preview.valid) {
                        Text(preview.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = {
                            pendingImportPayload = null
                            pendingImportPreview = null
                        }) { Text("关闭") }
                    } else {
                        Text("已读取数据包（${payload.length} 字符），确认后才会合并到本机。")
                        Text("包含 ${preview.counts.total} 条记录：角色 ${preview.counts.characters}、聊天 ${preview.counts.chats}、消息 ${preview.counts.messages}、记忆 ${preview.counts.memories}。")
                        Text(
                            if (preview.conflictCount == 0) "没有同 ID 冲突。"
                            else "发现 ${preview.conflictCount} 个同 ID 冲突，请明确选择冲突处理方式。",
                            color = if (preview.conflictCount == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Item)) {
                            Button(
                                onClick = {
                                    val importPayload = pendingImportPayload ?: return@Button
                                    pendingImportPayload = null
                                    pendingImportPreview = null
                                    scope.launch {
                                        message = graph.portableData.importData(importPayload, ImportConflictPolicy.KEEP_LOCAL).message
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("保留本机") }
                            Button(
                                onClick = {
                                    val importPayload = pendingImportPayload ?: return@Button
                                    pendingImportPayload = null
                                    pendingImportPreview = null
                                    scope.launch {
                                        message = graph.portableData.importData(importPayload, ImportConflictPolicy.USE_IMPORTED).message
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("使用导入") }
                        }
                        TextButton(onClick = {
                            pendingImportPayload = null
                            pendingImportPreview = null
                        }) { Text("取消导入") }
                    }
                }
            }
        }
        MiuixCardSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(YumerinSpacing.CardInset), verticalArrangement = Arrangement.spacedBy(YumerinSpacing.FormField)) {
                Text("清除全部本地数据", style = MaterialTheme.typography.titleLarge)
                Text("会停止后台陪伴，删除角色、聊天、群组、记忆、头像、模型配置和 API Key。此操作不可恢复，建议先导出。", color = MaterialTheme.colorScheme.error)
                Button(onClick = { showWipeConfirmation = true }, modifier = Modifier.fillMaxWidth()) { Text("清除全部数据") }
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
    if (showWipeConfirmation) {
        AlertDialog(
            onDismissRequest = { showWipeConfirmation = false },
            title = { Text("确认清除全部数据？") },
            text = { Text("角色、聊天、群组、记忆、模型配置、API Key 和本地模型都会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    showWipeConfirmation = false
                    scope.launch { graph.portableData.wipeAll(); message = "本地数据已清除。" }
                }) { Text("确认清除") }
            },
            dismissButton = { TextButton(onClick = { showWipeConfirmation = false }) { Text("取消") } }
        )
    }
}
