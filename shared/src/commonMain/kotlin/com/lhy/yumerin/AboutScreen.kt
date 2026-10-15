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
internal fun AboutPage(
    appIcon: ImageBitmap?,
    onBack: () -> Unit
) {
    PageColumn {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MiuixBackButton(onClick = onBack)
            Text("关于", style = MaterialTheme.typography.headlineLarge)
        }
        MiuixCardSurface(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                appIcon?.let {
                    Image(
                        bitmap = it,
                        contentDescription = "Yumerin 应用图标",
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(24.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Text("Yumerin", style = MaterialTheme.typography.titleLarge)
                Text(
                    "AI 情感陪伴",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                )
            }
        }
        MiuixCardSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                SettingRow("应用", "Yumerin")
                SettingRow("版本", "1.0.0")
                SettingRow("组件库", "Miuix")
                SettingRow("开发框架", "Kotlin Multiplatform + Compose")
                SettingRow("许可证总览", "Apache License 2.0")
            }
        }
        MiuixCardSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("开源许可证", style = MaterialTheme.typography.titleLarge)
                Text("本应用不把用户 API Key 或本地模型文件放进导出包；以下是运行时主要开源组件。")
                listOf(
                    "Miuix" to "Apache License 2.0",
                    "Kotlin / Compose Multiplatform" to "Apache License 2.0",
                    "LiteRT-LM" to "Apache License 2.0",
                    "Room / WorkManager" to "Apache License 2.0",
                    "Koin" to "Apache License 2.0"
                ).forEach { (name, license) -> SettingRow(name, license) }
            }
        }
    }
}
