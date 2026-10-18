---
status: accepted
---

# Split persona voice from executor reliability in prompt assembly

A run uses two prompt layers. The **persona layer** carries the full character voice (name, relationship framing, setting, personality, tone, backstory, interaction style, greeting/speaking examples, creator system prompt) and produces conversational replies. The **executor layer** is a neutral task framing with minimal character facts that runs the capability-call loop. `AgentRuntime` picks the persona layer for pure conversational turns and the executor layer whenever capabilities are in play; observations flow back through the shared conversation so the final answer still has them as evidence.

A pluggable `PersonaResultRewriter` (default: identity) re-voices executor-layer results — delegated-goal reports, for example, go out in the character's tone after the neutral layer finishes the work.

## Boundaries

- `domain/persona/PromptAssembler` owns both layers and the slot pipeline; `AgentRuntime` selects the layer from capability availability, not from UI state.
- Persona prompts follow the slot order: system rules -> persona (world info around it) -> memory brief -> relationship/emotional state -> turn context -> disclosure policy -> history -> post-history instructions. `system_prompt` with `{{original}}` wraps Yumerin's rules; `post_history_instructions` lands after history.
- Token budgeting is per-assembly: characters/4 estimate with a pluggable `TokenEstimator`, and slot budgets (characters + estimated tokens) are exposed for the debug panel.
- `ContextAssembler` remains as a compatibility facade over `PromptAssembler` until all adapters migrate.

## Rationale

- Long theatrical persona prompts degrade tool-call reliability: models under character prose produce malformed calls and hallucinate completion. A neutral framing with strict protocol text keeps the loop reliable while the persona rewrite keeps user-facing output in voice.
- SillyTavern-style prompt assembly (slots, macro expansion, world-info injection, post-history instructions) is the community's proven shape for persona fidelity; adopting the same pipeline makes card import (ADR 0027) map mechanically.

## Consequences

- Adding a persona field or slot is localized to `PromptAssembler`; adapters and use cases consume the assembled conversation unchanged.
- The executor layer intentionally omits tone-setting prose beyond a one-line persona summary; if a task needs full persona context (rare), the rewriter's inputs still carry the original request for inspection.
- Memory recall trace (`usedMemoryIds`) is computed by the assembler and returned with replies, unchanged in contract.
