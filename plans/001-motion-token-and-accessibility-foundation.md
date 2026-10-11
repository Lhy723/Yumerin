# 001 — Establish shared motion tokens and reduced-motion behavior

- **Status**: IMPLEMENTED
- **Commit**: uncommitted workspace
- **Severity**: HIGH
- **Category**: Cohesion & tokens / Accessibility
- **Estimated scope**: 3–5 Kotlin files, roughly 150 lines

## Problem

Motion values are currently duplicated and embedded in components. `App.kt:155-164` uses 360 ms page movement and separate 180 ms fades; `App.kt:196-200` uses 320 ms bottom-bar visibility transitions. `DampedDragAnimation.kt:30-34` and `InteractiveHighlight.kt:26-27` define independent spring values, while `InteractiveGlassBottomBar.kt:158-161` adds another inline spring. There is no shared motion scale or reduced-motion branch.

## Target

Add `shared/src/commonMain/kotlin/com/lhy/yumerin/MotionTokens.kt` containing one source of truth:

```kotlin
internal object MotionTokens {
    val easeOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val easeInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    const val pressDurationMillis = 140
    const val smallDurationMillis = 180
    const val bottomBarDurationMillis = 220
    const val pageDurationMillis = 260
    const val shimmerDurationMillis = 1200

    val pressSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    val selectionSpring = spring<Float>(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMedium
    )
}
```

The token file may expose factory functions instead of cached specs if the Compose version requires them, but the values above must remain exact. Entering/exiting uses the strong `easeOut`; on-screen movement uses the strong `easeInOut`; spring-driven interactions remain interruptible. UI transitions stay below 300 ms. The 1200 ms value is only for constant Shimmer motion.

Add a small common `MotionPreference`/`CompositionLocal` boundary and an Android actual implementation that treats the system animator scale as reduced motion when it is zero. In reduced mode:

- Remove positional movement from page and bottom-bar transitions.
- Keep opacity/color feedback with `smallDurationMillis` and `easeOut`.
- Disable moving Shimmer and render a static tonal Skeleton.
- Keep press feedback, but use opacity/color rather than a large scale change.

## Repo conventions to follow

- Keep the API in `shared/src/commonMain/kotlin/com/lhy/yumerin`, matching the existing common Compose components.
- Use `Animatable`, `AnimatedContent`, `AnimatedVisibility`, and `Modifier.graphicsLayer` already used in the repo; do not add an animation library.
- Preserve `SizeTransform(clip = false)` for the existing horizontal content transition.

## Steps

1. Add `MotionTokens.kt` with the exact curves, durations, and spring factories above.
2. Add the common motion preference boundary and Android implementation; default to full motion when no reduced-motion signal is available.
3. Thread the preference through the existing app root without changing page state or navigation.
4. Replace raw transition durations and spring definitions in `App.kt`, `DampedDragAnimation.kt`, `InteractiveHighlight.kt`, and `InteractiveGlassBottomBar.kt` with token references.
5. Add unit coverage for reduced-motion mode selection and token duration values.

## Boundaries

- Do NOT add a third-party animation dependency.
- Do NOT change the liquid-glass rendering effects, page layout, or navigation model.
- Do NOT remove the bottom-bar spring interaction; centralize and retarget it.
- Do NOT make reduced motion equal to no feedback; retain opacity/color state changes.

## Verification

- **Mechanical**: run `./gradlew :shared:compileKotlinAndroid :androidApp:assembleDebug` and the relevant common tests; expected result is a successful Android build.
- **Feel check**: switch between the three bottom pages rapidly, open Settings/Models/About, press and drag the liquid handle, and confirm transitions retarget from their current position without restarting from zero.
- Set Android animator duration scale to 0 and confirm page movement and Shimmer translation disappear while state feedback remains visible.
- In slow motion, confirm page movement is 260 ms, bottom-bar visibility is 220 ms, and no UI transition exceeds 300 ms.
- **Done when**: no production motion file contains an inline duration or spring parameter that duplicates the token set, and reduced motion preserves comprehension feedback.
