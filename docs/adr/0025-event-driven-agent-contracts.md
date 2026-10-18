---
status: accepted
---

# Emit typed Agent run events instead of a single accumulated-text callback

An Agent run reports progress through a typed event contract (`AgentRunEvent`) instead of only an accumulated-text callback. The contract covers run lifecycle (`AgentRunStarted`, `AgentRunCompleted`, `AgentRunFailed`), incremental output (`AgentTextDelta`), capability activity (`AgentCapabilityRequested`, `AgentCapabilityCompleted`, `AgentObservationRecorded`), budget pressure (`AgentBudgetWarning`), and cancellation (`AgentRunInterrupted`). Every event carries a run id, a trace id shared with the originating user turn, and a timestamp.

## Boundaries

- `core/model/AgentEvents.kt` owns the event data classes; they are framework-free and provider-neutral like the rest of the Agent contracts.
- `AgentRuntime.run` accepts an `onEvent` handler alongside the legacy `onAccumulatedText` callback. Existing callers keep working; new callers subscribe to events.
- Interruption is a first-class outcome: a cancelled run emits `AgentRunInterrupted` before propagating `CancellationException`, so channels can render "stopped" consistently instead of inferring it from failure paths.
- A run ends in exactly one terminal event: `AgentRunCompleted` on success or `AgentRunFailed` when the reply carries a failure status.

## Rationale

- Text, voice, and future channels share one run contract. A voice pipeline needs interruption and step progress signals that a text-only callback cannot express; TEN-style streaming pipelines treat interruption as a cancel event, and this contract makes that explicit from day one.
- Chat bubbles, the future execution-progress card, and the debug panel all read the same event stream rather than scraping message text.

## Consequences

- The `ChatDetailScreen` render path and the bubble renderer consume events for capability status lines and interrupt copy; presentation state tracks generating/capability-running/interrupted from events rather than message text conventions.
- Serialization for cross-process transport (remote workers, a future voice service) is deliberately deferred: events are plain data classes today, and a transport boundary can serialize them without changing emission or consumption.
- This refines ADR 0015/0020's boundary contracts; run budgets, admission, and capability rules are unchanged.
