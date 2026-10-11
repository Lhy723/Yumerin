package com.lhy.yumerin

import androidx.compose.foundation.border
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

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
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val contentTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f)
    val captureTint = if (isLightTheme) Color.Black else Color.White
    val trackColor = if (isLightTheme) {
        Color(0xFFFAF9FC).copy(alpha = 0.68f)
    } else {
        Color(0xFF24232A).copy(alpha = 0.70f)
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
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val barWidth = maxWidth * 0.63f
        BoxWithConstraints(
            modifier = Modifier
                .width(barWidth)
                .height(56.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val horizontalInsetPx = with(density) { 4.dp.toPx() }
            val tabWidthPx = ((constraints.maxWidth - horizontalInsetPx * 2f) / pages.size)
                .coerceAtLeast(1f)
            val tabWidth = with(density) { tabWidthPx.toDp() }
            val trackBorder = Brush.verticalGradient(
                colors = if (isLightTheme) {
                    listOf(Color.White.copy(alpha = 0.84f), Color.White.copy(alpha = 0.16f))
                } else {
                    listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.07f))
                }
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
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
                        highlight = {
                            Highlight.Default.copy(alpha = 0.22f * pressProgress)
                        },
                        shadow = { Shadow(alpha = 0.16f) },
                        innerShadow = { InnerShadow(radius = 7.dp, alpha = 0.12f) },
                        onDrawSurface = { drawRect(trackColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .border(0.8.dp, trackBorder, Capsule())
            )

            NavigationTabsContent(
                selectedPage = selectedPage,
                tint = contentTint,
                onPageSelected = onPageSelected,
                modifier = Modifier
                    .fillMaxSize()
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
                        val scale = lerp(1f, 1.12f, pressProgress)
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
                        translationX = selectorAnimation.value * tabWidthPx
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
                            Highlight.Default.copy(alpha = 0.82f * pressProgress)
                        },
                        shadow = { Shadow(alpha = 0.22f + 0.18f * pressProgress) },
                        innerShadow = {
                            InnerShadow(
                                radius = 7.dp * pressProgress,
                                alpha = 0.16f * pressProgress
                            )
                        },
                        onDrawSurface = {
                            drawRect(
                                if (isLightTheme) Color.Black.copy(alpha = 0.055f)
                                else Color.White.copy(alpha = 0.095f),
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
                    .pointerInput(pages.size, tabWidthPx) {
                        val touchSlop = viewConfiguration.touchSlop
                        val maxIndex = (pages.lastIndex).toFloat()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downX = down.position.x
                            val tabIndex = floor((downX - horizontalInsetPx) / tabWidthPx)
                                .toInt()
                                .coerceIn(0, pages.lastIndex)
                            val currentThumbCenter = horizontalInsetPx +
                                (selectorAnimation.value + 0.5f) * tabWidthPx
                            var dragging = abs(downX - currentThumbCenter) <= tabWidthPx * 0.55f
                            selectorAnimation.press()
                            if (!dragging) selectorAnimation.animateToValueKeepingPress(tabIndex.toFloat())
                            var lastX = downX
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: break
                                if (!change.pressed) {
                                    if (dragging) {
                                        val target = selectorAnimation.targetValue
                                            .roundToInt()
                                            .coerceIn(0, pages.lastIndex)
                                        if (target != selectedIndex) {
                                            onPageSelected(pages[target])
                                        }
                                        selectorAnimation.animateToValue(target.toFloat())
                                    } else if (
                                        change.position.x < 0f ||
                                        change.position.x > size.width ||
                                        change.position.y < 0f ||
                                        change.position.y > size.height
                                    ) {
                                        selectorAnimation.animateToValue(selectedIndex.toFloat())
                                    } else {
                                        onPageSelected(pages[tabIndex])
                                        selectorAnimation.release()
                                    }
                                    break
                                }

                                val dx = change.position.x - lastX
                                val totalDx = change.position.x - downX
                                if (!dragging && abs(totalDx) > touchSlop) {
                                    dragging = true
                                }
                                if (dragging && abs(dx) > 0.01f) {
                                    lastX = change.position.x
                                    selectorAnimation.updateValue(
                                        (selectorAnimation.targetValue + dx / tabWidthPx)
                                            .coerceIn(0f, maxIndex)
                                    )
                                    change.consume()
                                } else {
                                    lastX = change.position.x
                                }
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

private val AppPage.navigationIcon: ImageVector
    get() = when (this) {
        AppPage.HOME -> YumerinHomeIcon
        AppPage.EXPLORE -> YumerinCharacterIcon
        AppPage.PROFILE -> YumerinProfileIcon
    }

private val YumerinHomeIcon = ImageVector.Builder(
    name = "YumerinHomeIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 3f)
        lineTo(2f, 11f)
        lineTo(4f, 11f)
        lineTo(4f, 21f)
        lineTo(10f, 21f)
        lineTo(10f, 14f)
        lineTo(14f, 14f)
        lineTo(14f, 21f)
        lineTo(20f, 21f)
        lineTo(20f, 11f)
        lineTo(22f, 11f)
        close()
    }
}.build()

private val YumerinCharacterIcon = ImageVector.Builder(
    name = "YumerinCharacterIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(8f, 11f)
        curveTo(10.21f, 11f, 12f, 9.21f, 12f, 7f)
        curveTo(12f, 4.79f, 10.21f, 3f, 8f, 3f)
        curveTo(5.79f, 3f, 4f, 4.79f, 4f, 7f)
        curveTo(4f, 9.21f, 5.79f, 11f, 8f, 11f)
        close()
        moveTo(16.5f, 11f)
        curveTo(18.43f, 11f, 20f, 9.43f, 20f, 7.5f)
        curveTo(20f, 5.57f, 18.43f, 4f, 16.5f, 4f)
        curveTo(15.87f, 4f, 15.29f, 4.17f, 14.78f, 4.46f)
        curveTo(15.32f, 5.17f, 15.67f, 6.05f, 15.67f, 7f)
        curveTo(15.67f, 8.35f, 15.08f, 9.56f, 14.14f, 10.4f)
        curveTo(14.8f, 10.78f, 15.61f, 11f, 16.5f, 11f)
        close()
        moveTo(8f, 13f)
        curveTo(4.13f, 13f, 1f, 15f, 1f, 17.5f)
        lineTo(1f, 21f)
        lineTo(15f, 21f)
        lineTo(15f, 17.5f)
        curveTo(15f, 15f, 11.87f, 13f, 8f, 13f)
        close()
        moveTo(16.5f, 13f)
        curveTo(15.93f, 13f, 15.39f, 13.05f, 14.87f, 13.14f)
        curveTo(16.22f, 14.13f, 17f, 15.47f, 17f, 17f)
        lineTo(17f, 21f)
        lineTo(23f, 21f)
        lineTo(23f, 17.5f)
        curveTo(23f, 15f, 20.1f, 13f, 16.5f, 13f)
        close()
    }
}.build()

private val YumerinProfileIcon = ImageVector.Builder(
    name = "YumerinProfileIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 12f)
        curveTo(14.761f, 12f, 17f, 9.761f, 17f, 7f)
        curveTo(17f, 4.239f, 14.761f, 2f, 12f, 2f)
        curveTo(9.239f, 2f, 7f, 4.239f, 7f, 7f)
        curveTo(7f, 9.761f, 9.239f, 12f, 12f, 12f)
        close()
        moveTo(12f, 14f)
        curveTo(7.581f, 14f, 4f, 16.239f, 4f, 19f)
        lineTo(4f, 22f)
        lineTo(20f, 22f)
        lineTo(20f, 19f)
        curveTo(20f, 16.239f, 16.419f, 14f, 12f, 14f)
        close()
    }
}.build()
