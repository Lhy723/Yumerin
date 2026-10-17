package com.lhy.yumerin.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lhy.yumerin.core.model.ComponentMaterial
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun YumerinButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val primaryColor = MiuixTheme.colorScheme.primary
    val buttonColor = when (LocalComponentMaterial.current) {
        ComponentMaterial.STANDARD -> primaryColor
        ComponentMaterial.GLASS -> primaryColor.copy(alpha = YumerinGlassTokens.PrimaryActionAlpha)
    }
    MiuixButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        cornerRadius = YumerinShape.ControlRadius,
        minWidth = 0.dp,
        minHeight = YumerinSize.TouchTarget,
        insideMargin = PaddingValues(horizontal = YumerinSpacing.CardVertical, vertical = YumerinSpacing.Item),
        colors = MiuixButtonDefaults.buttonColorsPrimary(color = buttonColor),
        content = content
    )
}

@Composable
fun YumerinTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val textColor = MiuixTheme.colorScheme.primary
    MiuixButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        cornerRadius = YumerinShape.ControlRadius,
        minWidth = 0.dp,
        minHeight = YumerinSize.TouchTarget,
        insideMargin = PaddingValues(horizontal = YumerinSpacing.Field, vertical = YumerinSpacing.Item),
        colors = MiuixButtonDefaults.buttonColors(
            color = Color.Transparent,
            disabledColor = Color.Transparent,
            contentColor = textColor,
            disabledContentColor = MiuixTheme.colorScheme.disabledOnSurface
        ),
        content = content
    )
}

@Composable
fun YumerinOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val outline = MiuixTheme.colorScheme.outline
    YumerinTextButton(
        onClick = onClick,
        modifier = modifier.border(
            BorderStroke(1.dp, if (enabled) outline else outline.copy(alpha = 0.45f)),
            YumerinShape.Control
        ),
        enabled = enabled,
        content = content
    )
}

/** Shared Miuix text field adapter for the small set of form options used by Yumerin. */
@Composable
fun YumerinOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(YumerinSpacing.Compact)
    ) {
        if (label != null && placeholder != null) {
            MiuixText(label, style = MaterialTheme.typography.labelMedium)
        }
        MiuixTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = placeholder ?: label.orEmpty(),
            useLabelAsPlaceholder = placeholder != null,
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation
        )
        supportingText?.let {
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodySmall) {
                it()
            }
        }
    }
}

@Composable
fun YumerinAlertDialog(
    onDismissRequest: () -> Unit,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null
) {
    WindowDialog(
        show = true,
        onDismissRequest = onDismissRequest,
        insideMargin = androidx.compose.ui.unit.DpSize(YumerinSpacing.PageSection, YumerinSpacing.LargeInset)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(YumerinSpacing.Field)
        ) {
            title?.invoke()
            text?.invoke()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                dismissButton?.invoke()
                Spacer(Modifier.weight(1f))
                confirmButton()
            }
        }
    }
}
