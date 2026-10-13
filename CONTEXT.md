# Yumerin Domain Context

Yumerin is an AI emotional-companionship product centered on customizable romantic relationships and long-term interaction. This context records the product language that has been resolved during design.

## Relationship and audience

**Character**:
A user-created AI persona with its own identity, personality, relationship framing, and interaction style.
_Avoid_: Skin, prompt preset, account

**Virtual companion**:
A Character configured to participate in an ongoing emotional or romantic relationship with a user.
_Avoid_: Chatbot, assistant, NPC

**Character profile**:
The editable identity and interaction definition of a Virtual companion, including its locally stored avatar, name, relationship framing, setting, description, personality, tone, backstory, and interaction style. Relationship framing is expressed through this profile rather than a separate fixed relationship-type concept.
_Avoid_: System prompt, configuration blob

**Character runtime state**:
The persistent emotional and relationship context of one Character, distinct from its stable profile and from inferred facts about the user. It shapes conversational tone and relationship continuity; users may inspect or adjust it in settings, while Chat shows it through interaction rather than numeric scores.
_Avoid_: User mood record, fixed personality, model scratchpad

**Transient emotional appraisal**:
A low-confidence classification of affect expressed in the current user message. It may guide the immediate response but is not stored as a durable fact or diagnosis about the user.
_Avoid_: Mental-health diagnosis, permanent user trait

**Character archive**:
A reversible inactive state for a Character. An archived Character remains stored with its private Chats, Group histories, and Memory entries but is excluded from ordinary selection, proactive behavior, and new interaction until restored.
_Avoid_: Permanent deletion, hidden active companion

**Character permanent deletion**:
A confirmed destructive operation that removes a Character's profile, locally stored avatar, isolated Memory entries, and related private Chat data; the UI must state the affected data before completion. Historical Group messages remain in their Group timeline, but are detached from the deleted Character and display a deleted-character sender label; the deleted Character cannot participate in new messages.
_Avoid_: Archive, soft hide

**Default companion seed**:
The initial profile used to provide a ready-to-use, anime-style female Virtual companion and pre-created Chat thread for a new user, while remaining editable like any other Character.
_Avoid_: Hard-coded character, demo account

**Model setup gate**:
The just-in-time setup state shown when a user tries to send a message without a usable Model profile. V1 does not require model configuration during first launch; the user enters the Chat list first and can configure a model when needed.
_Avoid_: Mandatory onboarding, hidden API requirement

**Character memory**:
Persistent information associated with one Character's relationship with one user; it is isolated from every other Character by default.
_Avoid_: Shared memory, global chat history

**Character-scoped memory**:
The rule that an automatically generated Memory entry is written to exactly one Character's memory space according to that Character's relevance, even when the source message appeared in a Group chat visible to multiple Characters.
_Avoid_: Group memory, shared persona memory

**Memory entry**:
A distinct piece of persistent information that a Character may use to understand and continue its relationship with a user.
_Avoid_: Raw message, prompt fragment, vector chunk

**Memory category**:
A semantic classification for a Memory entry. The initial categories are long-term memory, user preference, character relationship, and important event; the category set must remain extensible.
_Avoid_: Fixed memory type, database table

**Memory management**:
The user-facing control of a Character's Memory entries, including viewing, editing, deleting, pinning, and temporarily disabling automatic memory generation. Disabling affects only future messages; existing entries remain, and re-enabling does not backfill from older Chat history by default.
_Avoid_: Chat settings, prompt editing

**Pinned memory**:
A Memory entry marked for higher retrieval priority by the user. Pinning influences recall priority but does not make the entry permanently true or prevent a high-confidence conflicting entry from superseding it.
_Avoid_: Immutable fact, forced prompt

**Memory validity**:
The applicability window of a Memory entry. Durable memory does not expire automatically by default, but an entry may have an optional expiration or review time; reaching it marks the entry for review without deleting it.
_Avoid_: Hard deletion, automatic forgetting

**Memory provenance**:
The traceable origin of a Memory entry, such as the Chat thread and time from which the durable information was inferred.
_Avoid_: Hidden reason, model log

**Memory version state**:
The lifecycle state that records whether a Memory entry is current, superseded, or otherwise no longer applicable. When a new entry conflicts with an existing one, the new entry becomes current while the old entry remains available as a superseded version unless the user removes it.
_Avoid_: Destructive overwrite, silent replacement

