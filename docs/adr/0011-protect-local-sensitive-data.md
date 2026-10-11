---
status: accepted
---

# Encrypt sensitive local data by default

V1 will treat Chat history, Characters, Memory entries, and User-provided model credentials as Protected local data. The Android implementation must encrypt the Room database and credential storage using keys protected by Android Keystore; clearing app data or uninstalling the app must also remove the local encryption material.

## Consequences

- Encryption is part of the Android data layer, not the domain model.
- API credentials must never be written to logs, unencrypted exports, or analytics.
- Any future backup, export, or sync feature needs an explicit user-visible protection policy.
- V1 exposes a confirmed Local data wipe that stops background work and clears all local product data, including credentials; the UI recommends exporting a Portable data package before proceeding.
- V1 does not add a separate in-app lock or biometric gate; the product relies on Android device security for access control.
