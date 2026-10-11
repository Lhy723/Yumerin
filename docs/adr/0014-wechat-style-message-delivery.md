---
status: accepted
---

# Persist messages before delivery and expose their lifecycle

Yumerin will follow a WeChat-style local-first message flow: an outgoing message is persisted to its Chat immediately, rendered optimistically, and tracked through a Message delivery state such as pending, sent, failed, or retryable. Network recovery may retry Pending messages, while users can manually retry or cancel failed sends; the local Chat history remains the source of truth for what the user attempted to send.

## Consequences

- Message persistence and remote model delivery are separate operations.
- The UI must show failures and retries without silently duplicating messages.
- AI response generation uses a visible AI response state with failure and recovery actions.
- Missing, invalid, or unreachable model configuration leaves the persisted user message intact and offers configuration, retry, or model-switch actions.
- Retrying a failed AI response must not duplicate the user's original message or create duplicate Memory entries.
- A newer user message may trigger Generation interruption; responses are processed in Chat order rather than emitted as unordered parallel replies.
- Streaming response is controlled by one global Response mode setting and is disabled by default; when disabled, the completed AI response is persisted and shown as one message.
