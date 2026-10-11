---
status: accepted
---

# Add LiteRT-LM as the Android local model runtime

Yumerin will support a local inference backend beside remote Model providers. Android uses LiteRT-LM's Kotlin API through a provider adapter and a process-scoped `LocalAiService`; LiteRT-LM types remain in the Android infrastructure layer. Local model files use the `.litertlm` format and are managed as versioned Local model artifacts rather than bundled into the application by default.

## Model lifecycle

- `LocalModelManager` owns the catalog and lifecycle of local artifacts: discovery, download, cancellation/resume, checksum verification, atomic installation, activation, and removal.
- User-started downloads run as resumable WorkManager jobs with persisted progress, pause/cancel/retry controls, and a foreground progress notification when the operation is long-running; `LocalAiService` never owns download work.
- Large downloads default to Wi-Fi-only WorkManager constraints and show size/storage estimates before starting; the user may explicitly allow mobile data.
- New catalog versions notify the user but do not download automatically; updates begin only after an explicit action in Model management.
- Model download progress uses its own Notification channel and remains visible in the app when that channel is disabled; notification preference does not cancel the underlying job.
- V1 downloads only artifacts from a curated, versioned Local model catalog and supports importing a local `.litertlm` file; arbitrary URL downloads are deferred.
- The first catalog release offers a small Gemma `.litertlm` artifact as an optional download rather than bundling model bytes in the APK; larger Gemma models, other small models, and classifiers remain extension points.
- Runtime backend selection exposes automatic, CPU, and GPU choices; automatic prefers GPU and falls back to CPU. NPU capability remains an optional extension and is not required for V1.
- Catalog entries expose source, checksum, license, size, Context window limit, backend capability, and device requirements before download.
- Model management exposes name, version, size, license, backend, Context window limit, lifecycle state, assignment count, and permitted actions for each artifact.
- Model metadata is persisted in Room; large model files are stored in app-private files storage, with temporary download files kept separate until verification succeeds.
- Downloaded or imported artifacts pass device compatibility checks for ABI, Android version, storage, memory, backend availability, and a lightweight initialization test before activation; failures retain a diagnostic reason.
- The first download of each model requires a user-visible license/source/size/privacy acknowledgement; later versions require acknowledgement again only when the license changes.
- Only a fully downloaded, checksum-verified artifact can become active or be assigned to a Character.
- Multiple verified artifacts may be active simultaneously; activation makes an artifact eligible for selection and does not eagerly load an engine.
- A failed or interrupted download never replaces the currently active artifact.
- Model updates install and verify a new version beside the old one before the logical model's active version changes; global and Character assignments bind the logical model rather than a particular artifact version. The old version remains removable only after no runtime or rollback policy references it.
- After verification, the new version automatically becomes current for future requests without changing any global or Character assignment; in-flight responses continue on the version they acquired.
- An artifact assigned as the global default or to any Character cannot be removed until all assignments are changed; removal then clears its file and metadata.
- If a local Model profile later becomes unavailable, its assignment is preserved and the user must explicitly select a replacement; the system never silently falls back to a remote provider.

## Runtime lifecycle

- `LocalAiService` is a process-scoped singleton that owns lazily created LiteRT-LM engines keyed by stable local model identity and version.
- `acquire(modelId)` initializes the engine off the UI thread and returns a `Local engine lease`; `release()` decrements the reference count.
- When a model's reference count reaches zero, the service keeps the engine for a 30-second idle grace period to avoid repeated expensive initialization; it closes the Conversation and Engine sooner on app backgrounding or memory pressure. No caller may retain raw LiteRT-LM objects after releasing its lease.
- Each Chat or Agent response creates a short-lived Local conversation session from the assembled context and closes it after completion, failure, or cancellation; Conversation state is never shared across Chats or Characters.
- Chat, Agent, and background work use the provider-neutral inference interface and never construct LiteRT-LM `Engine` objects directly.

## Consequences

- Gemma and other small on-device LLMs can be added by registering artifacts and capabilities instead of changing Chat or Character concepts.
- Later local classifiers, embeddings, and RAG components can share the Local model manager while using specialized adapters rather than pretending every model is a chat engine.
- Local inference does not send Character profiles, Chat context, or Memory entries over the network; network access is limited to user-initiated model catalog downloads or updates, and the UI should identify local responses.
- Local and remote Model profiles share the same Chat, Memory, Agent, Proactive message, and Background companion behavior; local inference is not a separate background mode or special trigger path.
- Download, verification, activation, and runtime failures are represented as recoverable local states and do not corrupt Chat history.
- LiteRT-LM SDK version, model artifact version, backend choice, and device compatibility must be recorded for diagnostics and future migrations.
