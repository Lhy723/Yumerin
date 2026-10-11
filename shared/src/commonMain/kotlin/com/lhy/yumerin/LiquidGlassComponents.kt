package com.lhy.yumerin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

@Composable
internal fun GlassSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    surfaceAlpha: Float = 0.78f,
    content: @Composable BoxScope.() -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha)
    Box(
        modifier = modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(16.dp.toPx())
                lens(18.dp.toPx(), 22.dp.toPx())
            },
            onDrawSurface = {
                drawRect(surfaceColor)
            }
        ),
        content = content
    )
}

/** The stronger liquid-glass treatment used by floating controls and bars. */
@Composable
internal fun LiquidGlassSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    surfaceAlpha: Float = 0.42f,
    content: @Composable BoxScope.() -> Unit
) {
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha)
    Box(
        modifier = modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(16.dp.toPx())
                lens(24.dp.toPx(), 24.dp.toPx())
            },
            highlight = {
                Highlight.Default.copy(alpha = 0.42f)
            },
            shadow = {
                Shadow(alpha = 0.18f)
            },
            innerShadow = {
                InnerShadow(radius = 8.dp, alpha = 0.14f)
            },
            onDrawSurface = {
                drawRect(surfaceColor)
                drawRect(
                    if (isLightTheme) Color.White.copy(alpha = 0.08f)
                    else Color.Black.copy(alpha = 0.08f)
                )
            }
        ),
        content = content
    )
}
