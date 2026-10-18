---
status: accepted
---
# Expose delegation as a capability and stage external-action approval

The model can now create a Delegated Agent goal from inside a chat turn: `delegate_task` is a registered read-only capability (direct chats only, delegation-intent gated) that validates safety and length rules and hands the request to the existing goal pipeline (plan ≤ 6 steps, bounded background runs, wait-for-user, pause/resume/cancel). The capability only creates the goal; it never runs steps inline and never authorizes external writes. Its observation tells the model to report the start in its own voice, keeping the persona layer as the user-facing surface.

Chat detail renders an active-goal progress card from the already-persisted goal steps (status, step marks, latest summary, pending question) — collapsible, driven by repository observation, no new storage.

`PendingExternalAction` lands as the minimal ADR-0022 boundary: the scope field family (service account, action type, target, full content, timing, impact limit, explicitly flexible fields), 24-hour expiry, and a resolve use case where rejection cancels the linked goal. V1 ships the model, repository contract, and in-memory store; the Room store and approval UI attach with the full external-authorization work (issue #1).

`ToolGateway`, `PromptHook`, and `AgentExtensionManifest` define the extension seams: MCP-shaped tool descriptions, prompt-lifecycle hooks that never grant permissions, and in-process extension declarations. Dynamic loading stays deferred and is never implied.

## Boundaries

- `domain/agent/DelegateTaskCapability.kt` follows the existing capability contract; the JSON argument parser now reads multiple string fields and skips any other value type.
- `core/model/PendingExternalAction.kt` owns the action model and pure expiry rules; `domain/repository/PendingExternalActionRepository.kt` owns storage and resolution.
- Registered extensions cannot alter tool permissions; approval stays per-action with the user.

## Consequences
- Multi-step delegation is reachable end-to-end from one chat turn (issue #3/#4 functionality), with the runtime's existing budget, admission, and serialization rules applying unchanged.
- A goal created via `delegate_task` still shares the global proactive switch and daily limits through the existing pipeline.
- The approval UI and a real executor for approved actions remain future work; `PendingExternalAction` never executes by itself.
