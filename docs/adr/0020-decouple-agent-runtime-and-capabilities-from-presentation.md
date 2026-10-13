---
status: accepted
---

# Keep Agent Runtime and capability modules independent from presentation

Agent execution belongs to the framework-free domain layer. The Runtime coordinates bounded model steps and capability results; provider adapters translate model requests and responses; capability modules validate and execute their own inputs. Presentation calls existing Use cases and renders their progress and final reply without importing Agent Runtime or capability implementation types.

## Boundaries

- `core` owns provider-neutral Agent request, conversation, capability descriptor, and model-step contracts.
- `domain/agent` owns context assembly, the Runtime loop, capability registry, safety checks, and capability modules. It does not depend on Compose, Android, Room, or a provider SDK.
- `data` owns model-provider adapters. An adapter may return either a final reply or a structured capability request; it never executes the requested capability itself.
- `SendChatMessageUseCase`, `RetryChatResponseUseCase`, and `SendProactiveMessageUseCase` invoke the Runtime. The UI continues to call its Use cases and consumes the existing accumulated-text callback and reply result.
- Capability modules are registered at the application composition boundary. A module can be added without changing Chat screens or ViewModels.

## Safety and limits

- Each run executes at most four capability calls by default. Cancellation is propagated to the owning chat operation.
- Only registered capabilities are executable. Capability inputs and observations are bounded; module failures are returned as observations rather than escaping the Runtime loop.
- Capabilities that mutate state, need permissions, or require user confirmation are not exposed until the corresponding authorization flow exists. External side effects are unavailable by default.
- V1 registers a local read-only calculator as the first concrete module. OpenAI-compatible adapters can exchange structured capability calls; LiteRT-LM remains on its current one-shot path until it supports a compatible action protocol.
- Tool-aware remote requests do not expose partial model tool arguments to Chat. Normal requests without a relevant capability keep the existing streaming path.

## Consequences

- Provider integrations, capability modules, and presentation can evolve independently behind the contracts.
- Adding a capability requires a domain module and registration; platform I/O still needs a data-layer adapter.
- Proactive messages use the Runtime's one-shot path and do not receive capabilities. Persistent character affect, web search, external messaging, and a shared admission/arbiter for autonomous triggers remain future work behind these boundaries.
- This decision refines the future-tool boundary in ADR 0015 while retaining its compressed-context and character-scoped memory rules.
