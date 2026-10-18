---
status: accepted
---

# Model message parts and route by declared model capabilities

Messages gain typed content parts (`MessagePart`: text, image, audio, file) alongside the existing text column; the encoded parts live in a `partsJson` Room column (v13) and portable packages round-trip them. Raw bytes stay in app-private storage — parts carry references, never blobs.

Model profiles declare their real capabilities (`VISION`, `AUDIO_IN`, `TOOL_CALLS`, `STREAMING`), stored per profile and user-correctable in model settings. A `ModelRouter` selects a profile per purpose (`CONVERSATION`, `TASK_EXECUTION`, `MEMORY_EXTRACTION`, `VOICE`) with an explicit degradation rule: the preferred profile wins whenever it satisfies the hard requirements; otherwise a capable profile is chosen and the selection reports a fallback reason so the UI can surface the swap — a missing capability never silently changes which model answers.

A `MessageNormalizer` turns parts into what the selected model can consume: images pass through only with VISION, otherwise they degrade to describe-first via a pluggable `ImageDescriber` (no VLM is wired in V1; the file never leaves the device); audio requires AUDIO_IN or transcribe-first; files never reach the model.

## Boundaries

- `core/model/MessageParts.kt` owns part and capability data classes plus the Room codec; `domain/model/ModelRouter.kt` and `MessageNormalizer.kt` own selection and planning; adapters consume the normalized plan.
- Chat bubbles render image parts through `MessageImageView` (per-platform; Web preview shows a fallback).
- Sending attachments is not yet exposed in the composer; the data path and rendering land first so the picker UI can attach without another storage migration.

## Consequences
- Model-based memory extraction (M2's `MemoryExtractor`) and the future voice channel can request purpose-specific profiles through the router.
- Capability declarations are trusted user input: a wrong declaration causes normalization notes, not crashes.
- Portable packages carry parts references; files themselves stay device-local by the existing portable-data rules.
