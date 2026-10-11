# 002 — Add reusable Skeleton and Shimmer loading states

- **Status**: IMPLEMENTED
- **Commit**: uncommitted workspace
- **Severity**: MEDIUM
- **Category**: Missed opportunities / Performance
- **Estimated scope**: 2–4 Kotlin files, roughly 180 lines

## Problem

The current UI renders only completed static page content. There are no loading primitives for Chat list rows, Character cards, model cards, or model download metadata. Introducing async Room/model states later without a loading state would cause content to pop in abruptly and would make the first-run local model experience feel unfinished.

## Target

Add reusable common Compose primitives under `shared/src/commonMain/kotlin/com/lhy/yumerin`:

- `SkeletonBlock(modifier, shape, color)` — a layout-preserving placeholder with no app-specific padding or width assumptions.
- `Shimmer(modifier, enabled)` — a moving highlight over the placeholder using a linear gradient and `MotionTokens.shimmerDurationMillis` (`1200` ms). The animation must use `LinearEasing` because it is constant motion.
- `ChatListSkeleton`, `CharacterCardSkeleton`, and `ModelCardSkeleton` — small composition helpers that accept caller modifiers and preserve the eventual content footprint.

The default Skeleton uses `MaterialTheme.colorScheme.surfaceVariant` with a restrained alpha and a single highlight pass. Shimmer translates only the brush/gradient; it must not animate layout, width, height, padding, or text measurement.

When reduced motion is active, render the static Skeleton without a moving highlight. Do not replace it with a rapid opacity pulse.

## Repo conventions to follow

- Use existing Material 3 color roles and Compose shapes; do not introduce a new color palette.
- Keep components framework-level and style-light so the existing Liquid Glass and stock-component combination remains the visual authority.
- Use `Modifier.graphicsLayer` or draw-time brush transforms; do not use animated layout properties.

## Steps

1. Add the generic `SkeletonBlock` and `Shimmer` primitives.
2. Add the three footprint-specific skeleton compositions without hard-coding screen-level margins.
3. Add previews or deterministic Compose tests for full-motion and reduced-motion states.
4. Integrate the primitives only at async boundaries introduced by the Chat list, model catalog, and model management screens; keep completed content unchanged.

## Boundaries

- Do NOT add a dependency such as Accompanist Placeholder or a web animation library.
- Do NOT animate layout dimensions or use `transition: all`-style broad property animation.
- Do NOT add decorative stagger to high-frequency Chat list rows.
- Do NOT show Skeletons after data has already loaded.

## Verification

- **Mechanical**: run `./gradlew :shared:compileKotlinAndroid :androidApp:assembleDebug` and Compose tests; expected result is a successful build.
- **Feel check**: open Chat list and Model management while data is delayed; confirm the final layout does not jump when Skeletons are replaced.
- Confirm the Shimmer travels at constant speed, does not recompose the whole list, and disappears immediately when content is ready.
- Toggle reduced motion and confirm Skeletons remain static but still communicate loading.
- **Done when**: all loading states preserve content footprint, animate only draw/transform properties, and use the shared 1200 ms linear Shimmer token.
