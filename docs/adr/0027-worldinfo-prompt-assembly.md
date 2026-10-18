---
status: accepted
---

# Activate world-info entries with community semantics and clean-room code

Persona assets may carry a world book (card-embedded `character_book` and, later, standalone lorebooks). Entry activation follows the public community conventions: keyword keys with and/or/not selective logic, constant entries, recursive activation with `preventRecursion` and `excludeRecursionIds`, inclusion groups resolved by weight, ordered injection, probabilistic activation, and three injection positions (before persona, after persona, at a chat depth inside history).

Activation is keyword-first with an optional pluggable `SemanticScorer` on top (default scorer omitted; a vector backend can be added without changing the entry format). Keyless entries can only activate via constant mode or a semantic hit.

## Boundaries

- `core/model/PersonaModels.kt` owns the book/entry data classes; `domain/persona/WorldInfoEngine` owns activation. Entry content is data: it is never executed and never influences tool permissions or safety checks.
- Injection order is deterministic: position, then depth, then order; at most `MAX_ACTIVATED_ENTRIES` (64) entries activate per run.
- Unknown book fields round-trip losslessly via `rawExtras` so imported cards export intact.
- The engine is a clean-room implementation from public format documentation (the SillyTavern reference implementation is AGPL-3.0 and is not copied).

## Rationale

- Pure keyword lorebooks degrade past ~100 entries; a hybrid keyword+semantic gate is the community's direction and fits the deferred-vector-recall decision in ADR 0021 (lexical scoring today, embeddings later).
- Card content is untrusted input (prompt injection, embedded links, scripts): treating it as prompt data with activation semantics — not as code — keeps the safety boundary of ADR-0022 intact even when tasks use capabilities.

## Consequences

- Prompt assembly (ADR 0026) consumes `WorldInfoInjection` values at the persona slots and history depth; regression snapshots compare assembled prompts against fixture cards.
- Probabilistic entries take a pluggable source so tests are deterministic; production uses a random roll.
- Full SillyTavern trigger parity (regex-keyword forms, recursive weight tuning) is incremental: unsupported constructs are preserved in `rawExtras` and reported by the import wizard rather than silently dropped.
