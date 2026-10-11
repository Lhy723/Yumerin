---
status: accepted
---

# Use Room for Android local persistence

Yumerin will use Room for the Android V1 local database because the first product implementation is intended to run locally on Android and needs structured persistence for Characters, Chats, Group messages, and Memory entries. Room types and DAOs must stay in the Android data layer; shared domain contracts remain framework-independent, and other targets require their own adapter only if they become product scope.

## Consequences

- Room entities must be mapped to domain models before reaching presentation.
- The Android data layer owns database migrations and local transaction boundaries.
- KMP shared code cannot assume Room APIs; Web/Wasm persistence remains a separate future decision.