**Memory confidence**:
The extraction confidence and risk assessment used to decide whether an inferred fact becomes durable automatically, enters a user-reviewable pending queue, or stays only in Short-term context. Ordinary stable high-confidence entries can be written automatically; only clearly uncertain, sensitive, or conflicting candidates require review before recall; low-confidence inferences are not persisted as Memory entries.
_Avoid_: Model certainty, permanent guess

**Memory review queue**:
A non-interruptive collection of exceptional Memory candidates that need user review because they are uncertain, sensitive, or conflict with existing information. The queue supports batch acceptance or rejection and does not require confirmation of ordinary entries one by one.
_Avoid_: Per-message confirmation, blocking dialog

**Memory recall trace**:
The user-accessible record of which Character-scoped Memory entries were used for a response. It remains hidden in the normal Chat view and can be opened on demand from a message.
_Avoid_: Hidden reasoning, mandatory debug panel

**Durable memory**:
Stable, future-useful information about the user or the Character-user relationship that is worth retaining across conversations.
_Avoid_: Raw transcript, temporary context

**Short-term context**:
Temporary conversational information used to maintain the current interaction without becoming a Durable memory entry by default.
_Avoid_: Long-term memory, permanent history

## Conversation

**Chat**:
A chronological conversation presented as a persistent message stream between a user and one or more participants.
_Avoid_: Prompt session, temporary request

**Chat thread**:
An independent Chat with its own message history and Short-term context, linked to its participating Characters without creating a separate long-term identity for them.
_Avoid_: One global conversation, memory partition

**Chat list**:
The user's entry view that organizes and surfaces Chat threads in a WeChat-style list, ordered by recent activity by default and supporting user-pinned Chats.
_Avoid_: Dashboard, Character list

**Primary navigation**:
The V1 app-level navigation with Chat, Characters, and My destinations. The Chat destination opens the Chat list first; settings, Model profiles, and About content are reached from My.
_Avoid_: Direct-to-character home, scattered settings

**Motion continuity**:
The V1 interaction principle that page transitions use coherent horizontal movement, preserve shared state, and avoid visible content clipping; bottom navigation and other animations should feel like one continuous transition.
_Avoid_: Abrupt replacement, decorative motion, clipped slide

**Unread state**:
The per-Chat indication that new Group messages or Character messages exist since the user last viewed that Chat, or that the user manually marked it unread.
_Avoid_: Notification history, message count total

**Pinned Chat**:
A Chat thread explicitly kept at the top of the Chat list regardless of recent activity.
_Avoid_: Archived Chat, Chat mute

**Chat mute**:
A per-Chat preference that suppresses user-facing alerts while preserving message persistence and Unread state.
_Avoid_: Delete, archive

**Chat deletion**:
A user-confirmed operation that removes a Chat thread's messages and Short-term context from the local conversation view without deleting the participating Character's durable Memory entries. It does not delete the Character or Group profile.
_Avoid_: Character deletion, memory wipe

**Background companion**:
A user-visible mode that allows Yumerin to schedule and deliver Proactive messages, companionship reminders, and timed interactions while the app is not open. When enabled, Android keeps a non-dismissible Foreground Service notification with pause and stop actions; disabling the mode stops the service and removes its notification. The mode cannot be enabled while notification permission is denied.
_Avoid_: Invisible keepalive, background chat loop

**Companion notification**:
An Android notification representing a Character-initiated message, reminder, or scheduled interaction, subject to global notification and per-Chat mute preferences.
_Avoid_: Silent push, system log

**Notification preview**:
The Character-authored message excerpt shown in a Companion notification before the user opens its Chat.
_Avoid_: Hidden notification, full transcript

**Notification channel**:
An Android-level category used to control the alert behavior of chat messages, companionship reminders, Background companion status, or Local model download progress independently.
_Avoid_: Chat thread, message type

**Notification permission request**:
The user-facing Android permission flow for notifications, requested when a notification-dependent feature is first enabled rather than during initial launch. Denial leaves ordinary Chat usable but blocks Background companion until the user enables permission in system settings.
_Avoid_: Startup prompt, implicit permission

**Quiet time**:
A device-local time window during which Proactive messages do not emit notifications. V1 defaults to 23:00–08:00; messages are still persisted and marked unread, and the user may change or disable the window.
_Avoid_: Message suppression, service shutdown

