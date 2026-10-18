package com.lhy.yumerin.domain.persona

/**
 * Pure text adaptation between chat prose and speech delivery.
 *
 * Card-authored text often carries stage directions (`*微笑*`), markdown, and
 * example markers (`<START>`) that a TTS engine would read aloud literally.
 * This adapter normalizes text for voice output and applies a spoken-style
 * prompt variant so replies come back short and colloquial.
 */
object SpeechTextAdapter {
    /** Prompt suffix applied to the persona layer when the channel is voice. */
    const val VOICE_PROMPT_VARIANT: String =
        "This reply will be spoken aloud. Use short, colloquial sentences; no markdown, no stage directions in asterisks, no emoji, no lists. "

    /** Removes or rewrites content that must not be spoken. */
    fun toSpeechText(text: String): String {
        if (text.isEmpty()) return text
        return text
            .replace(ACTION_BLOCK, ::rewriteAction)
            .replace(MARKDOWN_BOLD_OR_ITALIC, "$1")
            .replace(MARKDOWN_HEADER, "$1")
            .replace(MARKDOWN_CODE_BLOCK, " ")
            .replace(MARKDOWN_LINK, "$1")
            .replace(EMOJI_RUN, "")
            .replace(NEWLINES, " ")
            .replace(SPACES, " ")
            .trim()
    }

    /** Strips `<START>` example separators used by community cards. */
    fun stripExampleMarkers(text: String): String = text
        .replace(EXAMPLE_MARKER, "")
        .trim()

    /**
     * Rewrites an action block like `*微笑*` into a spoken cue or drops it.
     * Returns the spoken replacement; an empty replacement removes the block.
     */
    private fun rewriteAction(match: MatchResult): String {
        val action = match.groupValues[1].trim()
        return when {
            action.isEmpty() -> ""
            ACTION_TO_CUE.containsKey(action) -> ACTION_TO_CUE.getValue(action)
            else -> ""
        }
    }

    private val ACTION_TO_CUE = mapOf(
        "微笑" to "（轻笑）",
        "轻笑" to "（轻笑）",
        "叹气" to "（叹气）",
        "点头" to "（点头）"
    )

    private val ACTION_BLOCK = Regex("\\*([^*\\n]{1,40})\\*")
    private val MARKDOWN_BOLD_OR_ITALIC = Regex("(\\*\\*|__)(.*?)\\1")
    private val MARKDOWN_HEADER = Regex("(?m)^#{1,6}\\s*(.*)$")
    private val MARKDOWN_CODE_BLOCK = Regex("```.*?```", RegexOption.DOT_MATCHES_ALL)
    private val MARKDOWN_LINK = Regex("\\[([^\\]]*)\\]\\([^)]*\\)")
    private val EMOJI_RUN = Regex("[\\u{1F300}-\\u{1FAFF}\\u{2600}-\\u{27BF}]+")
    private val EXAMPLE_MARKER = Regex("<START>", RegexOption.IGNORE_CASE)
    private val NEWLINES = Regex("[\\r\\n]+")
    private val SPACES = Regex("\\s{2,}")
}
