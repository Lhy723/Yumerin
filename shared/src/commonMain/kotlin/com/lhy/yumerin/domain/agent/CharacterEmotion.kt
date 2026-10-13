package com.lhy.yumerin.domain.agent

import com.lhy.yumerin.core.model.AgentEmotionalContext
import com.lhy.yumerin.core.model.CharacterRuntimeState
import com.lhy.yumerin.core.model.EntityId
import com.lhy.yumerin.domain.repository.CharacterRuntimeStateRepository
import kotlin.math.exp

enum class UserAffectSignal {
    DISTRESSED,
    FRUSTRATED,
    POSITIVE,
    AFFECTIONATE,
    NEUTRAL
}

data class AppraisedAffect(
    val signal: UserAffectSignal,
    val targetValence: Float,
    val targetArousal: Float,
    val trustDelta: Float,
    val closenessDelta: Float
)

/**
 * Lightweight interaction appraisal. It classifies only the current message,
 * never diagnoses the user or writes a permanent user-emotion record.
 */
object EmotionAppraiser {
    private val distressed = listOf("难过", "伤心", "失落", "孤独", "崩溃", "焦虑", "害怕", "担心", "压力好大", "想哭", "sad", "lonely", "anxious", "afraid", "scared", "overwhelmed")
    private val frustrated = listOf("生气", "烦死", "愤怒", "受够了", "气死", "讨厌", "无语", "angry", "frustrated", "annoyed", "furious", "fed up")
    private val affectionate = listOf("爱你", "喜欢你", "想你", "抱抱", "亲亲", "宝贝", "love you", "miss you", "sweetheart", "hug")
    private val positive = listOf("开心", "高兴", "太好了", "谢谢", "感谢", "棒", "成功了", "喜欢", "happy", "glad", "great", "thanks", "thank you", "wonderful")

    fun appraise(text: String): AppraisedAffect {
        val normalized = text.lowercase()
        fun score(words: List<String>) = words.count { containsUnnegated(normalized, it) }
        val distressedScore = score(distressed)
        val frustratedScore = score(frustrated)
        val affectionateScore = score(affectionate)
        val positiveScore = score(positive)
        return when {
            frustratedScore > 0 && frustratedScore >= distressedScore -> AppraisedAffect(
                UserAffectSignal.FRUSTRATED, -0.22f, 0.72f, 0f, 0f
            )
            distressedScore > 0 -> AppraisedAffect(
                UserAffectSignal.DISTRESSED, -0.3f, 0.62f,
                trustDelta = if (listOf("我担心", "我害怕", "我想告诉你", "i'm worried", "i am worried").any(normalized::contains)) 0.008f else 0f,
                closenessDelta = if (listOf("我担心", "我害怕", "我想告诉你", "i'm worried", "i am worried").any(normalized::contains)) 0.006f else 0f
            )
            affectionateScore > 0 -> AppraisedAffect(
                UserAffectSignal.AFFECTIONATE, 0.48f, 0.46f, trustDelta = 0.012f, closenessDelta = 0.018f
            )
            positiveScore > 0 -> AppraisedAffect(
                UserAffectSignal.POSITIVE, 0.32f, 0.38f, trustDelta = 0.006f, closenessDelta = 0.01f
            )
            else -> AppraisedAffect(UserAffectSignal.NEUTRAL, 0f, 0.25f, 0f, 0f)
        }
    }

    private fun containsUnnegated(text: String, phrase: String): Boolean {
        var searchFrom = 0
        while (searchFrom < text.length) {
            val index = text.indexOf(phrase, startIndex = searchFrom)
            if (index < 0) return false
            val prefix = text.substring((index - NEGATION_LOOKBACK).coerceAtLeast(0), index)
            val negated = NEGATIONS.any { prefix.endsWith(it) || prefix.contains(it) }
            if (!negated) return true
            searchFrom = index + phrase.length
        }
        return false
    }

    private val NEGATIONS = listOf("并不", "不太", "不怎么", "没有", "不是", "不", "没", "未", "not ", "never ", "don't ", "dont ", "isn't ", "is not ")
    private const val NEGATION_LOOKBACK = 12
}

data class PreparedCharacterTurn(
    val state: CharacterRuntimeState,
    val emotionalContext: AgentEmotionalContext
)

