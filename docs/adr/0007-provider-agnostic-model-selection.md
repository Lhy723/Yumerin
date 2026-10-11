---
status: accepted
---

# Keep model integration provider-agnostic

Yumerin will support multiple Model providers and user-selectable Model profiles instead of binding Characters or Chats to one model implementation. New AI responses use the selected Active model, which can be changed globally or directly within a Chat; model-specific request and streaming details remain behind a provider-neutral model client boundary so additional providers can be added without changing the domain or presentation layers.

V1 uses User-provided model credentials (BYOK) rather than an application-owned shared model account. Credentials are configured per Model provider and remain local to the device in V1.

V1 standardizes the first integration boundary on OpenAI-compatible APIs: a Model profile carries a provider label, base URL, model identifier, and User-provided model credential. Native provider protocols may be added later behind separate adapters.

## Consequences

- Model availability and configuration belong outside Character profile and Memory entry concepts.
- The model settings experience must represent provider, model identifier, endpoint/auth configuration, and selection state separately.
- The app does not absorb model usage costs or proxy all requests through a shared credential in V1.
- OpenAI-compatible request/response behavior is the initial integration contract, not the permanent limit of the model domain.
- Switching models must not rewrite existing Chat history or Character memory.
- A Character model assignment may differ between Characters in the same Group chat and persists across that Character's future Chats.
- The global model selection is the default only for Characters without an explicit assignment.
- A model already generating a response continues to completion; a new assignment affects later responses.
- A Model profile may carry a local Context window limit and optional generation parameters; the context limit guides assembly and compression rather than binding the domain to a provider-specific request field.
