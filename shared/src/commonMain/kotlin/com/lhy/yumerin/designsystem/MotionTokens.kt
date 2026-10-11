package com.lhy.yumerin.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset

/** Shared motion values keep screen changes and component feedback coherent. */
object MotionTokens {
    val easeOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val easeInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    const val pressDurationMillis = 140
    const val smallDurationMillis = 180
    const val bottomBarDurationMillis = 220
    const val pageDurationMillis = 260
    const val shimmerDurationMillis = 1200

    fun bottomBarSettleSpring() = spring<Float>(1f, 300f, 0.5f)

    fun dragValueSpring(visibilityThreshold: Float) = spring<Float>(1f, 1000f, visibilityThreshold)
    fun dragVelocitySpring(visibilityThreshold: Float) = spring<Float>(0.5f, 300f, visibilityThreshold * 10f)
    fun dragPressProgressSpring() = spring<Float>(1f, 1000f, 0.001f)
    fun dragScaleXSpring() = spring<Float>(0.6f, 250f, 0.001f)
    fun dragScaleYSpring() = spring<Float>(0.7f, 250f, 0.001f)
    fun highlightPressProgressSpring() = spring<Float>(0.5f, 300f, 0.001f)
    fun highlightPositionSpring() = spring<Offset>(0.5f, 300f, Offset.VisibilityThreshold)
}
