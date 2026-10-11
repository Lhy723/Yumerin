---
status: accepted
---

# Use memory entries as the canonical unit of long-term memory

Yumerin will automatically generate extensible Memory entries for each Character-user relationship instead of treating raw chat messages as long-term memory. Each Character has isolated memory, and users can manage entries directly; the model must support long-term memories, user preferences, relationship state, and important events while leaving room for future vector retrieval, RAG, and context recall without changing the domain concept.

Only stable, future-useful information is eligible for durable memory. Temporary feelings, one-off plans, and ordinary small talk remain Short-term context unless the user or the system later identifies them as durable.

## Consequences

- Automatic memory extraction is part of the product loop, but users retain control through Memory management.
- Conflicting new information supersedes the current entry without silently destroying the prior entry; uncertain information must not automatically overwrite a current entry.
- Users can inspect, restore, or delete superseded Memory entries.
- Memory extraction is automatic for ordinary stable high-confidence facts. Clearly uncertain, sensitive, or conflicting candidates enter a non-blocking review queue and are excluded from recall until accepted; low-confidence inferences remain short-term context only. Ordinary entries never require per-entry confirmation.
- Disabling automatic memory generation is forward-looking: it preserves existing entries, skips future extraction while disabled, and does not backfill old Chat history when re-enabled by default.
- Pinning raises retrieval priority but does not prevent conflict resolution from superseding an outdated entry.
- Durable entries do not expire automatically by default; optional expiration or review timestamps can mark an entry for review without deleting it.
- Normal Chat output does not expose memory retrieval details by default, but each response may provide an on-demand trace of the Memory entries used.
- Retrieval technology can evolve independently from the Memory entry domain model.
- Memory must always be scoped to a Character and user relationship; global cross-character memory is not the default.
- Every Memory entry should retain user-visible Memory provenance so the user can understand and manage its origin.