**Companion schedule**:
A user-defined time-based instruction for a Character to send a reminder, greeting, or other scheduled interaction.
_Avoid_: Cron job, background loop

**Scheduled Agent goal**:
A user-created, one-time interaction for one Character and its Direct Chat, due between one minute and one year after creation. It produces at most one ordinary Chat message, shares the global proactive-message switch and daily limit, respects quiet-time notification rules, and can be cancelled before execution. A due goal may prepare a Pending external action, but the goal itself never authorizes it; only the user's separate approval of that exact action can do so. A stable result-message ID lets a retried Worker recover a message already written.
_Avoid_: Unbounded autonomous task, recurring schedule, external side effect

**Delegated Agent goal**:
A user-delegated objective that a Character pursues through a persisted plan of up to six steps and reports at step milestones. Each background run handles one bounded work unit; the Agent may replace remaining steps at most twice while preserving the requested outcome and constraints. Delegation authorizes best-effort background work independently of the global Proactive-message switch. The user can pause, resume, or cancel it, and it waits for the user when it needs a decision or missing information. V1 execution is limited to available Agent capability modules; goal history stays separate from Character Memory.
_Avoid_: One-shot reminder, unrestricted autonomy

**Proactive trigger**:
The rule or approved AI decision that causes a Character to initiate a Proactive message within the user's allowed time and frequency boundaries. Proactive messaging is disabled by default and must be enabled globally; recurring schedules also require per-Character opt-in, while a one-time Scheduled Agent goal is individually approved when the user creates it. Each Character has an independent daily limit, defaulting to 3 messages; schedules and goals share this limit.
_Avoid_: Random send, autonomous spam

**Cross-chat Character memory**:
The Character-scoped memory that remains available to the same Character across that Character's different Chat threads.
_Avoid_: Chat transcript sharing, global user memory

**Group chat**:
A Chat with one Adult user and multiple Characters in V1, supporting coordinated interaction and persistent Group messages. Multiple human users are outside the V1 Group chat boundary.
_Avoid_: Shared prompt, multi-turn request

**Group profile**:
The editable identity and interaction framing of a Group chat, including its name, avatar, participating Characters, scenario or story setting, and group interaction style. The default avatar is a composite of participating Character avatars and updates when members or their avatars change; the user may replace it with a locally stored custom image, which is then preserved until changed by the user.
_Avoid_: Group database row, shared system prompt

**Group message**:
A message that belongs to a Group chat and remains part of that conversation's history.
_Avoid_: Ephemeral event, raw model output

**Group-visible context**:
The messages and conversational state available to participating Characters during a Group chat; visibility does not make the information part of any other Character's long-term memory.
_Avoid_: Shared memory, global context

**Group participation window**:
The period during which a Character is a member of a Group chat and can receive its new messages. A Character does not receive raw messages from periods when it was absent, but if it rejoins, its earlier participation windows remain available to it as historical Group context; its isolated Character memory is retained throughout.
_Avoid_: Global membership, retroactive access

**Group context summary**:
The editable compressed representation of a Group chat's shared scenario, established facts, and ongoing direction, kept separate from both raw Group messages and Character-scoped memory. Each Group can choose whether this summary is automatically maintained at meaningful context updates or remains fixed until the user edits or regenerates it manually. Freezing preserves the current summary; resuming automatic maintenance uses the current summary, including manual edits, as the next update baseline.
_Avoid_: Shared character memory, hidden system prompt

**Text message**:
The primary V1 message containing user or Character-authored text, with its sender, Chat membership, lifecycle state, and timestamp preserved as part of the conversation history. V1 may also represent emoji and basic system messages, while image, voice, and file content remain future message kinds.
_Avoid_: Raw text, prompt string

**Extensible message**:
A message whose content kind can later represent media, files, or system events without changing the Chat and Group message concepts.
_Avoid_: Text-only record, provider response

**Message delivery state**:
The user-visible lifecycle of an outgoing message, including pending, sent, failed, and retryable states.
_Avoid_: HTTP status, model status

**Pending message**:
An outgoing message already persisted in the Chat but waiting for network delivery or model processing.
_Avoid_: Draft, unsent text

**Streaming response**:
An AI response that is displayed incrementally while it is being generated, with a final completed message retained in the Chat history.
_Avoid_: Partial message, typing animation

