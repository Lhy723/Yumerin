---
status: accepted
---

# Start local-first while keeping persistence sync-ready

V1 will persist all product data on the user's device and will not require an account or cloud backend. On Android, Room is the local persistence implementation; it remains behind platform data sources and repository contracts so the domain and presentation layers do not depend on Room. The domain and data boundaries must remain Sync-ready so a future remote data source and synchronization workflow can be introduced without replacing the Character, Chat, Group message, or Memory entry concepts used by the UI.

## Consequences

- V1 must work without network access and without authentication.
- Android's Room implementation is an infrastructure choice, not a domain model.
- Repository contracts should not expose storage-specific details.
- Core entities use stable UUID identities plus creation, update, and soft-deletion metadata so portable import and future synchronization do not depend on Room row positions.
- Future synchronization will need explicit conflict, identity, and versioning decisions; those are intentionally deferred rather than hidden in the local model.
