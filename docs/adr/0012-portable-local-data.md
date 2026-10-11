---
status: accepted
---

# Support portable local data without exporting credentials

V1 will support exporting and importing a Portable data package so users can move Characters, Chat histories, Group messages, and Memory entries between Android devices without an account or cloud sync. User-provided model credentials are excluded by default; an encrypted export may be offered for the non-secret product data.

## Consequences

- The export format needs a version marker and stable identifiers for future migrations.
- Exported Characters, Chats, Groups, messages, and Memory entries retain their Stable entity identifier and lifecycle timestamps.
- Local model metadata, activation state, and Character assignments may be exported, but large model binaries are excluded and are marked for re-download or re-import after import.
- Import must not silently move API Keys or other secrets.
- Import uses a preview-before-merge flow and must not delete existing local data by default.
- Merge conflicts need explicit choices such as keep local, use imported, or import as a new record.
