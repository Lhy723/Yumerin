---
status: accepted
---

# Persist Character affect and route scheduled work through Agent admission

The initial Agent Runtime boundary in ADR 0020 left affect persistence, relevance-based memory recall, scheduled-run coordination, and local-model capability calls for a later step. This decision implements those pieces without introducing Agent types into presentation.

## Decision

- Store dynamic mood and relationship continuity in a separate, character-scoped `CharacterRuntimeState` record. Do not mix evolving state into the editable Character profile or persist the user's transient affect classification.
- Appraise only the current message with a small deterministic classifier. Use it as uncertain response guidance. Mood has inertia and decays toward neutral; trust and closeness change only in small bounded increments. Commit state only after a successful response, with the source Chat and message identifiers retained as provenance.
- Retrieve accepted, non-expired, non-deleted memories within one Character's memory space. Rank by topic overlap, pinned priority, confidence, and recency. Return the actual recalled Memory IDs on the response.
- Route user messages, retries, and scheduled proactive messages through the same Agent Runtime. Serialize runs per Chat; foreground work waits, while a due scheduled trigger skips a busy Chat.
- Share the admission arbiter from the Android composition root so UI and WorkManager graphs in the same process coordinate on the same per-Chat locks.
- Persist user-created one-time Agent goals with a due time, state, and result-message link. Run them through the same per-Chat admission, global proactive switch, daily Character limit, memory recall, affect context, and notification quiet-time rules as scheduled proactive messages. A deterministic result-message ID lets a retried Worker recover an already-written message without duplicating it. A goal can produce one Chat message and cannot perform an external side effect.
- Let Android LiteRT-LM use the same registered read-only capabilities through a JSON action protocol. The provider adapter parses a request but never executes it.
- Register calculator, Character-scoped memory recall, and read-only schedule lookup modules at the application composition boundary. Keep network access, permission-dependent work, and side effects unavailable until an explicit authorization flow exists.
- Bound Agent runs by elapsed time, model output tokens, capability-call count, weighted capability cost units, and observation size. Pass output limits into both OpenAI-compatible and LiteRT-LM adapters; propagate caller cancellation.
- Persist Character runtime state and one-time Agent goals in Room schema version 9 and include them in portable export/import and local-data wipe.
- Normalize imported affect values to their valid ranges and reject malformed one-time goals before persistence.

## Consequences

- Emotional continuity, memory retrieval, provider adaptation, and trigger admission remain in `core`, `domain/agent`, and `data`; Chat screens continue to depend on use cases.
- Affect appraisal is deliberately lightweight and may miss nuance. It is temporary guidance, not a durable user profile or a clinical assessment.
- Memory retrieval is lexical and character-scoped; semantic/vector search can replace it behind the same domain boundary later.
- Recurring schedules and one-time, user-created goals are supported. Long-running self-directed goal planning and network/external-action capabilities remain outside this change.