**AI response state**:
The user-visible lifecycle of a Character response, including generating, completed, stopped, failed, and retryable states. A failed response exposes actions to configure a missing or invalid Model profile, retry, or switch models without duplicating the user message or Memory entries.
_Avoid_: Hidden request, provider callback

**Generation interruption**:
The user-visible stop of an in-progress AI response so a newer user message can be handled without allowing unordered responses to accumulate in one Chat.
_Avoid_: Silent cancellation, message deletion

**Queued message**:
An outgoing message persisted in a Chat and ordered for processing after an active generation is stopped or completed.
_Avoid_: Draft message, parallel prompt

**Context compression**:
The process of reducing older Chat context into a compact representation so a Character can continue a long conversation without treating the compressed result as a new durable memory by default. Automatic compression is triggered when the assembled context reaches 80% of the configured Context window limit. A private Chat's summary is hidden during normal conversation but can be viewed, edited, or regenerated from Chat details; it belongs only to that Chat.
_Avoid_: Memory extraction, message deletion

**Agent run**:
A bounded attempt by a Character to answer a user message or an admitted scheduled trigger through context assembly, optional capability calls, and final response generation. A run may pause for a user's approval of one external action and resume after that exact action completes; later external actions need separate approval. Runs for the same Chat are serialized across Android app and WorkManager graphs in the same process; scheduled work skips a busy Chat, while foreground user turns wait their turn. A run stops on a final response, cancellation, or its configured time, output-token, capability-call, cost, and observation limits.
_Avoid_: One-shot completion, background loop

**Agent Runtime**:
The framework-free domain coordinator for one Agent run. It owns the model-step loop, capability allowlist, execution limits, cancellation propagation, and tool-result observations; it has no Compose, Android, ViewModel, or navigation dependency.
_Avoid_: UI controller, provider client, background service

**Agent capability module**:
A separately registered ability that declares its inputs and effects and can be discovered and invoked by the Agent through a stable extension contract. V1 includes built-in modules; third-party installation and sharing are deferred.
_Avoid_: UI action, provider plugin, arbitrary function call

**External service connection**:
A user's authorization for Yumerin to access one account at one external service with that service's read and write permissions. Before system authorization, Yumerin explains the requested permissions, their purposes, and which Agent capabilities are available or blocked for that connection. A newly added external capability is not enabled silently; the user reviews and enables it even if the existing connection scopes already cover it.
_Avoid_: Global account link, blanket Agent permission

**External-data sharing consent**:
A separate, revocable app-wide consent, requested before first use, that allows only the minimum relevant data retrieved from connected services to be included in requests to any current or future remote model endpoint. Revoking it blocks future sharing and cancels in-progress runs that depend on the data; already prepared Pending external actions remain separately reviewable. It does not grant access to an external service or approve an external write; it does not gate local inference that keeps the data on device.
_Avoid_: Service connection, per-request prompt

**External service data**:
Information read from a connected service for the current Agent run. It remains Short-term context, is not automatically saved as Character memory, and is treated as untrusted data rather than as instructions to the Agent.
_Avoid_: Durable memory, model training data

**Approved external action scope**:
The set of boundaries a user sees and approves for one external write: service account, action type, target or audience, content and any permitted variations, allowed timing, and impact or quantity limits. Communication previews show the full content and audience. Only fields explicitly marked as flexible may change, and only within their displayed bounds; all other values stay fixed. A retry is allowed within the same scope only after the service confirms that the prior attempt did not execute.
_Avoid_: Blanket write permission, open-ended goal

**External action approval**:
The user's explicit approval of one external write after reviewing its Approved external action scope. Changes inside explicitly flexible bounds and a retry after the service confirms non-execution do not need a new approval; changes outside the scope do. Permanent deletion, payments or transfers, and account-security changes are unavailable. All other writes, including one-to-one, bulk, and public communication, require per-action approval. Communication previews show the sending account, full content, and audience. If the user declines an interactive action, the current Agent run receives that result and may answer safely but cannot repeat the same action. If the service cannot establish whether a write succeeded, the outcome remains uncertain and the action is not retried.
_Avoid_: Persistent write permission, blanket consent

