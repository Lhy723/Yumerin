---
status: accepted
---

# Separate chat history from Character memory

A Character may participate in multiple Chat threads. Each thread owns its message history and Short-term context, while the Character's durable memory is shared across that Character's future threads with the same user. This preserves continuity without treating every Chat transcript as one undifferentiated context.

## Consequences

- Context retrieval must combine thread-local history with Character-scoped durable memory.
- Deleting or archiving a Chat thread must have an explicit policy for any Memory entries derived from it.
- A Character's memory remains isolated even when the Character participates in both private and Group chats.
- V1 deletion removes the Chat thread's history and Short-term context but leaves existing Memory entries intact; Memory management handles their later deletion.
- Chat deletion does not delete the participating Character or Group profile.
- Memory entries should retain enough Memory provenance for the user to understand where they came from.
