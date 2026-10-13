---
status: accepted
---

# Support durable, user-delegated Agent goals

Yumerin is an emotional-companion app whose Agent should also complete useful, multi-step work. The existing Scheduled Agent goal is a one-time, proactive Chat message. It does not model a persistent objective, a plan, or progress across multiple Agent runs. This decision proposes a separate Delegated Agent goal and defines how it interacts with Character continuity, capabilities, Android background work, and the existing approval rules.

## Decision

- Keep a Delegated Agent goal distinct from a Scheduled Agent goal. A user creates a Delegated Agent goal by describing an outcome and its constraints; the Agent decomposes it into at most six steps and may replace the remaining steps at most twice within that scope. The Agent reports meaningful milestones, pauses when it needs a user decision, and stores the goal's plan and progress as a durable goal record.
- Treat the user's delegation as authorization to begin bounded, in-scope work, including best-effort background execution. The goal can be paused, resumed, or cancelled by the user. The global Proactive-message switch continues to govern proactive companionship and one-time scheduled messages; it does not pause an explicitly delegated goal. Goal progress is task reporting, not an unsolicited Proactive message.
- Execute goals in persisted, bounded work units through Android's background-work adapter. Use [WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent) to resume eligible work after process exit or device restart, subject to Android scheduling constraints. Do not promise continuous or immediate execution. A goal that needs user input remains paused until the user returns; notification delivery follows existing quiet-time and notification-permission behavior, and goal state remains available in Yumerin if a notification is unavailable.
- Reuse the provider-neutral Agent Runtime and registered capability contract. V1 ships built-in modules behind a stable extension interface; third-party installation and sharing remain deferred. Keep goal orchestration in domain use cases and persistence, with Android scheduling and UI presentation behind their existing boundaries.
- Apply the existing external-service authorization rules to every goal. External reads require a service connection and, before remote-model sharing, the app-wide external-data consent. Every external write requires a separate preview and approval; blocked high-impact actions remain unavailable. A background write waits in Yumerin for review, expires after 24 hours, and rejection or expiry cancels its linked Goal. A goal never grants a capability additional permission.
- Keep goal plans, progress, and outcomes in the goal record. Do not automatically copy external-service data into Character memory; durable Character memories continue through the existing memory lifecycle. Character affect and relationship continuity should shape the Agent's natural tone and responses, remain inspectable or adjustable in settings, and not appear as numeric scores in Chat.

## Consequences

- ADR 0013 remains the policy for continuous Background companion mode and unsolicited or scheduled proactive behavior. A Delegated Agent goal is an additional user-authorized source of bounded background work; it does not start the continuous Background companion Foreground Service. Android restricts starting foreground services from the background, so goal execution does not depend on keeping one active ([Foreground Service start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)).
- ADR 0021's one-time Scheduled Agent goal remains supported. Delegated goals add durable multi-step progress without changing the one-message semantics of the scheduled goal.
- ADR 0022's approval boundaries remain in force. This decision extends its pending-action lifecycle to linked Delegated Agent goals while keeping the same 24-hour expiry, explicit approval, and cancellation behavior.
- WorkManager can preserve and reschedule work, but Android may defer execution. The UI must expose the real goal state, including waiting for user input, running, paused, completed, cancelled, or failed, instead of implying that a goal is continuously active.
- Character memory is not a transcript of every goal step. Goal history remains available for task continuity, while the separate memory policy controls which durable user facts and preferences inform later companionship.
