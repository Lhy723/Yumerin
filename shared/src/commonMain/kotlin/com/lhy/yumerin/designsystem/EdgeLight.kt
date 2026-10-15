package com.lhy.yumerin.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class EdgeLight(
    val color: Color = Color.White.copy(alpha = 0.5f),
    val width: Dp = 0.5.dp,
    val blurRadius: Dp = 1.dp,
    val intensity: Float = 1f
)

/** Draws a soft, shape-following highlight around a translucent surface. */
@Stable
fun Modifier.edgeLight(
    shape: Shape,
    edgeLight: EdgeLight = EdgeLight()
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val strokeWidth = edgeLight.width.toPx().coerceAtMost(size.minDimension / 2f)
    val blurRadius = edgeLight.blurRadius.toPx().coerceAtLeast(0f)
    val layers = listOf(
        (strokeWidth + blurRadius * 2f) to 0.1f,
        (strokeWidth + blurRadius) to 0.2f,
        strokeWidth to 0.55f
    )

    onDrawWithContent {
        drawContent()

        val alpha = (edgeLight.color.alpha * edgeLight.intensity).coerceIn(0f, 1f)
        if (alpha <= 0f || strokeWidth <= 0f) return@onDrawWithContent

        layers.forEach { (layerWidth, layerAlpha) ->
            drawOutline(
                outline = outline,
                color = edgeLight.color.copy(alpha = alpha * layerAlpha),
                style = Stroke(width = layerWidth)
            )
        }
    }
}
