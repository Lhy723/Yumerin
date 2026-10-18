package com.lhy.yumerin.core.model

/**
 * A message content part. V1 chats stay text-first; image parts are the first
 * supported attachment, with audio and file parts reserved for the voice and
 * task channels. Raw bytes live in app-private storage; a part carries only
 * its reference so portable packages and sync never embed large blobs.
 */
sealed interface MessagePart {
    val uri: String?

    data class Text(val text: String) : MessagePart {
        override val uri: String? = null
    }

    /** Locally stored image reference (app-private file or content URI). */
    data class Image(override val uri: String) : MessagePart

    /** Locally stored audio reference with optional duration metadata. */
    data class Audio(override val uri: String, val durationMillis: Long? = null) : MessagePart

    /** Generic attachment with its display name and optional size. */
    data class File(override val uri: String, val name: String, val sizeBytes: Long? = null) : MessagePart
}

enum class ModelCapability {
    /** The model accepts image input; images can be passed through directly. */
    VISION,
    /** The model accepts audio input. */
    AUDIO_IN,
    /** The model supports provider tool/function calling. */
    TOOL_CALLS,
    /** The model supports token streaming. */
    STREAMING
}
