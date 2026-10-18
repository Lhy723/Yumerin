---
status: accepted
---

# Consolidate memory through a four-layer model and a post-turn integrator

Character memory keeps the single `MemoryEntry` unit (ADR 0001) but organizes usage into four layers. **Working memory** remains the compressed chat summary. **Episodic memory** gains `importance`, `emotionTag`, and `occurredAt` so event entries can rank and age. **Portrait memory** is the new `StructuredFact` (subject-predicate-value) carried on a `MemoryEntry`, giving the user viewable, editable, deletable structured facts about themselves. **Relationship state** extends `CharacterRuntimeState` with `formsOfAddress`, `sharedInJokes`, and `openCommitments`, edited from the existing continuity settings.

Writing moves behind a `MemoryIntegrator`: extraction runs only after a turn fully succeeded (failed, cancelled, and retried-in-vain turns never write), through a pluggable `MemoryExtractor` whose default bridges the lexical `MemoryPolicy`. Dedup, topic-conflict supersession, and the review queue rules are unchanged from the previous inline behavior.

Reading moves behind a `MemoryProvider` (default `RepositoryMemoryProvider` over the lexical retriever). It exposes ranked `retrieve` plus a compact `brief` — pinned and important entries with a recency tail — that proactive messages and a future voice call read ahead of the turn instead of mid-turn.

## Boundaries

- `domain/memory/MemoryProvider.kt` and `MemoryIntegrator.kt` are framework-free; Room stores the new columns via migration v12 and portable packages round-trip them.
- `SendChatMessageUseCase` calls the integrator after `CharacterStateCoordinator.commit`, gated by the existing `autoMemoryEnabled` setting.
- `AgentRunDiagnostics` records per-run prompt slot budgets, world-info hits, capability calls, and the memory recall trace in a process-local ring consumed by the Agent debug page in Settings. It stores no message text or raw observations.

## Consequences

- Model-based extraction (via a cheap model route) can replace the lexical extractor without touching use cases.
- Users see structured facts and relationship-state fields through existing memory and continuity surfaces; no separate UI system is introduced.
- Diagnostics are transient by design; they are not exported in portable packages and clear on restart.
- This refines ADR 0001/0009 storage rules and keeps ADR 0021's lexical recall as the default ranking.
