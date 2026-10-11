package com.lhy.yumerin.data.room

import com.lhy.yumerin.core.model.*

fun CharacterEntity.toDomain() = CharacterProfile(
    id = id,
    name = name,
    avatarUri = avatarUri,
    relationshipFraming = relationshipFraming,
    setting = setting,
    description = description,
    personality = personality,
    tone = tone,
    backstory = backstory,
    interactionStyle = interactionStyle,
    modelProfileId = modelProfileId,
    archived = archived,
    createdAt = createdAt,
    updatedAt = updatedAt,
    proactiveEnabled = proactiveEnabled,
    proactiveDailyLimit = proactiveDailyLimit,
    deletedAt = deletedAt
)

fun CharacterProfile.toEntity() = CharacterEntity(
    id = id,
    name = name,
    avatarUri = avatarUri,
    relationshipFraming = relationshipFraming,
    setting = setting,
    description = description,
    personality = personality,
    tone = tone,
    backstory = backstory,
    interactionStyle = interactionStyle,
    modelProfileId = modelProfileId,
    archived = archived,
    createdAt = createdAt,
    updatedAt = updatedAt,
    proactiveEnabled = proactiveEnabled,
    proactiveDailyLimit = proactiveDailyLimit,
    deletedAt = deletedAt
)

fun ChatThreadEntity.toDomain() = ChatThread(
    id = id,
    kind = enumValueOrDefault(kind, ChatKind.DIRECT),
    title = title,
    characterId = characterId,
    groupId = groupId,
    avatarUri = avatarUri,
    lastMessagePreview = lastMessagePreview,
    lastActivityAt = lastActivityAt,
    unreadCount = unreadCount,
    pinned = pinned,
    muted = muted,
    shortTermSummary = shortTermSummary,
    summaryMaintenanceMode = enumValueOrDefault(summaryMaintenanceMode, SummaryMaintenanceMode.AUTO),
    deletedAt = deletedAt
)

fun ChatThread.toEntity() = ChatThreadEntity(
    id = id,
    kind = kind.name,
    title = title,
    characterId = characterId,
    groupId = groupId,
    avatarUri = avatarUri,
    lastMessagePreview = lastMessagePreview,
    lastActivityAt = lastActivityAt,
    unreadCount = unreadCount,
    pinned = pinned,
    muted = muted,
    shortTermSummary = shortTermSummary,
    summaryMaintenanceMode = summaryMaintenanceMode.name,
    deletedAt = deletedAt
)

fun ChatMessageEntity.toDomain() = ChatMessage(
    id = id,
    chatId = chatId,
    senderId = senderId,
    senderType = enumValueOrDefault(senderType, MessageSenderType.SYSTEM),
    senderName = senderName,
    senderAvatarUri = senderAvatarUri,
    text = text,
    timestamp = timestamp,
    type = enumValueOrDefault(type, MessageType.TEXT),
    isProactive = isProactive,
    isRead = isRead,
    deliveryState = enumValueOrDefault(deliveryState, MessageDeliveryState.SENT),
    responseState = responseState?.let { enumValueOrDefault(it, AiResponseState.GENERATING) },
    errorMessage = errorMessage,
    usedMemoryIds = usedMemoryIds.split('\u001F').filter(String::isNotBlank),
    deletedAt = deletedAt
)

fun ChatMessage.toEntity() = ChatMessageEntity(
    id = id,
    chatId = chatId,
    senderId = senderId,
    senderType = senderType.name,
    senderName = senderName,
    senderAvatarUri = senderAvatarUri,
    text = text,
    timestamp = timestamp,
    type = type.name,
    isProactive = isProactive,
    isRead = isRead,
    deliveryState = deliveryState.name,
    responseState = responseState?.name,
    errorMessage = errorMessage,
    usedMemoryIds = usedMemoryIds.joinToString("\u001F"),
    deletedAt = deletedAt
)

fun GroupEntity.toDomain() = GroupProfile(
    id = id,
    name = name,
    avatarUri = avatarUri,
    memberCharacterIds = memberCharacterIds.split(',').filter(String::isNotBlank),
    scenario = scenario,
    storySetting = storySetting,
    interactionStyle = interactionStyle,
    autonomousTurnLimit = autonomousTurnLimit,
    summary = summary,
    summaryMaintenanceMode = enumValueOrDefault(summaryMaintenanceMode, SummaryMaintenanceMode.AUTO),
    archived = archived,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun GroupProfile.toEntity() = GroupEntity(
    id = id,
    name = name,
    avatarUri = avatarUri,
    memberCharacterIds = memberCharacterIds.joinToString(","),
    scenario = scenario,
    storySetting = storySetting,
    interactionStyle = interactionStyle,
    autonomousTurnLimit = autonomousTurnLimit,
    summary = summary,
    summaryMaintenanceMode = summaryMaintenanceMode.name,
    archived = archived,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)
