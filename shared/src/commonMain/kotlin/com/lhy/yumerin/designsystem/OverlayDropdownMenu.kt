package com.lhy.yumerin.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl as MiuixDropdownItemRow
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Surface as Surface
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Miuix preference row and anchored popup, adapted from Nexio's
 * `com.haooz.chedule.ui.basic.OverlayDropdownMenu` to the published Miuix API.
 *
 * The popup uses Miuix's `OverlayListPopup` with window dimming disabled so the
 * menu stays attached to its setting row without covering the rest of the page.
 */
@Composable
fun OverlayDropdownMenu(
    entry: DropdownEntry,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    startAction: @Composable (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    renderInRootScaffold: Boolean = true,
    collapseOnSelection: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
) {
    val entries = remember(entry) { listOf(entry) }
    val nonEmptyEntries = entries.filter { it.items.isNotEmpty() }
    val hasEntries = nonEmptyEntries.isNotEmpty()
    val actualEnabled = enabled && hasEntries

    val interactionSource = remember { MutableInteractionSource() }
    val isDropdownExpanded = remember { mutableStateOf(false) }
    val isHoldDown = remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val currentHapticFeedback by rememberUpdatedState(hapticFeedback)
    val currentOnExpandedChange = rememberUpdatedState(onExpandedChange)
    val currentEntries by rememberUpdatedState(nonEmptyEntries)
    val currentCollapseOnSelection by rememberUpdatedState(collapseOnSelection)
    val popupPositionProvider = rememberNexioDropdownPositionProvider()

    val setExpanded: (Boolean) -> Unit = remember {
        { expanded ->
            if (isDropdownExpanded.value != expanded) {
                isDropdownExpanded.value = expanded
                currentOnExpandedChange.value?.invoke(expanded)
            }
        }
    }
    val actionColor = if (actualEnabled) {
        MiuixTheme.colorScheme.onSurfaceVariantActions
    } else {
        MiuixTheme.colorScheme.disabledOnSecondaryVariant
    }
    val selectedText = nonEmptyEntries
        .asSequence()
        .flatMap { it.items.asSequence() }
        .firstOrNull { it.selected }
        ?.text

    val handleClick = remember(actualEnabled) {
        {
            if (actualEnabled) {
                setExpanded(!isDropdownExpanded.value)
                if (isDropdownExpanded.value) {
                    isHoldDown.value = true
                    currentHapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
            }
        }
    }
    val onItemClick: (Int, Int) -> Unit = remember {
        { entryIndex, itemIndex ->
            currentHapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
            currentEntries.getOrNull(entryIndex)
                ?.items
                ?.getOrNull(itemIndex)
                ?.onClick
                ?.invoke()
            if (currentCollapseOnSelection) setExpanded(false)
        }
    }

    BasicComponent(
        modifier = modifier,
        interactionSource = interactionSource,
        insideMargin = insideMargin,
        title = title,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        startAction = startAction,
        endActions = {
            SelectedValueAndArrow(
                selectedText = selectedText,
                actionColor = actionColor
            )
            if (hasEntries) {
                DropdownEntriesPopup(
                    entries = nonEmptyEntries,
                    show = isDropdownExpanded.value,
                    popupPositionProvider = popupPositionProvider,
                    onDismissRequest = { setExpanded(false) },
                    onDismissFinished = { isHoldDown.value = false },
                    maxHeight = maxHeight,
                    renderInRootScaffold = renderInRootScaffold,
                    dropdownColors = dropdownColors,
                    onItemClick = onItemClick,
                )
            }
        },
        bottomAction = bottomAction,
        onClick = handleClick,
        role = Role.DropdownList,
        holdDownState = isHoldDown.value,
        enabled = actualEnabled,
    )
}

/** Compact pill trigger for the same anchored single-choice popup used by settings rows. */
@Composable
fun OverlayDropdownCapsule(
    entry: DropdownEntry,
    label: String? = null,
    modifier: Modifier = Modifier,
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    renderInRootScaffold: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
) {
    val entries = remember(entry) { listOf(entry) }.filter { it.items.isNotEmpty() }
    val actualEnabled = enabled && entries.isNotEmpty()
    val interactionSource = remember { MutableInteractionSource() }
    val isDropdownExpanded = remember { mutableStateOf(false) }
    val isHoldDown = remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val currentHapticFeedback by rememberUpdatedState(hapticFeedback)
    val currentOnExpandedChange = rememberUpdatedState(onExpandedChange)
    val currentEntries by rememberUpdatedState(entries)
    val popupPositionProvider = rememberNexioDropdownPositionProvider(preferAbove = true)
    val selectedText = entries.asSequence()
        .flatMap { it.items.asSequence() }
        .firstOrNull { it.selected }
        ?.text
    val displayedText = label ?: selectedText ?: "选择模型"
    val capsuleContentColor = if (isHoldDown.value) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val actionColor = if (actualEnabled) {
        MiuixTheme.colorScheme.onSurfaceVariantActions
    } else {
        MiuixTheme.colorScheme.disabledOnSecondaryVariant
    }

    val setExpanded: (Boolean) -> Unit = remember {
        { expanded ->
            if (isDropdownExpanded.value != expanded) {
                isDropdownExpanded.value = expanded
                currentOnExpandedChange.value?.invoke(expanded)
            }
        }
    }
    val handleClick = remember(actualEnabled) {
        {
            if (actualEnabled) {
                val expanded = !isDropdownExpanded.value
                setExpanded(expanded)
                if (expanded) {
                    isHoldDown.value = true
                    currentHapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
            }
        }
    }
    val onItemClick: (Int, Int) -> Unit = remember {
        { entryIndex, itemIndex ->
            currentHapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
            currentEntries.getOrNull(entryIndex)
                ?.items
                ?.getOrNull(itemIndex)
                ?.onClick
                ?.invoke()
            setExpanded(false)
        }
    }

    Box(modifier = modifier.widthIn(max = 280.dp)) {
        Surface(
            modifier = Modifier.clickable(
                interactionSource = interactionSource,
                role = Role.DropdownList,
                enabled = actualEnabled,
                onClick = handleClick,
            ),
            shape = CircleShape,
            color = if (isHoldDown.value) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            contentColor = capsuleContentColor,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = YumerinOpacity.DropdownOutline),
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = YumerinSpacing.FormField, vertical = YumerinSpacing.Dense),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = displayedText,
                    modifier = Modifier.widthIn(max = 228.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = capsuleContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DropdownArrowEndAction(actionColor = actionColor)
            }
        }

        if (entries.isNotEmpty()) {
            DropdownEntriesPopup(
                entries = entries,
                show = isDropdownExpanded.value,
                popupPositionProvider = popupPositionProvider,
                onDismissRequest = { setExpanded(false) },
                onDismissFinished = { isHoldDown.value = false },
                maxHeight = maxHeight,
                renderInRootScaffold = renderInRootScaffold,
                dropdownColors = dropdownColors,
                onItemClick = onItemClick,
                alignment = PopupPositionProvider.Align.Start,
            )
        }
    }
}

@Composable
private fun DropdownEntriesPopup(
    entries: List<DropdownEntry>,
    show: Boolean,
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    maxHeight: Dp?,
    renderInRootScaffold: Boolean,
    dropdownColors: DropdownColors,
    onItemClick: (Int, Int) -> Unit,
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.End,
) {
    if (entries.isEmpty()) return

    OverlayListPopup(
        show = show,
        popupPositionProvider = popupPositionProvider,
        alignment = alignment,
        enableWindowDim = false,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        maxHeight = maxHeight,
        renderInRootScaffold = renderInRootScaffold,
    ) {
        ListPopupColumn {
            entries.forEachIndexed { entryIndex, dropdownEntry ->
                dropdownEntry.items.forEachIndexed { itemIndex, item ->
                    key(entryIndex, itemIndex) {
                        MiuixDropdownItemRow(
                            item = item,
                            optionSize = dropdownEntry.items.size,
                            isSelected = item.selected,
                            index = itemIndex,
                            dropdownColors = dropdownColors,
                            enabled = dropdownEntry.enabled && item.enabled,
                            isFirst = entryIndex == 0 && itemIndex == 0,
                            isLast = entryIndex == entries.lastIndex &&
                                itemIndex == dropdownEntry.items.lastIndex,
                            onSelectedIndexChange = { selectedItemIndex ->
                                onItemClick(entryIndex, selectedItemIndex)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberNexioDropdownPositionProvider(
    preferAbove: Boolean = false,
): PopupPositionProvider {
    val density = LocalDensity.current
    val offsetX = with(density) { 7.dp.roundToPx() }
    val offsetY = with(density) { 5.dp.roundToPx() }

    return remember(offsetX, offsetY, preferAbove) {
        object : PopupPositionProvider {
            private val margins = PaddingValues(horizontal = 0.dp, vertical = 0.dp)

            override fun calculatePosition(
                anchorBounds: IntRect,
                windowBounds: IntRect,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
                popupMargin: IntRect,
                alignment: PopupPositionProvider.Align,
            ): IntOffset {
                val aligned = if (layoutDirection == LayoutDirection.Ltr) {
                    alignment
                } else {
                    when (alignment) {
                        PopupPositionProvider.Align.Start -> PopupPositionProvider.Align.End
                        PopupPositionProvider.Align.End -> PopupPositionProvider.Align.Start
                        else -> alignment
                    }
                }
                val x = if (aligned == PopupPositionProvider.Align.End) {
                    anchorBounds.right - popupContentSize.width - popupMargin.right + offsetX
                } else {
                    anchorBounds.left + popupMargin.left + offsetX
                }
                val spaceBelow = windowBounds.bottom - anchorBounds.bottom
                val spaceAbove = anchorBounds.top - windowBounds.top
                val showBelow = spaceBelow > popupContentSize.height
                val showAbove = !showBelow && spaceAbove > popupContentSize.height
                val y = when {
                    preferAbove && spaceAbove > popupContentSize.height ->
                        anchorBounds.top - popupContentSize.height - offsetY
                    showBelow -> anchorBounds.top - offsetY
                    showAbove -> anchorBounds.bottom - popupContentSize.height + offsetY
                    else -> anchorBounds.top + anchorBounds.height / 2 - popupContentSize.height / 2
                }
                val maxX = (windowBounds.right - popupContentSize.width - popupMargin.right)
                    .coerceAtLeast(windowBounds.left)
                val maxY = (windowBounds.bottom - popupContentSize.height - popupMargin.bottom)
                    .coerceAtLeast(windowBounds.top + popupMargin.top)
                val clamped = IntOffset(
                    x = x.coerceIn(windowBounds.left, maxX),
                    y = y.coerceIn(windowBounds.top + popupMargin.top, maxY),
                )
                return clamped
            }

            override fun getMargins(): PaddingValues = margins
        }
    }
}

@Composable
private fun RowScope.SelectedValueAndArrow(
    selectedText: String?,
    actionColor: androidx.compose.ui.graphics.Color,
) {
    if (selectedText != null) {
        Text(
            text = selectedText,
            fontSize = 14.2.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            maxLines = 1,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
    DropdownArrowEndAction(actionColor = actionColor)
}
