package com.lhy.yumerin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.lhy.yumerin.designsystem.EdgeLight
import com.lhy.yumerin.designsystem.edgeLight
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

@Composable
internal fun MiuixFloatingBottomBar(
    backdrop: Backdrop,
    selectedPage: AppPage,
    onPageSelected: (AppPage) -> Unit,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val pages = AppPage.entries
    val selectedIndex = pages.indexOf(selectedPage).coerceAtLeast(0)
    val latestSelectedIndex = rememberUpdatedState(selectedIndex)
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val contentTint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    val captureTint = if (isLightTheme) Color.Black else Color.White
    val trackColor = if (isLightTheme) {
        Color(0xFFFAFAFA).copy(alpha = 0.6f)
    } else {
        Color(0xFF242424).copy(alpha = 0.6f)
    }
    val animationScope = rememberCoroutineScope()
    val selectorAnimation = remember(animationScope, pages.size, reducedMotion) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..pages.lastIndex.toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            reducedMotion = reducedMotion,
            onDragStarted = {},
            onDragStopped = {},
            onDrag = { _, _ -> }
        )
    }
    val tabsBackdrop = rememberLayerBackdrop()
    val glowBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop, glowBackdrop)
    val pressProgress = selectorAnimation.pressProgress
    val viewConfiguration = LocalViewConfiguration.current
    val density = LocalDensity.current

    LaunchedEffect(selectorAnimation, selectedIndex) {
        if (selectorAnimation.targetValue != selectedIndex.toFloat()) {
            selectorAnimation.animateToValue(selectedIndex.toFloat())
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        val barWidth = minOf(maxWidth * 0.89f, 420.dp)
        BoxWithConstraints(
            modifier = Modifier
                .width(barWidth)
                .height(56.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val offsetAnimation = remember { Animatable(0f) }
            val panelOffset by remember(density, constraints.maxWidth) {
                derivedStateOf {
                    val fraction = (offsetAnimation.value / constraints.maxWidth.coerceAtLeast(1))
                        .coerceIn(-1f, 1f)
                    with(density) {
                        4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                    }
                }
            }
            val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
            val ltrSign = if (isLtr) 1f else -1f
            val latestPanelOffset = rememberUpdatedState(panelOffset)
            val interactiveHighlight = remember(animationScope, density) {
                InteractiveHighlight(
                    animationScope = animationScope,
                    position = { size, finger ->
                        val scale = 1f + with(density) { 16.dp.toPx() } / size.width
                        Offset(
                            (finger.x - latestPanelOffset.value - size.width / 2f) / scale + size.width / 2f,
                            (finger.y - size.height / 2f) / scale + size.height / 2f
                        )
                    }
                )
            }
            val horizontalInsetPx = with(density) { 4.dp.toPx() }
            val tabWidthPx = ((constraints.maxWidth - horizontalInsetPx * 2f) / pages.size)
                .coerceAtLeast(1f)
            val tabWidth = with(density) { tabWidthPx.toDp() }
            val edgeLight = remember(isLightTheme) {
                EdgeLight(
                    color = Color.White.copy(alpha = if (isLightTheme) 0.6f else 0.2f),
                    width = if (isLightTheme) 0.24.dp else 0.34.dp,
                    blurRadius = 0.4.dp
                )
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = panelOffset
                        val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, pressProgress)
                        scaleX = scale
                        scaleY = scale
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(24.dp.toPx(), 24.dp.toPx())
                        },
                        onDrawSurface = { drawRect(trackColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .edgeLight(Capsule(), edgeLight)
            )

            NavigationTabsContent(
                selectedPage = selectedPage,
                tint = contentTint,
                onPageSelected = onPageSelected,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = panelOffset
                        val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, pressProgress)
                        scaleX = scale
                        scaleY = scale
                    }
                    .padding(4.dp)
            )

            // A separate transparent export preserves the icon and label pixels for the
            // moving lens. This keeps the selected capsule optically connected to the tabs.
            Row(
                modifier = Modifier
                    .clearAndSetSemantics { }
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer {
                        translationX = panelOffset
                        val scale = lerp(1f, 1.2f, pressProgress)
                        scaleX = scale
                        scaleY = scale
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(
                                24.dp.toPx() * pressProgress,
                                24.dp.toPx() * pressProgress
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = pressProgress)
                        },
                        onDrawSurface = { drawRect(trackColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .fillMaxSize()
                    .padding(4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(captureTint)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavigationTabsContent(
                    selectedPage = selectedPage,
                    tint = contentTint,
                    onPageSelected = {},
                    modifier = Modifier.fillMaxSize(),
                    captureOnly = true
                )
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(glowBackdrop)
                    .graphicsLayer {
                        translationX = panelOffset
                        val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, pressProgress)
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(Capsule())
            )

            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .graphicsLayer {
                        translationX = if (isLtr) {
                            selectorAnimation.value * tabWidthPx + panelOffset
                        } else {
                            (pages.lastIndex - selectorAnimation.value) * tabWidthPx + panelOffset
                        }
                        scaleX = selectorAnimation.scaleX
                        scaleY = selectorAnimation.scaleY
                        val velocity = selectorAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                    }
                    .width(tabWidth)
                    .height(48.dp)
                    .drawBackdrop(
                        backdrop = combinedBackdrop,
                        shape = { Capsule() },
                        effects = {
                            lens(
                                10.dp.toPx() * pressProgress,
                                14.dp.toPx() * pressProgress,
                                chromaticAberration = true
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = pressProgress)
                        },
                        shadow = { Shadow(alpha = pressProgress) },
                        innerShadow = {
                            InnerShadow(
                                radius = 8.dp * pressProgress,
                                alpha = pressProgress
                            )
                        },
                        onDrawSurface = {
                            drawRect(
                                if (isLightTheme) Color.Black.copy(alpha = 0.06f)
                                else Color.White.copy(alpha = 0.1f),
                                alpha = 1f - pressProgress
                            )
                            drawRect(Color.Black.copy(alpha = 0.035f * pressProgress))
                        }
                    )
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .then(interactiveHighlight.gestureModifier)
                    .pointerInput(pages.size, tabWidthPx, isLtr) {
                        val touchSlop = viewConfiguration.touchSlop
                        val maxIndex = (pages.lastIndex).toFloat()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downX = down.position.x
                            val tabIndex = (if (isLtr) {
                                floor((downX - horizontalInsetPx) / tabWidthPx)
                            } else {
                                floor((size.width - horizontalInsetPx - downX) / tabWidthPx)
                            })
                                .toInt()
                                .coerceIn(0, pages.lastIndex)
                            val currentThumbCenter = if (isLtr) {
                                horizontalInsetPx + (selectorAnimation.value + 0.5f) * tabWidthPx
                            } else {
                                size.width - horizontalInsetPx -
                                    (selectorAnimation.value + 0.5f) * tabWidthPx
                            }
                            var dragging = abs(downX - currentThumbCenter) <= tabWidthPx * 0.55f
                            selectorAnimation.press()
                            if (!dragging) selectorAnimation.animateToValueKeepingPress(tabIndex.toFloat())
                            var lastX = downX
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) {
                                    val target = if (dragging) {
                                        selectorAnimation.targetValue
                                            .roundToInt()
                                            .coerceIn(0, pages.lastIndex)
                                    } else {
                                        tabIndex
                                    }
                                    if (target != latestSelectedIndex.value) onPageSelected(pages[target])
                                    selectorAnimation.animateToValue(target.toFloat())
                                    selectorAnimation.release()
                                    animationScope.launch {
                                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                                    }
                                    break
                                }

                                val dx = change.position.x - lastX
                                val totalDx = change.position.x - downX
                                if (!dragging && abs(totalDx) > touchSlop) {
                                    dragging = true
                                    lastX = change.position.x
                                }
                                if (dragging && abs(dx) > 0.01f) {
                                    lastX = change.position.x
                                    selectorAnimation.updateValue(
                                        (selectorAnimation.targetValue + dx / tabWidthPx * ltrSign)
                                            .coerceIn(0f, maxIndex)
                                    )
                                    animationScope.launch {
                                        offsetAnimation.snapTo(offsetAnimation.value + dx)
                                    }
                                } else {
                                    lastX = change.position.x
                                }
                                change.consume()
                            }

                        }
                    }
            )
        }
    }
}

@Composable
private fun NavigationTabsContent(
    selectedPage: AppPage,
    tint: Color,
    onPageSelected: (AppPage) -> Unit,
    modifier: Modifier = Modifier,
    captureOnly: Boolean = false
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppPage.entries.forEach { page ->
            val selected = selectedPage == page
            val itemModifier = if (captureOnly) {
                Modifier
            } else {
                Modifier
                    .clip(Capsule())
                    .semantics(mergeDescendants = true) {
                        contentDescription = page.label
                        role = Role.Tab
                        this.selected = selected
                        onClick(label = page.label) {
                            onPageSelected(page)
                            true
                        }
                    }
            }
            Column(
                modifier = itemModifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = page.navigationIcon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = page.label,
                    color = tint,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}
