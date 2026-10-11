---
status: accepted
---

# Make proactive messages configurable and bounded

Characters may initiate Proactive messages, but the behavior must be configurable per Character and constrained by Quiet time and frequency controls. Proactive messaging is disabled by default and must be enabled globally or for an individual Character. Each Character has an independent daily limit, defaulting to 3 messages; user-created schedules and AI-approved triggers share this limit. V1 uses device-local Quiet time from 23:00 to 08:00 by default; messages created during that window remain persisted and unread without emitting a notification. Yumerin will not silently run ongoing background conversations that users cannot see or stop; proactive behavior is a user-visible part of the relationship rather than an invisible generation loop.

Proactive messages are persisted before notification delivery and follow the ordinary Chat and Memory rules. Their origin outside an active user turn does not create a separate Memory policy.
