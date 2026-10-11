---
status: accepted
---

# Support multi-step Agent runs over compressed context

Yumerin responses may be produced by an Agent run rather than a single model completion. An Agent run can compress older Chat context, retrieve Character-scoped memory, invoke tools such as web search, and then produce one final response. Context compression remains separate from durable memory so shortening a Chat does not silently create new Memory entries. For Group chats, compression may produce a user-visible Group context summary that is shared only as Group-visible context and never becomes a Character's durable memory automatically.

## Consequences

- A response needs a step lifecycle in addition to the final message lifecycle.
- Context assembly triggers automatic compression at 80% of the configured Context window limit, leaving headroom for the next response.
- Context assembly combines Character and Chat settings, recent raw messages, the Chat's compressed summary, and relevant Character-scoped Memory; Group chats additionally use their Group-visible context and Group context summary.
- Private Chat summaries are hidden in normal conversation but user-accessible from Chat details for viewing, editing, and regeneration; they remain Chat-scoped rather than becoming Character Memory.
- V1 Agent runs execute only local context compression and Character Memory retrieval automatically; external Tool invocations are placeholders and are not executed.
- Future Tool invocation requires explicit capability boundaries, failure handling, and cancellation behavior.
- Future RAG and context recall can be implemented as steps in the Agent run without changing Chat or Memory entry concepts.
- Agent execution visibility is a global three-mode setting—final response only, concise status, or detailed step results—and defaults to final response only; no mode exposes private internal reasoning.
- Sending messages inside Yumerin Chats is the core response behavior and does not require per-message confirmation.
- Sending messages to external platforms or third-party contacts is outside the V1 tool boundary.
- Web search is reserved as a future Tool invocation placeholder; its privacy and authorization policy are intentionally deferred.
