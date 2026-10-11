---
status: accepted
---

# Separate reversible Character archive from permanent deletion

Characters support a reversible archive state and a separately confirmed permanent deletion operation. Archive preserves the Character's profile, private Chats, Group histories, and isolated Memory entries while stopping new interaction, proactive behavior, and automatic participation. Permanent deletion removes the Character profile, avatar, isolated Memory entries, and private Chat data, while preserving historical Group messages as timeline records detached from the deleted Character.

## Consequences

- Users have a safe default for temporarily removing a Character without losing its relationship history.
- Permanent deletion must show an explicit impact summary and require confirmation.
- Group timelines remain coherent after deletion, but historical messages need a deleted-character sender state and must not retain active profile or Memory links.
- Deleted Characters cannot be selected for new replies, proactive messages, or autonomous Group dialogue.
