---
status: accepted
---

# Bound autonomous multi-character dialogue

Group chats may continue briefly through Bounded autonomous dialogue after a user's message. The sequence must be limited and interruptible, giving the product room to support AI theater and role-play scenes without allowing characters to generate an unbounded conversation, overwhelm the user, or consume resources indefinitely. Each Group exposes a 1-, 2-, or 3-turn limit, defaulting to 3 turns, and the sequence stops early when the user sends a new message, pauses or ends it, or the content becomes repetitive.

## Consequences

- Turn selection must distinguish a user-directed reply from a short autonomous scene.
- Each autonomous turn selects and generates one Character response serially; parallel Character generations are out of scope for V1.
- The user needs visible controls to interrupt, pause, or end the scene.
- Runtime status is represented by a lightweight typing indicator and controls, not by exposing internal reasoning or tool steps.
- Future AI theater can build on the same multi-character interaction model instead of inventing a separate conversation primitive.
