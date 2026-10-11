---
status: accepted
---

# Keep Android/KMP feature code behind clean architecture boundaries

Yumerin follows a Clean Architecture dependency direction for Android and Kotlin Multiplatform work. The logical layers are `app`, `presentation`, `domain`, `data`, `core`, and reusable `design-system`; feature modules may be introduced as the product grows. The dependency direction is:

```text
app -> presentation, domain, data, core
presentation -> domain, design-system, core
data -> domain, core
domain -> core (or no project dependency)
core -> no project dependency
```

The `domain` layer remains pure Kotlin. It owns framework-free domain models, repository contracts, and Use cases. Room entities, network DTOs, LiteRT-LM runtime objects, Android services, and Compose state remain outside domain and are converted through data-layer mappers or provider adapters.

## Consequences

- Each business operation is represented by a focused Use case with `operator fun invoke` where appropriate.
- Repository interfaces are declared in domain and implemented in data; repositories coordinate focused local, remote, and runtime Data sources.
- ViewModels expose immutable Presentation state and delegate business rules to Use cases rather than becoming service or database coordinators.
- Room is an Android data-layer implementation; its entities and DAOs never cross into domain or presentation.
- Errors cross boundaries through `Result` or a project-defined sealed error type and are mapped to user-facing UI state at presentation.
- All asynchronous work follows Structured concurrency and is owned by a ViewModel, Worker, service, or request session.
- Dependency injection is an infrastructure concern and uses Koin for the KMP-compatible object graph; domain code does not import Koin APIs.
