package com.lhy.yumerin.data.memory

import com.lhy.yumerin.core.model.CharacterProfile
import com.lhy.yumerin.core.model.ChatKind
import com.lhy.yumerin.core.model.ChatMessage
import com.lhy.yumerin.core.model.ChatThread
import com.lhy.yumerin.core.model.MessageSenderType
import com.lhy.yumerin.core.model.MessageType
import com.lhy.yumerin.core.model.SummaryMaintenanceMode
import yumerin.shared.generated.resources.Res

internal data class DefaultCompanionSeedData(
    val character: CharacterProfile,
    private val previousCharacter: CharacterProfile,
    private val chatId: String,
    private val greeting: String,
    private val chatSummary: String
) {
    val chat = ChatThread(
        id = chatId,
        kind = ChatKind.DIRECT,
        title = character.name,
        characterId = character.id,
        groupId = null,
        avatarUri = character.avatarUri,
        lastMessagePreview = greeting,
        lastActivityAt = 0L,
        unreadCount = 0,
        pinned = true,
        muted = false,
        shortTermSummary = chatSummary,
        summaryMaintenanceMode = SummaryMaintenanceMode.AUTO
    )

    val welcomeMessage = ChatMessage(
        id = "message-default-welcome",
        chatId = chatId,
        senderId = character.id,
        senderType = MessageSenderType.CHARACTER,
        senderName = character.name,
        senderAvatarUri = character.avatarUri,
        text = greeting,
        timestamp = 0L,
        type = MessageType.TEXT,
        isProactive = false,
        isRead = true
    )

    /** Refresh only fields that still match the previous resource, preserving profile edits. */
    fun migrateExistingProfile(existing: CharacterProfile): CharacterProfile {
        if (existing.id != character.id) return existing
        return existing.copy(
            relationshipFraming = refreshSeededField(existing.relationshipFraming, previousCharacter.relationshipFraming, character.relationshipFraming),
            setting = refreshSeededField(existing.setting, previousCharacter.setting, character.setting),
            description = refreshSeededField(existing.description, previousCharacter.description, character.description),
            personality = refreshSeededField(existing.personality, previousCharacter.personality, character.personality),
            tone = refreshSeededField(existing.tone, previousCharacter.tone, character.tone),
            backstory = refreshSeededField(existing.backstory, previousCharacter.backstory, character.backstory),
            interactionStyle = refreshSeededField(existing.interactionStyle, previousCharacter.interactionStyle, character.interactionStyle)
        )
    }

    private fun refreshSeededField(current: String, previous: String, updated: String): String =
        if (current == previous) updated else current
}

internal object DefaultCompanionSeed {
    private const val CURRENT_PROFILE = "files/default_companion.properties"
    private const val PREVIOUS_PROFILE = "files/default_companion_v1.properties"

    suspend fun load(): DefaultCompanionSeedData {
        val current = readProfile(CURRENT_PROFILE)
        val previous = readProfile(PREVIOUS_PROFILE)
        require(previous.version < current.version) { "Default companion profile versions must increase." }
        return DefaultCompanionSeedData(
            character = current.character,
            previousCharacter = previous.character,
            chatId = current.chatId,
            greeting = current.greeting,
            chatSummary = current.chatSummary
        )
    }

    private suspend fun readProfile(path: String): ParsedCompanionProfile {
        val values = mutableMapOf<String, String>()
        Res.readBytes(path).decodeToString().lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
            val separator = line.indexOf('=')
            require(separator > 0) { "Invalid default companion profile line ${index + 1} in $path." }
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            require(key !in values) { "Duplicate default companion profile key '$key' in $path." }
            values[key] = value
        }

        fun required(key: String): String = values[key] ?: error("Missing '$key' in default companion profile $path.")
        val version = required("version").toIntOrNull()
            ?: error("Invalid version in default companion profile $path.")
        val avatarUri = when (required("avatarKey")) {
            "default-companion" -> defaultCompanionAvatarUri
            else -> error("Unknown avatar key in default companion profile $path.")
        }

        return ParsedCompanionProfile(
            version = version,
            chatId = required("chatId"),
            greeting = required("greeting"),
            chatSummary = required("chatSummary"),
            character = CharacterProfile(
                id = required("characterId"),
                name = required("name"),
                avatarUri = avatarUri,
                relationshipFraming = required("relationshipFraming"),
                setting = required("setting"),
                description = required("description"),
                personality = required("personality"),
                tone = required("tone"),
                backstory = required("backstory"),
                interactionStyle = required("interactionStyle"),
                modelProfileId = null,
                archived = false,
                createdAt = 0L,
                updatedAt = 0L
            )
        )
    }

    private data class ParsedCompanionProfile(
        val version: Int,
        val chatId: String,
        val greeting: String,
        val chatSummary: String,
        val character: CharacterProfile
    )
}