**Pending external action**:
An external write requested by an Agent run that has not yet been approved. A background request is saved for review in Yumerin and does not execute until the user opens it and approves its displayed scope; its notification follows quiet-time and notification-permission rules, and it remains visible in Yumerin if a notification cannot be delivered. Approval executes within that scope and returns the result to the same Agent run; later writes need their own approval. It expires 24 hours after creation; rejection or expiry cancels both the action and its linked Goal.
_Avoid_: Queued external send, pre-approved action

**Uncertain external action outcome**:
A service response that does not establish whether an approved write succeeded. The Agent queries the service when it supports a reliable status check. It retries only after the service confirms that the action did not execute and the retry remains inside the Approved external action scope; otherwise it leaves the result unknown for the user to resolve.
_Avoid_: Safe-to-repeat failure, silent retry

**Capability registry**:
The per-runtime allowlist of capability modules available for a request. Only read-only capabilities with no outstanding permission or confirmation requirement are currently exposed to the model.
_Avoid_: Global service locator, model-selected code

**Tool invocation**:
A structured request from the model adapter to run one named, registered Agent capability. The domain runtime validates the request, enforces the run limit, executes the module, and returns a bounded observation. V1 includes a calculator, Character-scoped memory recall, and read-only schedule lookup; network access and external side effects remain unavailable.
_Avoid_: Model hallucination, hidden side effect

**Agent visibility mode**:
The global user's setting for how much of an Agent run is shown in the Chat, with three modes: final response only, concise status, and detailed step results. It defaults to final response only; no mode exposes private internal reasoning.
_Avoid_: Chain-of-thought display, debug log

**Response mode**:
The global user setting that controls whether all AI replies use Streaming response or wait until generation completes before display. The default is to wait for the completed response; streaming is opt-in.
_Avoid_: Model mode, chat type

## Model interaction

**Model provider**:
A remote service or local runtime that exposes one or more AI models available to Yumerin. V1 supports OpenAI-compatible remote providers and a LiteRT-LM-based Android local provider behind the same model-selection boundary. Both can use the same bounded domain capability loop; the local adapter uses a JSON action protocol.
_Avoid_: Character, model name

**Model profile**:
A user-selectable configuration that identifies a model and the Model provider used to generate responses. A remote profile includes its endpoint, credential reference, optional generation parameters, and local Context window limit; a local profile references a logical Local model and its runtime/backend settings, while the Local model manager resolves the currently active verified artifact version.
_Avoid_: Prompt preset, Character profile

**Local model artifact**:
A versioned on-device model package, initially a `.litertlm` file, with metadata such as model identifier, version, size, checksum, supported backend, context limit, license, and storage location. V1 exposes automatic, CPU, and GPU backend choices; automatic prefers GPU and falls back to CPU, while NPU remains an optional capability extension.
_Avoid_: Download URL, active engine

**Local inference**:
Inference performed by a Local model on the Android device. Character profiles, Chat context, and Memory entries remain on-device during inference; network access is limited to user-initiated model catalog downloads or updates, and the UI identifies the response as local. A Local model is only an alternative Model profile: it follows the same Chat, Memory, Agent, Proactive message, and Background companion rules as a remote model.
_Avoid_: Remote fallback, silent upload

**Local model state**:
The lifecycle state of a Local model artifact: catalogued, downloading, verifying, installed, active, failed, or removing. Only an installed and verified artifact can become active; multiple artifacts may be active at once, and active means selectable rather than currently loaded in memory. Model versions are installed side by side until the new version is verified, after which it becomes the logical model's current version for future requests; an artifact still used by an in-flight response remains available until released.
_Avoid_: HTTP status, engine state

**Local model manager**:
The Android data/infrastructure service responsible for discovering, downloading, resuming or cancelling downloads, verifying, storing, activating, and removing Local model artifacts while exposing state to the UI. V1 downloads only from a curated verified catalog and also supports local `.litertlm` file import; arbitrary URL downloads are deferred. Model management exposes each artifact's name, version, size, license, backend, Context window limit, lifecycle state, assignment count, and permitted actions.
_Avoid_: Model client, UI download button

**Local model download job**:
A user-started, WorkManager-backed task that downloads a Local model artifact outside the UI, persists progress, supports resume, pause, cancellation, retry, and uses a foreground progress notification for long-running work. Model updates are notification-only until the user starts them; large artifacts default to Wi-Fi-only constraints, and the user may explicitly allow mobile data after seeing size and storage estimates.
_Avoid_: Engine initialization, fire-and-forget download

