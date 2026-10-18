package com.lhy.yumerin.domain.voice

/**
 * Contracts for the future voice channel. The text channel already produces
 * [com.lhy.yumerin.core.model.AgentRunEvent] values with an explicit
 * [com.lhy.yumerin.core.model.AgentRunInterrupted] event; a voice pipeline is
 * a channel adapter around the same Agent run, not a second brain.
 *
 * The state machine is IDLE -> LISTENING -> THINKING -> SPEAKING. When the
 * user starts speaking during SPEAKING, the client stops local playback
 * immediately and raises an interrupt; the run cancels its model and TTS
 * streams and truncates history to what was actually played.
 */
enum class VoiceChannelState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

/** Why a speaking turn ended: finished, or cut by the user. */
enum class VoiceUtteranceEnd {
    COMPLETED,
    INTERRUPTED_BY_USER
}

/**
 * Per-sentence playback progress. Interruption truncation needs to know how
 * much of the reply was actually heard before deciding where history ends.
 */
data class VoicePlaybackProgress(
    val sentenceIndex: Int,
    val sentenceCount: Int,
    val spokenText: String
)

/**
 * Platform voice loop contract. Implementations own VAD, streaming ASR,
 * turn detection, streaming TTS, playback, and acoustic echo handling; the
 * domain layer only sees transcripts, playback progress, and interrupts.
 */
interface VoiceEngine {
    val state: VoiceChannelState

    /** Starts a listening turn. */
    suspend fun startListening()

    /**
     * Requests an immediate barge-in: stop playback locally, cancel in-flight
     * synthesis, and report [VoiceUtteranceEnd.INTERRUPTED_BY_USER] for the
     * active utterance. Returns false when nothing was speaking.
     */
    suspend fun interrupt(): Boolean

    /** Releases audio resources; safe to call in any state. */
    suspend fun shutdown()
}

/** Callbacks a voice channel surfaces back to the app layer. */
interface VoiceEngineCallbacks {
    /** Partial or final ASR transcript of the user's speech. */
    fun onUserTranscript(text: String, isFinal: Boolean)

    /** Sentence-level playback progress while the companion speaks. */
    fun onPlaybackProgress(progress: VoicePlaybackProgress)

    fun onStateChange(state: VoiceChannelState)

    /** An utterance ended naturally or by interruption. */
    fun onUtteranceEnd(end: VoiceUtteranceEnd)
}
