---
status: accepted
---
# Import community character cards with lossless extension handling

Community character cards (SillyTavern-compatible PNG exports and raw card JSON) import into Yumerin's persona model through a clean-room parser: the card JSON rides base64-encoded in PNG `tEXt` chunks keyed `chara` (v2) or `ccv3` (v3), `ccv3` wins when both exist, and v2/v3 cards read from the `data` object while legacy v1 cards read flat. Card-embedded `character_book` and standalone lorebooks map into the world-info engine's entry model (selective logic, positions, probability, recursion flags, inclusion groups).

Lossless round-trip is the contract: `extensions` and any unrecognized fields are preserved verbatim (`PersonaPackage.rawExtras`, book `rawExtras`) and every unmapped field is reported — the import wizard's compatibility report lists `data.<field>` / `entry.<uid>.<field>` names instead of silently dropping them.

Card content is untrusted input. Parsing never executes embedded data, entries only ever contribute prompt text through the activation engine, and card content cannot influence tool permissions (ADR 0027).

## Boundaries

- `data/portable/st/` owns parsing (`SillyTavernCardParser`, `LorebookParser`, `JsonFlattener`, `Base64Common`) in commonMain; platform image handling stays out of scope.
- `zTXt` (compressed) chunks are not decoded and surface as failures with a reason; card JSON size is capped.
- The import wizard UI (SAF file picking, compatibility report rendering, memory seeding from Data Bank text) attaches at the presentation layer without changing these contracts.
- No SillyTavern source is used; behavior follows the public card-spec and lorebook field documentation only (AGPL clean-room boundary).

## Consequences
- Regression tests parse fixture cards into persona + world-info snapshots; extending field coverage is additive.
- Community cards with regex scripts or embedded hooks in `extensions` are preserved for export but never activated by Yumerin.
- Unsupported constructs degrade to the report rather than breaking import.
