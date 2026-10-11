# Motion implementation plans

The Compose motion surface now has shared motion tokens, a reduced-motion path, reusable Skeleton/Shimmer primitives, and a retargetable liquid handle. The implementation order below is complete:

1. `001-motion-token-and-accessibility-foundation.md` — establish shared curves, durations, spring specs, and reduced-motion behavior.
2. `002-skeleton-shimmer-loading-states.md` — add reusable loading primitives using the shared motion policy.
3. `003-screen-and-liquid-bar-motion.md` — migrate existing page, bottom-bar, and gesture transitions to the shared tokens and fix rapid-interaction retargeting.

All three plans are implemented in the current workspace; the product data and navigation layers remain separate from the motion primitives.
