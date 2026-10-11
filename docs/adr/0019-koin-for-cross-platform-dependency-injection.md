---
status: accepted
---

# Use Koin for cross-platform dependency injection

Yumerin will use Koin to assemble the Kotlin Multiplatform object graph across common domain/data/presentation code and Android infrastructure. The domain layer remains unaware of Koin; platform modules provide Room, LiteRT-LM, WorkManager, notification, and service implementations at the application boundary.

## Consequences

- Use cases, Repository contracts, Data sources, ViewModels, and provider adapters can be registered from shared modules where their dependencies are platform-independent.
- Android starts Koin from the application entry point and contributes Android-specific modules without moving Android types into domain.
- Tests can replace repositories, model clients, clocks, and schedulers with fakes through test modules.
- Koin is an assembly mechanism, not a business-logic location; classes remain constructor-injectable and usable without a global lookup.
