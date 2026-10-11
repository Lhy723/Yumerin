package com.lhy.yumerin

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.MutatePriority
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import com.lhy.yumerin.designsystem.MotionTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Clock

class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    private val valueRange: ClosedRange<Float>,
    visibilityThreshold: Float,
    private val initialScale: Float,
    private val pressedScale: Float,
    private val reducedMotion: Boolean = false,
    private val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    private val onDragStopped: DampedDragAnimation.() -> Unit,
    private val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit
) {
    private val valueAnimationSpec = MotionTokens.dragValueSpring(visibilityThreshold)
    private val velocityAnimationSpec = MotionTokens.dragVelocitySpring(visibilityThreshold)
    private val pressProgressAnimationSpec = MotionTokens.dragPressProgressSpring()
    private val scaleXAnimationSpec = MotionTokens.dragScaleXSpring()
    private val scaleYAnimationSpec = MotionTokens.dragScaleYSpring()
    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val progress: Float
        get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            }
        ) { change, dragAmount ->
            onDrag(size, dragAmount)
        }
    }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            if (reducedMotion) {
                scaleXAnimation.snapTo(initialScale)
                scaleYAnimation.snapTo(initialScale)
            } else {
                launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
                launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
            }
        }
    }

    fun release() {
        animationScope.launch {
            awaitFrame()
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            if (reducedMotion) {
                scaleXAnimation.snapTo(initialScale)
                scaleYAnimation.snapTo(initialScale)
            } else {
                launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
                launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
            }
        }
    }

    fun updateValue(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        animationScope.launch {
            mutatorMutex.mutate(MutatePriority.UserInput) {
                // Pointer samples are state retargets, not queued animations.
                valueAnimation.snapTo(targetValue)
                updateVelocity()
            }
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val targetValue = value.coerceIn(valueRange)
                if (reducedMotion) {
                    valueAnimation.snapTo(targetValue)
                    velocityAnimation.snapTo(0f)
                    release()
                    return@mutate
                }
                launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release()
            }
        }
    }

    /** Moves the selector to a pressed tab while keeping its press state until the gesture ends. */
    fun animateToValueKeepingPress(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                val targetValue = value.coerceIn(valueRange)
                if (reducedMotion) {
                    valueAnimation.snapTo(targetValue)
                    velocityAnimation.snapTo(0f)
                    return@mutate
                }
                valueAnimation.animateTo(targetValue, valueAnimationSpec)
            }
        }
    }

    private suspend fun updateVelocity() {
        velocityTracker.addPosition(
            Clock.System.now().toEpochMilliseconds(),
            Offset(value, 0f)
        )
        val targetVelocity = velocityTracker.calculateVelocity().x /
            (valueRange.endInclusive - valueRange.start)
        velocityAnimation.snapTo(targetVelocity)
    }
}
