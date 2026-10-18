package com.lhy.yumerin.domain.usecase

import com.lhy.yumerin.core.model.CharacterProfile
import com.lhy.yumerin.core.model.ChatKind
import com.lhy.yumerin.core.model.ChatThread
import com.lhy.yumerin.core.model.EntityId
import com.lhy.yumerin.core.model.MemoryCategory
import com.lhy.yumerin.core.model.newEntityId
import com.lhy.yumerin.core.model.MemoryEntry
import com.lhy.yumerin.core.model.MemoryReviewState
import com.lhy.yumerin.core.model.SummaryMaintenanceMode
import com.lhy.yumerin.data.portable.st.SillyTavernCardParser
import com.lhy.yumerin.domain.repository.CharacterRepository
import com.lhy.yumerin.domain.repository.ChatRepository
import com.lhy.yumerin.domain.repository.MemoryRepository
import com.lhy.yumerin.domain.safety.SafetyPolicy

/**
 * Imports a community character card into a new Character with its own direct
 * chat. The embedded world book becomes the character's knowledge seed via
 * accepted memory entries (Character-scoped per ADR-0009), and the greeting
 * (first_mes) seeds the chat so the imported card opens like it does in
 * community frontends. Unknown fields stay in the persona for lossless export.
 */
class ImportCharacterCardUseCase(
    private val characters: CharacterRepository,
    private val chats: ChatRepository,
    private val memories: MemoryRepository,
    private val saveCharacter: SaveCharacterUseCase
) {
    sealed interface Result {
        data class Imported(
            val characterId: EntityId,
            val chatId: EntityId,
            val personaName: String,
            val memoryCount: Int,
            /** Fields the parser could not map; shown to the user before/after import. */
            val unmappedFields: List<String>,
            val spec: String
        ) : Result

        data class Failed(val reason: String) : Result
    }

    suspend operator fun invoke(pngOrJsonBytes: ByteArray): Result {
        val parseResult = SillyTavernCardParser.parse(pngOrJsonBytes)
        val parsed = when (parseResult) {
            is SillyTavernCardParser.ParseResult.Parsed -> parseResult
            is SillyTavernCardParser.ParseResult.Failed ->
                // Fall back to raw JSON text import for .json files.
                runCatching { pngOrJsonBytes.decodeToString() }
                    .map { SillyTavernCardParser.parseCardJson(it, preferV3 = false) }
                    .getOrNull()?.let { parsed ->
                        when (parsed) {
                            is SillyTavernCardParser.ParseResult.Parsed -> parsed
                            is SillyTavernCardParser.ParseResult.Failed -> return Result.Failed(parseResult.reason)
                        }
                    }
                    ?: return Result.Failed(parseResult.reason)
        }
        val persona = parsed.persona
        if (persona.name.isBlank()) return Result.Failed("角色卡缺少名字。")
        val personaText = listOf(persona.description, persona.personality, persona.scenario, persona.mesExample)
            .joinToString("\n")
        if (!SafetyPolicy.evaluate(personaText).allowed) {
            return Result.Failed("角色卡内容越过安全边界，未导入。")
        }

        val now = useCaseNowMillis()
        val character = CharacterProfile(
            id = newEntityId("character"),
            name = persona.name,
            avatarUri = null,
            relationshipFraming = "由角色卡导入",
            setting = persona.scenario,
            description = persona.description,
            personality = persona.personality,
            tone = "",
            backstory = persona.description,
            interactionStyle = persona.firstMes.take(120),
            modelProfileId = null,
            archived = false,
            createdAt = now,
            updatedAt = now
        )
        characters.saveCharacter(character)
        val chat = ChatThread(
            id = newEntityId("chat"),
            kind = ChatKind.DIRECT,
            title = character.name,
            characterId = character.id,
            groupId = null,
            avatarUri = null,
            lastMessagePreview = persona.firstMes.take(80),
            lastActivityAt = now,
            unreadCount = 0,
            pinned = false,
            muted = false,
            shortTermSummary = persona.scenario.take(500),
            summaryMaintenanceMode = SummaryMaintenanceMode.AUTO
        )
        chats.saveChat(chat)
        if (persona.firstMes.isNotBlank()) {
            chats.appendMessage(
                com.lhy.yumerin.core.model.ChatMessage(
                    id = newEntityId("message"),
                    chatId = chat.id,
                    senderId = character.id,
                    senderType = com.lhy.yumerin.core.model.MessageSenderType.CHARACTER,
                    senderName = character.name,
                    senderAvatarUri = null,
                    text = persona.firstMes,
                    timestamp = now + 1,
                    type = com.lhy.yumerin.core.model.MessageType.TEXT,
                    isProactive = false,
                    isRead = false,
                    deliveryState = com.lhy.yumerin.core.model.MessageDeliveryState.SENT,
                    responseState = com.lhy.yumerin.core.model.AiResponseState.COMPLETED
                )
            )
        }
        // World book entries seed the character's memory space as accepted facts.
        persona.characterBook?.entries?.forEach { entry ->
            if (entry.content.isBlank()) return@forEach
            memories.saveMemory(
                MemoryEntry(
                    id = newEntityId("memory"),
                    characterId = character.id,
                    sourceChatId = null,
                    sourceMessageId = null,
                    category = MemoryCategory.LONG_TERM,
                    content = entry.content.take(500),
                    confidence = 0.9f,
                    reviewState = MemoryReviewState.ACCEPTED,
                    pinned = entry.constant,
                    supersedesId = null,
                    createdAt = now,
                    updatedAt = now,
                    reviewAt = null,
                    importance = if (entry.constant) 0.9f else 0.5f
                )
            )
        }
        return Result.Imported(
            characterId = character.id,
            chatId = chat.id,
            personaName = character.name,
            memoryCount = persona.characterBook?.entries?.count { it.content.isNotBlank() } ?: 0,
            unmappedFields = parsed.unmappedFields,
            spec = parsed.spec
        )
    }
}
