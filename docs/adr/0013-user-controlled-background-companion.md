---
status: accepted
---

# Make background companionship user-controlled

Yumerin's Background companion is disabled by default and becomes continuous only after the user explicitly enables it. When enabled on Android, a user-visible Foreground Service maintains the active mode with a non-dismissible notification that exposes pause and stop actions; when disabled, WorkManager handles low-frequency scheduled tasks, and BootReceiver restores only tasks that were previously enabled by the user. Notification channels are initialized as part of the Android application infrastructure.

## Consequences

- The app must expose the current Background companion status and a clear pause/stop action.
- Foreground Service, periodic work, boot restoration, and notification delivery are infrastructure concerns behind domain-level schedules and preferences.
- Proactive behavior must still obey global notifications, Chat mute, quiet-time, and frequency controls.
- User-created Companion schedules and approved AI Proactive triggers are the only sources of background initiation in V1.
- The Android notification permission is requested when a notification-dependent feature is first enabled, not unconditionally during first launch.
- If notification permission is denied, ordinary Chat remains usable but Background companion cannot start until permission is restored in system settings.
- A Proactive message is persisted to its Chat before a Companion notification is emitted; notifications are entry points, not the source of truth.
- Companion notifications show a Notification preview by default; users can still control notification permission and individual Chat mute behavior.
- Android notifications are separated into channels for chat messages, companionship reminders, Background companion status, and Local model download progress.
