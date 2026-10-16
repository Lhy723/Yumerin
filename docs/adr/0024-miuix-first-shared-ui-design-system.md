---
status: accepted
---

# Prefer Miuix and centralize Yumerin's UI design system

Yumerin needs a consistent component library, visual material, and layout across its shared Compose interface. Custom screens and explicit user design requirements remain possible, but should not cause accidental one-off styles or duplicate component implementations.

## Decision

- Use a suitable component from the project's current Miuix dependency whenever one exists. Use Jetpack Compose native or platform components only when Miuix lacks the needed component or cannot meet a stated functional or platform requirement.
- Keep Miuix components and native fallbacks on the same shared theme and design-system tokens. Component material is a global, switchable appearance choice; it is not independently hard-coded by each screen or widget.
- Standardize page structure, safe-area handling, spacing, dimensions, shape, and typography through semantic design-system rules and reusable components.
- When the same component or visual/interaction pattern appears in two or more places, evaluate it for extraction into a shared component. Keep distinct behavior explicit rather than hiding it behind an overly generic abstraction.
- An explicit user requirement for a particular screen or component takes precedence over these defaults. Keep the exception scoped to the requested surface and preserve it in that surface's acceptance notes when needed.

## Consequences

- New UI work checks Miuix first and documents the reason for any native component fallback.
- Global material changes must propagate across Miuix controls and native fallbacks through shared tokens or wrappers.
- Shared layout and typography are maintained centrally; screen files should compose these patterns rather than invent parallel versions.
- Existing appearance options such as accent color do not automatically satisfy the separate requirement for a switchable material style. The product can decide the exact material options when implementing that control.
- UI changes are checked on the rendered Android screen, including relevant material variants; compilation alone is insufficient visual acceptance.
