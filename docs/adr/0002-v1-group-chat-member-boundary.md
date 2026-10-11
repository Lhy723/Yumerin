---
status: accepted
---

# Keep V1 group chats single-user and multi-character

V1 Group chats will contain one human user and multiple Characters. This keeps the first multi-character experience focused on persistent group messages, distinct personas, and isolated Character memory without introducing multi-user identity, permissions, moderation, or cross-user privacy concerns. Multiple human users and broader virtual communities remain future extensions rather than implicit capabilities of the V1 group model.

Characters participate through one or more Group participation windows: newly added Characters do not receive raw messages from before they joined, and removed Characters stop receiving new messages while retaining their isolated Character memory. If a Character rejoins, it can access the raw messages from its earlier participation windows, but it does not receive or replay messages created while it was absent; the current Group context summary remains available as shared Group context.