class CharacterStateCoordinator(
    private val repository: CharacterRuntimeStateRepository
) {
    suspend fun prepare(
        characterId: EntityId,
        chatId: EntityId,
        sourceMessageId: EntityId?,
        userText: String?,
        nowMillis: Long
    ): PreparedCharacterTurn {
        val persisted = repository.getState(characterId) ?: CharacterRuntimeState(characterId = characterId)
        val decayed = decay(normalize(persisted), nowMillis)
        val appraisal = userText?.let(EmotionAppraiser::appraise)
        val next = if (appraisal == null) {
            decayed.copy(
                sourceChatId = chatId,
                sourceMessageId = sourceMessageId ?: decayed.sourceMessageId,
                updatedAt = nowMillis
            )
        } else {
            decayed.copy(
                moodValence = blend(decayed.moodValence, appraisal.targetValence, 0.34f),
                moodArousal = blend(decayed.moodArousal, appraisal.targetArousal, 0.3f),
                trust = (decayed.trust + appraisal.trustDelta).coerceIn(0f, 1f),
                closeness = (decayed.closeness + appraisal.closenessDelta).coerceIn(0f, 1f),
                sourceChatId = chatId,
                sourceMessageId = sourceMessageId,
                updatedAt = nowMillis
            )
        }
        val signal = appraisal?.signal ?: UserAffectSignal.NEUTRAL
        return PreparedCharacterTurn(next, toPromptContext(next, signal))
    }

    suspend fun commit(
        prepared: PreparedCharacterTurn,
        nowMillis: Long,
        completedMessageId: EntityId? = null
    ) {
        repository.saveState(
            prepared.state.copy(
                completedInteractions = prepared.state.completedInteractions + 1,
                lastInteractionAt = nowMillis,
                sourceMessageId = completedMessageId ?: prepared.state.sourceMessageId,
                updatedAt = nowMillis
            )
        )
    }

    private fun decay(state: CharacterRuntimeState, nowMillis: Long): CharacterRuntimeState {
        val elapsed = (nowMillis - state.updatedAt).coerceAtLeast(0L)
        if (elapsed == 0L) return state
        val retention = exp(-elapsed.toDouble() / MOOD_DECAY_TIME_CONSTANT_MILLIS).toFloat().coerceIn(0f, 1f)
        return state.copy(
            moodValence = state.moodValence * retention,
            moodArousal = NEUTRAL_AROUSAL + (state.moodArousal - NEUTRAL_AROUSAL) * retention
        )
    }

    private fun normalize(state: CharacterRuntimeState): CharacterRuntimeState = state.copy(
        moodValence = finiteOr(state.moodValence, 0f).coerceIn(-1f, 1f),
        moodArousal = finiteOr(state.moodArousal, NEUTRAL_AROUSAL).coerceIn(0f, 1f),
        trust = finiteOr(state.trust, 0.5f).coerceIn(0f, 1f),
        closeness = finiteOr(state.closeness, 0.5f).coerceIn(0f, 1f),
        completedInteractions = state.completedInteractions.coerceAtLeast(0)
    )

    private fun finiteOr(value: Float, fallback: Float): Float = if (value.isFinite()) value else fallback

    private fun toPromptContext(state: CharacterRuntimeState, signal: UserAffectSignal) = AgentEmotionalContext(
        characterMood = when {
            state.moodValence > 0.36f && state.moodArousal > 0.55f -> "bright and energetic"
            state.moodValence > 0.18f -> "warm and relaxed"
            state.moodValence < -0.24f -> "concerned and low-key"
            state.moodArousal > 0.68f -> "alert and attentive"
            else -> "calm and emotionally steady"
        },
        relationshipContinuity = when {
            state.trust < 0.28f || state.closeness < 0.28f -> "the relationship is still getting to know each other; respect distance"
            state.trust > 0.76f && state.closeness > 0.76f -> "the relationship feels familiar and trusting"
            else -> "the relationship is growing familiar; keep warmth balanced"
        },
        userAffect = signal.name.lowercase().replace('_', ' '),
        responseGuidance = when (signal) {
            UserAffectSignal.DISTRESSED -> "Acknowledge the feeling before solving. Do not diagnose, minimize, or pressure the user. Offer one small next step only when useful."
            UserAffectSignal.FRUSTRATED -> "Stay patient, reflect the concrete concern, and focus on the requested action."
            UserAffectSignal.POSITIVE -> "Share the warmth naturally without exaggerating or making assumptions about the relationship."
            UserAffectSignal.AFFECTIONATE -> "Respond warmly while honoring the user's stated boundaries and established character style."
            UserAffectSignal.NEUTRAL -> "Follow the user's intent and avoid forcing an emotional interpretation."
        }
    )

    private fun blend(current: Float, target: Float, weight: Float) =
        (current * (1f - weight) + target * weight).coerceIn(-1f, 1f)

    private companion object {
        const val NEUTRAL_AROUSAL = 0.25f
        const val MOOD_DECAY_TIME_CONSTANT_MILLIS = 12.0 * 60 * 60 * 1_000
    }
}
