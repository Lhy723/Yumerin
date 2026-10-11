package com.lhy.yumerin.data.room

import com.lhy.yumerin.core.model.*

fun AppSettingsEntity.toDomain() = AppSettings(
    adultConfirmed = adultConfirmed,
    themeMode = enumValueOrDefault(themeMode, ThemeMode.SYSTEM),
    agentVisibility = enumValueOrDefault(agentVisibility, AgentVisibility.FINAL_ONLY),
    streamingEnabled = streamingEnabled,
    autoMemoryEnabled = autoMemoryEnabled,
    proactiveMessagesEnabled = proactiveMessagesEnabled,
    backgroundCompanionEnabled = backgroundCompanionEnabled,
    dailyProactiveLimit = dailyProactiveLimit,
    quietTimeEnabled = quietTimeEnabled,
    quietStartMinutes = quietStartMinutes,
    quietEndMinutes = quietEndMinutes,
    contextCompressionThreshold = contextCompressionThreshold,
    defaultModelProfileId = defaultModelProfileId,
    reducedMotionEnabled = reducedMotionEnabled,
    userName = userName,
    userAvatarUri = userAvatarUri
)

fun AppSettings.toEntity() = AppSettingsEntity(
    adultConfirmed = adultConfirmed,
    themeMode = themeMode.name,
    agentVisibility = agentVisibility.name,
    streamingEnabled = streamingEnabled,
    autoMemoryEnabled = autoMemoryEnabled,
    proactiveMessagesEnabled = proactiveMessagesEnabled,
    backgroundCompanionEnabled = backgroundCompanionEnabled,
    dailyProactiveLimit = dailyProactiveLimit,
    quietTimeEnabled = quietTimeEnabled,
    quietStartMinutes = quietStartMinutes,
    quietEndMinutes = quietEndMinutes,
    contextCompressionThreshold = contextCompressionThreshold,
    defaultModelProfileId = defaultModelProfileId,
    reducedMotionEnabled = reducedMotionEnabled,
    userName = userName,
    userAvatarUri = userAvatarUri
)
