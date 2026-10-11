# 003 — Tune screen and liquid-bar transitions for continuity

- **Status**: IMPLEMENTED
- **Commit**: uncommitted workspace
- **Severity**: HIGH
- **Category**: Easing & duration / Interruptibility
- **Estimated scope**: 4 Kotlin files, roughly 80 lines changed

## Problem

`shared/src/commonMain/kotlin/com/lhy/yumerin/App.kt:145-164` currently moves page content with `tween(360)` and fades it with `tween(180)`, exceeding the under-300 ms UI budget and using a separate timing pair from the bottom bar. `App.kt:196-200` uses `tween(320)` for bottom-bar appearance. `DampedDragAnimation.kt:96-104` launches `animateTo` for every pointer update, while `InteractiveGlassBottomBar.kt:158-161` owns an inline spring. This can queue competing animations during fast gestures and makes the liquid handle and page transition feel like separate systems.

## Target

- Keep the horizontal `AnimatedContent` direction and `SizeTransform(clip = false)`.
- Replace page movement with `MotionTokens.pageDurationMillis` (`260` ms) and `MotionTokens.easeInOut`; keep the fade at `MotionTokens.smallDurationMillis` (`180` ms) with `MotionTokens.easeOut`.
- Replace bottom-bar enter/exit timing with `MotionTokens.bottomBarDurationMillis` (`220` ms) and `MotionTokens.easeOut`.
- Route handle settling, press scale, highlight position, and panel offset through `MotionTokens.selectionSpring` or `MotionTokens.pressSpring` rather than inline `spring(...)` calls.
- During direct pointer movement, retarget the `Animatable` through the existing `MutatorMutex` at user-input priority or use `snapTo` for the sampled drag position; only settle to a target with the selection spring on release. Do not launch a new `animateTo` coroutine for every drag sample.
- Preserve velocity-aware distortion and the existing `SizeTransform(clip = false)` behavior; only the timing/retargeting source changes.

## Repo conventions to follow

- `App.kt:145-164` is the existing page-transition exemplar and must remain the single destination transition.
- `DampedDragAnimation.kt` already owns gesture state and `MutatorMutex`; extend that ownership instead of introducing a second gesture state machine.
- Keep all movement on `graphicsLayer`/transform and opacity; do not animate layout padding or size.

## Steps

1. Replace the page transition specs in `App.kt` with the shared 260/180 ms tokens and retain horizontal direction plus `clip = false`.
2. Replace the bottom-bar `AnimatedVisibility` 320 ms specs with the 220 ms token.
3. Move the inline offset spring from `InteractiveGlassBottomBar.kt:158-161` to the token set.
4. Migrate `InteractiveHighlight` and `DampedDragAnimation` raw spring values to the shared spring factories.
5. Make pointer updates interruptible and sample-driven: snap/retarget during drag, then perform exactly one spring settle on release or click.
6. Verify rapid taps, drag-to-release, drag cancellation, and navigation to Settings/Models/About.

## Boundaries

- Do NOT change tab widths, bottom-bar geometry, backdrop effects, or page spacing.
- Do NOT replace the AndroidLiquidGlass component with a custom imitation.
- Do NOT add clipping to solve transition overlap; `SizeTransform(clip = false)` is intentional.
- Do NOT introduce keyframes for toggles, drags, or other interruptible state.

## Verification

- **Mechanical**: run `./gradlew :shared:compileKotlinAndroid :androidApp:assembleDebug`; expected result is a successful build.
- **Feel check**: tap bottom tabs repeatedly, drag the liquid handle and reverse direction mid-drag, release, cancel, and immediately navigate to a settings subpage.
- Confirm the handle carries velocity into the settle, never jumps back to its origin, and never queues visibly behind pointer input.
- Confirm page transitions move horizontally with no visible clipping and complete within 260 ms; bottom-bar visibility completes within 220 ms.
- Toggle reduced motion and confirm movement is removed while selected-state opacity/color feedback remains.
- **Done when**: all high-frequency transitions are retargetable, transform/opacity-only, share the motion tokens, and pass a slow-motion feel check on a real Android device.
