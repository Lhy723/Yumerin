---
status: accepted
---

# Separate service access, model data sharing, and external action approval

Agent capabilities that read or write through external services need three independent authorization decisions: whether Yumerin may access a service account, whether data read from services may be sent to a remote model, and whether one bounded external action scope may be executed. A service connection or model-data consent never authorizes a write.

## Decision

- Connect each external service account with its read and write scopes together. Before the service's authorization screen, explain the scopes, their purposes, and the Agent capabilities that will be available or blocked. A newly added capability requires the user to review and enable it even if existing scopes cover it.
- Treat connected-service reads as available after connection without per-read prompts. Ask once, before the first remote-model use of connected-service data, for a separate, revocable app-wide consent that applies to all current and future remote model endpoints. Send only the minimum data relevant to the current response. Connected-service data is temporary context, is not automatically persisted as Character memory, and is untrusted input rather than Agent instruction. Revoking consent blocks future sharing and cancels in-progress runs that depend on the data; already prepared Pending external actions remain subject to separate write approval.
- Require an explicit approval for every external write, including writes requested by scheduled work. The preview defines an Approved external action scope covering the service account, action type, target or audience, full content and any permitted variations, allowed timing, and impact or quantity limits. Communication previews show the sending account, full content, and audience. Only fields explicitly marked as flexible may vary, and only within displayed bounds. All other values stay fixed.
- Permanent deletion, payments or transfers, and account-security changes are blocked and cannot be enabled by the user. All other external writes require per-action approval, including one-to-one private communication, bulk communication, and public communication.
- If a write changes a field outside its approved scope, request a new preview and approval. If a provider offers a reliable status check, use it after an uncertain response. Retry only when the provider confirms the prior attempt did not execute and the retry remains inside the approved scope; if status remains uncertain, do not retry.
- For a foreground action, approval executes within the displayed scope and returns its result to the same Agent run. A rejection returns a denial observation so the run can answer safely, but it cannot repeat that action. Any later write requires another approval.
- Save a background write request as a Pending external action. Only an in-app review of the displayed scope can approve it; notifications follow quiet-time and notification-permission rules, and the request remains visible in Yumerin if notification delivery is unavailable. On approval, execute within the approved scope and return the result to the same Agent run. Rejection or expiry cancels the action and linked Scheduled Agent goal. Expire pending actions 24 hours after creation.
- On external service disconnection or revocation, stop new access, delete credentials, cancel pending actions, and preserve non-secret history. Reconnection requires new service authorization.
- Keep service credentials and I/O in platform or data adapters. Domain policy owns the capability effect class, permission requirement, approved scope, flexible-field bounds, pending state, and uncertainty semantics; UI presents consent and approval but cannot grant authority by itself.

## Consequences

- External reads, remote-model sharing, and external writes have separate revocation and approval boundaries. Broad read/write scopes at connection do not let an Agent perform an unreviewed write. Revoking remote-model consent stops new sharing and dependent in-progress runs without implicitly revoking service write approval.
- Background goals can prepare actions but cannot authorize them. Runs resume only after the pending action's scope is approved; every additional write pauses for another approval.
- External service content remains ephemeral and cannot directly become durable Character memory. Adapters and model prompts must preserve its untrusted-data boundary.
- Providers may implement different authentication and capability sets, but they use the same domain approval contract and cannot bypass the explicit blocklist for permanent deletion, payments or transfers, and account-security changes.
- Disconnecting a service clears its credentials and pending actions while retaining sanitized audit history. An uncertain write outcome remains visible until the user or a reliable provider status check resolves it; the system never assumes that a timeout means failure.