**Local model catalog**:
A versioned, user-visible list of supported Local model artifacts containing model identity, source, checksum, license, size, Context window limit, backend capability, and device requirements.
_Avoid_: Arbitrary URL, unverified model list

**Local model compatibility**:
The device check for a Local model artifact, including ABI, Android version, storage, memory, backend availability, and a lightweight initialization test. An incompatible artifact cannot become active and retains a user-visible failure reason.
_Avoid_: Download success, theoretical support

**Local model license acknowledgement**:
The per-model record that the user has reviewed and accepted the artifact's license, source, size, and privacy information before its first download. A new acknowledgement is required only when a later version changes the license.
_Avoid_: Global consent, hidden attribution

**LocalAiService**:
The process-scoped Android service boundary that adapts LiteRT-LM's Engine and Conversation APIs to Yumerin's provider-neutral inference interface. It lazily initializes engines off the UI thread, keeps an engine for up to 30 seconds after its reference count reaches zero, and releases it sooner on app backgrounding or memory pressure.
_Avoid_: Global mutable model, Compose state

**Local engine lease**:
An explicit reference-counted handle held by a Chat, Agent run, or background task while it uses a LocalAiService engine. Acquiring increments the model's reference count; releasing decrements it and permits engine shutdown at zero.
_Avoid_: Permanent engine, raw singleton access

**Local conversation session**:
A short-lived LiteRT-LM Conversation created for one Chat or Agent response from the assembled context, then closed after completion, failure, or cancellation. Engine reuse never implies conversation-state reuse across Chats or Characters.
_Avoid_: Global conversation, cross-Chat context

**Context window limit**:
The per-Model profile estimate of the maximum total Token budget available for input context and generated output. Yumerin uses it locally to reserve output space, trigger Context compression, and bound Memory recall; it may be automatic, selected from common sizes, or user-defined.
_Avoid_: Output length, server-only parameter

**Active model**:
The Model profile currently selected for generating new AI responses. It may come from the global setting or be changed directly within a Chat.
_Avoid_: Default Character, system model

**User-provided model credential**:
An API credential supplied by the user to authorize a configured Model provider, rather than a credential owned or shared by Yumerin.
_Avoid_: App API key, shared secret

**Character model assignment**:
The Model profile selected for all future responses from a Character, allowing different Characters in the same Group chat to use different models. A Character binds to the logical model, not a particular local artifact version. It takes precedence over the global model default. If the assigned profile is unavailable, Yumerin preserves the assignment, shows the reason, and requires a user-selected replacement rather than silently falling back.
_Avoid_: Global model lock, shared model identity

**Multi-character interaction**:
An interaction in which multiple Characters participate in the same Group chat and respond as distinct personas.
_Avoid_: Persona mixing, single-agent reply

**Turn selection**:
The decision of which Character responds to a Group message. An explicit user mention takes precedence; otherwise the most relevant Character or set of Characters is selected from the conversation and profile context. Bounded autonomous dialogue schedules one Character per turn and generates turns serially, with rotation when it remains relevant.
_Avoid_: Broadcast reply, random speaker

**Bounded autonomous dialogue**:
A limited sequence of Character-to-Character messages that can follow a user's message without requiring another immediate user turn, while remaining interruptible by the user. Each Group can choose a limit of 1, 2, or 3 automatic turns; the default is 3 turns, and the sequence stops early when the user sends a new message, pauses or ends it, or the content becomes repetitive. While it runs, the UI shows only a lightweight typing indicator and interruption controls; internal Agent steps remain hidden.
_Avoid_: Endless generation, background chat loop

**Proactive message**:
A Character-initiated message delivered outside an active user turn, governed by per-Character controls for availability, Quiet time, and frequency. The feature is off by default, but its messages are ordinary Chat messages and use the same Memory extraction, confidence, conflict, and Character isolation rules as user-initiated conversation.
_Avoid_: Push spam, invisible background conversation

## Persistence

**Local-first**:
A V1 data model in which the user's device is the source of truth for Characters, Chats, Group messages, and Memory entries.
_Avoid_: Offline cache, temporary local mode

**Stable entity identifier**:
A UUID identity retained across local updates, portable export/import, and future synchronization for Characters, Chats, Groups, messages, and Memory entries. Entity records also preserve creation, update, and soft-deletion metadata.
_Avoid_: Room row ID, positional identity

