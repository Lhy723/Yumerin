---
status: accepted
---

# Use user-provided model credentials in V1

V1 will use User-provided model credentials for configured Model providers. This preserves the Local-first product boundary and avoids requiring an account, shared backend billing, or a cloud credential service before the core companion experience is validated; credentials must remain local to the Android device and be handled as sensitive data.

## Consequences

- Users are responsible for provider access and usage costs.
- The model settings flow must make credential ownership and usage explicit.
- A future Yumerin-hosted model service would be a separate product and security decision, not an implicit migration.
