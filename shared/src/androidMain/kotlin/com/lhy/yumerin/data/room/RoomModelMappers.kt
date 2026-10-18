package com.lhy.yumerin.data.room

import com.lhy.yumerin.core.model.*

fun ModelProfileEntity.toDomain() = ModelProfile(
    id = id,
    displayName = displayName,
    providerKind = enumValueOrDefault(providerKind, ModelProviderKind.OPENAI_COMPATIBLE),
    providerName = providerName,
    baseUrl = baseUrl,
    modelId = modelId,
    credentialAlias = credentialAlias,
    contextLength = contextLength,
    contextLengthMode = enumValueOrDefault(contextLengthMode, ContextLengthMode.AUTO),
    generationParameters = GenerationParameters(
        temperature = temperature,
        topP = topP,
        maxOutputTokens = maxOutputTokens,
        stopSequences = stopSequences.split('\u001F').filter(String::isNotBlank)
    ),
    localModelId = localModelId,
    localModelVersion = localModelVersion,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
    localBackend = localBackend,
    deletedAt = deletedAt,
    capabilities = capabilities.split("\u001F").filter(String::isNotBlank)
        .mapNotNull { name -> ModelCapability.entries.firstOrNull { it.name == name } }
        .toSet()
)

fun ModelProfile.toEntity() = ModelProfileEntity(
    id = id,
    displayName = displayName,
    providerKind = providerKind.name,
    providerName = providerName,
    baseUrl = baseUrl,
    modelId = modelId,
    credentialAlias = credentialAlias,
    contextLength = contextLength,
    contextLengthMode = contextLengthMode.name,
    temperature = generationParameters.temperature,
    topP = generationParameters.topP,
    maxOutputTokens = generationParameters.maxOutputTokens,
    stopSequences = generationParameters.stopSequences.joinToString("\u001F"),
    localModelId = localModelId,
    localModelVersion = localModelVersion,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
    localBackend = localBackend,
    deletedAt = deletedAt,
    capabilities = capabilities.joinToString("\u001F") { it.name }
)

fun LocalModelArtifactEntity.toDomain() = LocalModelArtifact(
    id = id,
    logicalModelId = logicalModelId.ifBlank { id },
    displayName = displayName,
    version = version,
    fileName = fileName,
    fileSizeBytes = fileSizeBytes,
    sha256 = sha256,
    license = license,
    sourceUrl = sourceUrl,
    backend = backend,
    contextLength = contextLength,
    state = enumValueOrDefault(state, LocalModelState.CATALOGUED),
    localPath = localPath,
    assignedProfileCount = assignedProfileCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deviceRequirements = deviceRequirements,
    licenseAcknowledged = licenseAcknowledged,
    diagnosticReason = diagnosticReason,
    deletedAt = deletedAt
)

fun LocalModelArtifact.toEntity() = LocalModelArtifactEntity(
    id = id,
    logicalModelId = logicalModelId,
    displayName = displayName,
    version = version,
    fileName = fileName,
    fileSizeBytes = fileSizeBytes,
    sha256 = sha256,
    license = license,
    sourceUrl = sourceUrl,
    backend = backend,
    contextLength = contextLength,
    state = state.name,
    localPath = localPath,
    assignedProfileCount = assignedProfileCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deviceRequirements = deviceRequirements,
    licenseAcknowledged = licenseAcknowledged,
    diagnosticReason = diagnosticReason,
    deletedAt = deletedAt
)