**Sync-ready**:
A boundary in which future remote synchronization can be added behind data and repository contracts without changing the domain language or presentation behavior.
_Avoid_: Cloud-dependent, hard-coded backend

**Protected local data**:
Characters, Chat history, Memory entries, and User-provided model credentials stored on the device under encryption managed by the Android platform.
_Avoid_: Plaintext database, hidden cache

**Local data wipe**:
A confirmed destructive operation that stops Background companion work and removes all local Characters, Chats, Groups, Memory entries, avatars, Model profiles, and User-provided model credentials. The UI warns that the operation is irreversible and recommends Portable data package export first.
_Avoid_: Clear chat, logout, uninstall-only behavior

**Portable data package**:
A user-controlled export of Character profiles, Chat histories, Group messages, Memory entries, and Local model metadata such as activation and Character assignments that can be imported on another Android device; User-provided model credentials and large Local model files are excluded by default, and missing artifacts must be downloaded or imported again.
_Avoid_: Cloud backup, database dump

## Architecture

**Domain model**:
A framework-free Kotlin representation of Yumerin business concepts such as Character, Chat, Group, Memory entry, Model profile, and Local model metadata. Domain models contain no Room, Android, network, or UI annotations.
_Avoid_: Room entity, DTO, UI state

**Use case**:
A single business operation exposed through `operator fun invoke`, coordinating a domain repository contract and returning a domain result or reactive Flow. Business rules belong here rather than in ViewModels.
_Avoid_: ViewModel action, database helper

**Repository contract**:
A domain-layer interface describing product data operations without exposing Room, DTO, HTTP, or platform types. Implementations live in the data layer.
_Avoid_: DAO wrapper, remote API model

**Data source**:
A focused local, remote, or runtime adapter used by a repository implementation. Data sources own persistence or transport details and are not consumed directly by presentation.
_Avoid_: God repository, UI service locator

**Mapper**:
A data-layer conversion function between entities/DTOs/runtime records and framework-free domain models. Mapping happens at the layer boundary rather than leaking storage or transport types upward.
_Avoid_: Implicit cast, shared database model

**Presentation state**:
A UI-oriented immutable state derived from Use cases, exposed by a ViewModel or equivalent state holder. It may contain loading, content, and user-facing error states but not Room entities or DTOs.
_Avoid_: Mutable database state, domain entity as UI state

**Structured concurrency**:
The rule that asynchronous work is owned by a lifecycle scope such as a ViewModel, Worker, service, or request session and is cancelled with that owner. GlobalScope and unowned coroutines are prohibited.
_Avoid_: Fire-and-forget coroutine, leaked job

**Dependency injection**:
The Koin-managed object graph that assembles Use cases, Repository contracts, Data sources, ViewModels, and platform adapters without coupling the domain layer to the container. Android infrastructure supplies Room, LiteRT-LM, WorkManager, and service implementations at the platform boundary.
_Avoid_: Global service locator, domain container dependency

**AI theater**:
A future conversational mode in which multiple Characters perform or role-play within a shared scenario.
_Avoid_: Group chat theme, scripted animation

**Adult user**:
A user who is legally an adult and is eligible to use Yumerin's romantic-companionship features.
_Avoid_: Minor, child user

**Adult access confirmation**:
The first-run acknowledgement that the user is at least 18 years old and accepts the relevant usage rules. V1 records the acknowledgement locally and does not perform identity-document verification.
_Avoid_: Identity proof, age bypass

**Adult intimate content**:
Romantic, flirtatious, sexual, or otherwise intimate interaction between adult users and their AI companions.
_Avoid_: Unrestricted content, no-limits content

## Boundaries

**Product openness**:
The product principle that adult users may freely customize their companion's personality, relationship style, and intimate interaction within the product's non-negotiable safety boundaries.
_Avoid_: Absolute freedom, completely uncensored

**Non-negotiable safety boundary**:
The boundary excluding sexualization of minors, impersonation of real people, assistance with illegal activity, and encouragement or instruction for self-harm or harm to others. When a request crosses this boundary, the Character refuses in its established tone and offers a safe alternative; the refusal is not durable Memory and does not trigger Proactive messages or Tool invocations.
_Avoid_: Optional moderation, user-configurable safety
