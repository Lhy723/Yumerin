package com.lhy.yumerin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.lhy.yumerin.core.model.CharacterProfile
import com.lhy.yumerin.core.model.EntityId
import com.lhy.yumerin.designsystem.YumerinButton as MiuixButton
import com.lhy.yumerin.designsystem.YumerinShape
import com.lhy.yumerin.designsystem.YumerinSpacing
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class CharacterContinuityUiState(
    val moodValence: Float,
    val trust: Float,
    val closeness: Float
)

@Composable
internal fun CharacterContinuityListPage(
    characters: List<CharacterProfile>,
    onOpenCharacter: (EntityId) -> Unit,
    onBack: () -> Unit
) {
    MiuixScaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { _ ->
        PageColumn {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Field)
            ) {
                MiuixBackButton(onClick = onBack)
                MiuixText("角色连续性", style = MiuixTheme.textStyles.title2)
            }
            MiuixText(
                "查看和调整每个角色保留的关系与情绪语境。这里的状态只用于后续对话，不会显示为聊天分数。",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions
            )
            if (characters.isEmpty()) {
                MiuixText(
                    "还没有可设置的角色。",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            } else {
                characters.sortedBy { it.name }.forEach { character ->
                    MiuixCard(
                        onClick = { onOpenCharacter(character.id) },
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = YumerinShape.CardRadius,
                        insideMargin = PaddingValues(YumerinSpacing.CardInset)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(YumerinSpacing.Compact)) {
                            MiuixText(character.name, style = MiuixTheme.textStyles.headline1)
                            MiuixText(
                                "查看和调整近期心情、信任感与亲近感",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CharacterContinuityPage(
    character: CharacterProfile,
    state: CharacterContinuityUiState?,
    onSave: (moodValence: Float, trust: Float, closeness: Float) -> Unit,
    onBack: () -> Unit
) {
    var moodPosition by remember(character.id, state) {
        mutableFloatStateOf(((state?.moodValence ?: 0f) + 1f) / 2f)
    }
    var trust by remember(character.id, state) { mutableFloatStateOf(state?.trust ?: 0.5f) }
    var closeness by remember(character.id, state) { mutableFloatStateOf(state?.closeness ?: 0.5f) }

    MiuixScaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { _ ->
        PageColumn {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(YumerinSpacing.Field)
            ) {
                MiuixBackButton(onClick = onBack)
                MiuixText(character.name, style = MiuixTheme.textStyles.title2)
            }
            MiuixText(
                "调整后续对话中使用的关系与情绪语境。",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions
            )
            if (state == null) {
                MiuixText(
                    "这位角色还没有累积连续性记录，将从平和、适中的关系状态开始。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
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
            MiuixButton(
                onClick = { onSave(moodPosition * 2f - 1f, trust, closeness) },
                modifier = Modifier.fillMaxWidth()
            ) {
                MiuixText("保存连续性", style = MiuixTheme.textStyles.button)
            }
            Spacer(Modifier.height(YumerinSpacing.PageSection))
        }
    }
}

@Composable
private fun ContinuityCard(title: String, content: @Composable () -> Unit) {
    MiuixCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = YumerinShape.CardRadius,
        insideMargin = PaddingValues(YumerinSpacing.CardInset)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(YumerinSpacing.Item)
        ) {
            MiuixText(title, style = MiuixTheme.textStyles.title4)
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
        MiuixText(title, style = MiuixTheme.textStyles.headline2)
        MiuixText(
            description,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions
        )
        MiuixSlider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )
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
