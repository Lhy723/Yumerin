package com.lhy.yumerin.core.model

/**
 * Yumerin's internal persona asset: a superset of the community character-card
 * schema (v2/v3) plus product-specific fields. Importers map foreign cards into
 * this shape; exporters map it back. Fields unknown to Yumerin are preserved
 * verbatim in [rawExtras] so a card round-trips without losing extension data.
 *
 * The field names mirror the public card schema so mapping stays mechanical.
 * Card content is untrusted input: it never grants tool permissions and is
 * rendered as data, not as instructions to the platform.
 */
data class PersonaPackage(
    val name: String,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMes: String = "",
    val mesExample: String = "",
    val creatorNotes: String = "",
    /** Creator-authored system prompt; `{{original}}` marks where Yumerin's own system rules attach. */
    val systemPrompt: String? = null,
    /** Instructions injected after the chat history. */
    val postHistoryInstructions: String? = null,
    /** Card-embedded world info (lorebook) shipped with the persona. */
    val characterBook: WorldInfoBook? = null,
    /** Yumerin extension: preferred TTS voice and per-emotion settings. */
    val voiceConfig: PersonaVoiceConfig? = null,
    /** Yumerin extension: templates describing how the relationship should evolve. */
    val relationshipStateTemplate: String? = null,
    /** Yumerin extension: the persona's own behavioral boundaries, applied inside the safety envelope. */
    val behavioralBoundaries: String? = null,
    val personaVersion: String = DEFAULT_PERSONA_VERSION,
    /** Original JSON text of fields this class does not model; preserved for lossless export. */
    val rawExtras: String = ""
) {
    companion object {
        const val DEFAULT_PERSONA_VERSION = "1"
    }
}

/** Yumerin voice extension of a persona; consumed by the speech text adapter and future TTS. */
data class PersonaVoiceConfig(
    val voiceId: String? = null,
    val speakingRate: Double? = null,
    val pitch: Double? = null,
    /** Emotion name to voice-style descriptor, e.g. "happy" -> "gentle, bright". */
    val emotionStyles: Map<String, String> = emptyMap()
)

data class WorldInfoBook(
    val name: String = "",
    val entries: List<WorldInfoEntry> = emptyList(),
    /** Original JSON text of unrecognized book fields, kept for lossless export. */
    val rawExtras: String = ""
)

enum class WorldInfoSelectiveLogic {
    /** Secondary keys: at least one matches. */
    AND_ANY,
    /** Secondary keys: not all of them match. */
    NOT_ALL,
    /** Secondary keys: none match. */
    NOT_ANY,
    /** Secondary keys: all of them match. */
    AND_ALL
}

enum class WorldInfoPosition {
    /** Injected before the persona/personality block. */
    BEFORE_PERSONA,
    /** Injected after the persona block. */
    AFTER_PERSONA,
    /** Injected into the chat history at [WorldInfoEntry.depth] messages from the end. */
    AT_DEPTH
}

data class WorldInfoEntry(
    val uid: Int,
    val key: List<String> = emptyList(),
    val keysecondary: List<String> = emptyList(),
    val selectiveLogic: WorldInfoSelectiveLogic = WorldInfoSelectiveLogic.AND_ANY,
    val content: String = "",
    val comment: String = "",
    val constant: Boolean = false,
    val enabled: Boolean = true,
    val position: WorldInfoPosition = WorldInfoPosition.AFTER_PERSONA,
    /** Chat depth for [WorldInfoPosition.AT_DEPTH]; counts messages back from the newest. */
    val depth: Int = 4,
    /** Lower order injects earlier inside the same position. */
    val order: Int = 0,
    /** Activation probability in percent; null or 100 means always. */
    val useProbability: Int? = null,
    /** Inclusion group: when several enabled entries share a group, one winner is drawn by [groupWeight]. */
    val group: String? = null,
    val groupWeight: Int = 100,
    /** When false, content activated by this entry can recursively trigger further entries. */
    val preventRecursion: Boolean = false,
    /** Entries excluded from consideration when this entry activates. */
    val excludeRecursionIds: List<Int> = emptyList(),
    val extensions: Map<String, String> = emptyMap()
)
