---
status: accepted
---

# Isolate long-term memory by Character

Group-visible context may be read by all participating Characters during a Group chat, but every automatically generated Memory entry must be written to exactly one Character's isolated memory space according to that Character's relevance. Yumerin will not create a default global or group-shared long-term memory; users may explicitly manage or assign memories when they want to share information across Characters.

## Consequences

- Context assembly and memory persistence are separate responsibilities.
- A group message can be visible to several Characters without creating cross-character memory leakage.
- Retrieval must always be scoped by user and Character before a Memory entry is considered for a response.
