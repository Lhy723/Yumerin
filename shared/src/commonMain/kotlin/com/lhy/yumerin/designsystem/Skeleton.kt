package com.lhy.yumerin.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    reducedMotion: Boolean = false
) {
    val base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    val brush = if (reducedMotion) {
        Brush.linearGradient(listOf(base, base))
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton-shimmer")
        val progress by transition.animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(
                animation = tween(MotionTokens.shimmerDurationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "skeleton-shimmer-progress"
        )
        val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(progress * 420f, 0f),
            end = Offset(progress * 420f + 240f, 0f)
        )
    }
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .background(brush, shape)
    )
}

@Composable
fun ChatListSkeleton(modifier: Modifier = Modifier, reducedMotion: Boolean = false) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(4) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SkeletonBlock(Modifier.size(52.dp), RoundedCornerShape(18.dp), reducedMotion)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(Modifier.fillMaxWidth(0.55f).height(16.dp), reducedMotion = reducedMotion)
                    SkeletonBlock(Modifier.fillMaxWidth(0.88f).height(13.dp), reducedMotion = reducedMotion)
                }
            }
        }
    }
}
