package com.lhy.yumerin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lhy.yumerin.designsystem.YumerinSpacing
import com.lhy.yumerin.domain.usecase.ImportCharacterCardUseCase
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton

/**
 * Import wizard for community character cards: pick a PNG/JSON file, review
 * the compatibility report (mapped fields, memory seed count, unmapped
 * fields), and confirm. The use case enforces safety before anything persists.
 */
@Composable
fun CardImportPage(
    importCard: ImportCharacterCardUseCase,
    onPickFile: ((onSelected: (ByteArray?) -> Unit) -> Unit),
    onImported: (characterId: String, chatId: String) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<ImportCharacterCardUseCase.Result.Imported?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = YumerinSpacing.PageHorizontal, vertical = YumerinSpacing.Item),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiuixBackButton(onClick = onBack)
        }
        SmallTitle(text = "导入角色卡")
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = YumerinSpacing.PageHorizontal)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MiuixText(
                    "支持社区前端的 PNG 角色卡（chara/ccv3）和 JSON 角色卡。导入会创建角色、开场白和世界书记忆种子；"
                        + "未知字段会被保留并列入兼容性报告，不会被执行。",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
                MiuixTextButton(
                    text = if (busy) "解析中…" else "选择文件并导入",
                    enabled = !busy,
                    onClick = {
                        busy = true
                        error = null
                        onPickFile { bytes ->
                            if (bytes == null) {
                                busy = false
                                return@onPickFile
                            }
                            scope.launch {
                                when (val result = importCard(bytes)) {
                                    is ImportCharacterCardUseCase.Result.Imported -> {
                                        report = result
                                        busy = false
                                    }
                                    is ImportCharacterCardUseCase.Result.Failed -> {
                                        error = result.reason
                                        busy = false
                                    }
                                }
                            }
                        }
                    }
                )
                error?.let {
                    MiuixText(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            }
        }
        report?.let { imported ->
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = YumerinSpacing.PageHorizontal)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiuixText("导入成功：${imported.personaName}", fontWeight = FontWeight.Medium)
                    MiuixText("格式：${imported.spec} · 记忆种子 ${imported.memoryCount} 条")
                    if (imported.unmappedFields.isNotEmpty()) {
                        MiuixText(
                            "未映射字段（已保留，不影响使用）：",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                        imported.unmappedFields.take(8).forEach { field ->
                            MiuixText("· $field", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        }
                        if (imported.unmappedFields.size > 8) {
                            MiuixText(
                                "…共 ${imported.unmappedFields.size} 项",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    MiuixTextButton(
                        text = "进入聊天",
                        onClick = { onImported(imported.characterId, imported.chatId) }
                    )
                }
            }
        }
    }
}
