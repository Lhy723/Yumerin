package com.lhy.yumerin.data.room

import com.lhy.yumerin.core.model.CharacterRuntimeState
import com.lhy.yumerin.core.model.OpenCommitment

private const val LIST_SEPARATOR = "\u001F"
private const val COMMITMENT_FIELD_SEPARATOR = "|"

internal fun CharacterRuntimeStateEntity.toDomain() = CharacterRuntimeState(
    characterId = characterId,
    moodValence = moodValence,
    moodArousal = moodArousal,
    trust = trust,
    closeness = closeness,
    completedInteractions = completedInteractions,
    lastInteractionAt = lastInteractionAt,
    sourceChatId = sourceChatId,
    sourceMessageId = sourceMessageId,
    updatedAt = updatedAt,
    formsOfAddress = decodeList(formsOfAddress),
    sharedInJokes = decodeList(sharedInJokes),
    openCommitments = decodeCommitments(openCommitments)
)

internal fun CharacterRuntimeState.toEntity() = CharacterRuntimeStateEntity(
    characterId = characterId,
    moodValence = moodValence,
    moodArousal = moodArousal,
    trust = trust,
    closeness = closeness,
    completedInteractions = completedInteractions,
    lastInteractionAt = lastInteractionAt,
    sourceChatId = sourceChatId,
    sourceMessageId = sourceMessageId,
    updatedAt = updatedAt,
    formsOfAddress = encodeList(formsOfAddress),
    sharedInJokes = encodeList(sharedInJokes),
    openCommitments = encodeCommitments(openCommitments)
)

private fun encodeList(values: List<String>): String = values.joinToString(LIST_SEPARATOR)

private fun decodeList(value: String): List<String> =
    value.split(LIST_SEPARATOR).filter(String::isNotBlank)

private fun encodeCommitments(values: List<OpenCommitment>): String = values
    .filter { it.text.isNotBlank() }
    .joinToString(LIST_SEPARATOR) { commitment ->
        val dueAt = commitment.dueAtMillis?.toString().orEmpty()
        commitment.text.replace(COMMITMENT_FIELD_SEPARATOR, "/") + COMMITMENT_FIELD_SEPARATOR + dueAt
    }

private fun decodeCommitments(value: String): List<OpenCommitment> = value
    .split(LIST_SEPARATOR)
    .filter(String::isNotBlank)
    .mapNotNull { encoded ->
        val separatorIndex = encoded.lastIndexOf(COMMITMENT_FIELD_SEPARATOR)
        if (separatorIndex <= 0) return@mapNotNull null
        val text = encoded.substring(0, separatorIndex)
        val dueAt = encoded.substring(separatorIndex + 1).toLongOrNull()
        if (text.isBlank()) null else OpenCommitment(text, dueAt)
    }
