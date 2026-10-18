package com.lhy.yumerin

import androidx.compose.animation.*
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.LocalContentColor as Material3LocalContentColor
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.lhy.yumerin.core.model.AppReleaseMetadata
import com.lhy.yumerin.core.model.AppSettings
import com.lhy.yumerin.core.model.EntityId
import com.lhy.yumerin.core.model.ThemeMode
import com.lhy.yumerin.data.memory.InMemoryAppGraph
import com.lhy.yumerin.designsystem.ChatListSkeleton
import com.lhy.yumerin.designsystem.MotionTokens
import com.lhy.yumerin.designsystem.isSystemReducedMotion
import com.lhy.yumerin.designsystem.LocalComponentMaterial
import com.lhy.yumerin.designsystem.YumerinTypography
import com.lhy.yumerin.designsystem.YumerinAlertDialog as AlertDialog
import com.lhy.yumerin.designsystem.YumerinTextButton as TextButton
import com.lhy.yumerin.domain.AppGraph
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.basic.Surface as Surface

internal enum class AppPage(
    val label: String
) {
    HOME("聊天"),
    EXPLORE("联系人"),
    SETTINGS("设置"),
    PROFILE("个人资料")
}

private sealed interface AppDestination {
    data object CHAT_LIST : AppDestination
    data object CHARACTERS : AppDestination
    data object PROFILE : AppDestination
    data class USER_PROFILE(val returnTo: AppDestination = PROFILE) : AppDestination
    data class CHAT_DETAIL(val chatId: EntityId) : AppDestination
    data class CHAT_SETTINGS(val chatId: EntityId) : AppDestination
    data class CHARACTER_PROFILE(
        val characterId: EntityId,
        val returnTo: AppDestination = CHARACTERS
    ) : AppDestination
    data class CHARACTER_EDITOR(
        val characterId: EntityId?,
        val returnTo: AppDestination = CHARACTERS
    ) : AppDestination
    data class MEMORIES(
        val characterId: EntityId,
        val returnTo: AppDestination = CHARACTERS
    ) : AppDestination
    data class GROUP_EDITOR(val groupId: EntityId? = null) : AppDestination
    data object SETTINGS : AppDestination
    data class SETTINGS_CATEGORY(val category: SettingsCategory) : AppDestination
    data object CHARACTER_CONTINUITY_LIST : AppDestination
    data class CHARACTER_CONTINUITY(val characterId: EntityId) : AppDestination
    data object MODELS : AppDestination
    data object LOCAL_MODELS : AppDestination
    data class ABOUT(val returnTo: AppDestination = PROFILE) : AppDestination
    data object SCHEDULES : AppDestination
    data object DATA_MANAGEMENT : AppDestination
    data object AGENT_DEBUG : AppDestination
    data object CARD_IMPORT : AppDestination
}

private val AppDestination.bottomPage: AppPage?
    get() = when (this) {
        AppDestination.CHAT_LIST -> AppPage.HOME
        AppDestination.CHARACTERS -> AppPage.EXPLORE
        AppDestination.SETTINGS -> AppPage.SETTINGS
        AppDestination.PROFILE -> AppPage.PROFILE
        else -> null
    }

private fun AppPage.toDestination(): AppDestination = when (this) {
    AppPage.HOME -> AppDestination.CHAT_LIST
    AppPage.EXPLORE -> AppDestination.CHARACTERS
    AppPage.SETTINGS -> AppDestination.SETTINGS
    AppPage.PROFILE -> AppDestination.PROFILE
}

private fun AppDestination.rank(): Int = when (this) {
    AppDestination.CHAT_LIST -> 0
    AppDestination.CHARACTERS -> 1
    AppDestination.PROFILE -> 2
    is AppDestination.USER_PROFILE -> 3
    is AppDestination.CHAT_DETAIL -> 4
    is AppDestination.CHAT_SETTINGS -> 5
    is AppDestination.CHARACTER_PROFILE -> 6
    is AppDestination.CHARACTER_EDITOR -> 7
    is AppDestination.MEMORIES -> 8
    is AppDestination.GROUP_EDITOR -> 9
    AppDestination.SETTINGS -> 10
    is AppDestination.SETTINGS_CATEGORY -> 11
    AppDestination.MODELS -> 12
    AppDestination.LOCAL_MODELS -> 13
    is AppDestination.ABOUT -> 14
    AppDestination.SCHEDULES -> 15
    AppDestination.DATA_MANAGEMENT -> 16
    AppDestination.CHARACTER_CONTINUITY_LIST -> 17
    is AppDestination.CHARACTER_CONTINUITY -> 18
    AppDestination.AGENT_DEBUG -> 19
    AppDestination.CARD_IMPORT -> 20
}

@Composable
@Preview
fun App(
    appGraph: AppGraph? = null,
    appIcon: ImageBitmap? = null,
    initialChatId: EntityId? = null,
    onBackgroundPermissionRequest: ((onGranted: () -> Unit) -> Unit) = { onGranted -> onGranted() },
    onExportData: (String) -> Unit = {},
    onImportDataRequest: ((onPayload: (String) -> Unit) -> Unit) = {},
    onPickAvatar: ((onSelected: (String?) -> Unit) -> Unit) = { onSelected -> onSelected(null) },
    onPickLocalModel: ((onSelected: (String?) -> Unit) -> Unit) = { onSelected -> onSelected(null) },
    onPickCardFile: ((onSelected: (ByteArray?) -> Unit) -> Unit) = { onSelected -> onSelected(null) },
    skipAdultConfirmationForDebug: Boolean = false,
    appVersionName: String = "开发版",
    onCheckForUpdates: (suspend () -> AppReleaseMetadata)? = null,
    onOpenExternalUrl: ((String) -> Unit)? = null
) {
    val graph = appGraph ?: remember { InMemoryAppGraph() }
    val settings by graph.settings.observeSettings().collectAsState(AppSettings())
    val reducedMotion = settings.reducedMotionEnabled || isSystemReducedMotion()
    var initialized by remember(graph) { mutableStateOf(false) }

    LaunchedEffect(graph) {
        graph.ensureSeedData()
        initialized = true
    }

    val isDarkTheme = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val themeColors = remember(settings.themeColor, settings.componentMaterial) {
        settings.themeColor.colorSchemes(settings.componentMaterial)
    }

    val themeController = remember(isDarkTheme, themeColors) {
        ThemeController(
            colorSchemeMode = if (isDarkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light,
            lightColors = themeColors.light,
            darkColors = themeColors.dark
        )
    }

    CompositionLocalProvider(LocalComponentMaterial provides settings.componentMaterial) {
        MiuixTheme(controller = themeController, textStyles = YumerinTypography.miuix) {
            MaterialTheme(
                colorScheme = MiuixTheme.colorScheme.toMaterialColorScheme(isDarkTheme),
                typography = YumerinTypography.material3(MiuixTheme.textStyles)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    // Miuix and Material3 use separate content-color locals. Bridge the
                    // Material3 one so default Text color follows the active theme too.
                    CompositionLocalProvider(
                        Material3LocalContentColor provides MaterialTheme.colorScheme.onSurface
                    ) {
                        AppContent(
                            graph = graph,
                            appIcon = appIcon,
                            initialChatId = initialChatId,
                            settings = settings,
                            reducedMotion = reducedMotion,
                            initialized = initialized,
                            onBackgroundPermissionRequest = onBackgroundPermissionRequest,
                            onExportData = onExportData,
                            onImportDataRequest = onImportDataRequest,
                            onPickAvatar = onPickAvatar,
                onPickCardFile = onPickCardFile,
                            onPickLocalModel = onPickLocalModel,
                            skipAdultConfirmationForDebug = skipAdultConfirmationForDebug,
                            appVersionName = appVersionName,
                            onCheckForUpdates = onCheckForUpdates,
                            onOpenExternalUrl = onOpenExternalUrl
                        )
                    }
                }
            }
        }
    }
}

private fun Colors.toMaterialColorScheme(isDark: Boolean) = if (isDark) {
    darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = primaryVariant,
        onTertiary = onPrimaryVariant,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariantSummary,
        outline = outline,
        outlineVariant = dividerLine,
        inverseSurface = onSurface,
        inverseOnSurface = surface,
        inversePrimary = primaryVariant,
        scrim = windowDimming,
        surfaceTint = surface,
        surfaceDim = surfaceVariant,
        surfaceBright = surface,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surfaceContainer,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest
    )
} else {
    lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = primaryVariant,
        onTertiary = onPrimaryVariant,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariantSummary,
        outline = outline,
        outlineVariant = dividerLine,
        inverseSurface = onSurface,
        inverseOnSurface = surface,
        inversePrimary = primaryVariant,
        scrim = windowDimming,
        surfaceTint = surface,
        surfaceDim = surfaceVariant,
        surfaceBright = surface,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surfaceContainer,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest
    )
}

@Composable
private fun AppContent(
    graph: AppGraph,
    appIcon: ImageBitmap?,
    initialChatId: EntityId?,
    settings: AppSettings,
    reducedMotion: Boolean,
    initialized: Boolean,
    onBackgroundPermissionRequest: ((onGranted: () -> Unit) -> Unit),
    onExportData: (String) -> Unit,
    onImportDataRequest: ((onPayload: (String) -> Unit) -> Unit),
    onPickAvatar: ((onSelected: (String?) -> Unit) -> Unit),
    onPickLocalModel: ((onSelected: (String?) -> Unit) -> Unit),
    onPickCardFile: ((onSelected: (ByteArray?) -> Unit) -> Unit),
    skipAdultConfirmationForDebug: Boolean,
    appVersionName: String,
    onCheckForUpdates: (suspend () -> AppReleaseMetadata)?,
    onOpenExternalUrl: ((String) -> Unit)?
) {
    val navigationBackdrop = rememberLayerBackdrop()
    val glassButtonBackdrop = rememberLayerBackdrop()
            val chats by graph.chats.observeChats().collectAsState(emptyList())
    val characters by graph.characters.observeCharacters().collectAsState(emptyList())
    val archivedCharacters by graph.characters.observeArchivedCharacters().collectAsState(emptyList())
    val allCharacters = remember(characters, archivedCharacters) {
        (characters + archivedCharacters).distinctBy { it.id }
    }
    val groups by graph.groups.observeGroups().collectAsState(emptyList())
    val modelProfiles by graph.models.observeProfiles().collectAsState(emptyList())
    val localArtifacts by graph.models.observeLocalArtifacts().collectAsState(emptyList())
    val useCases = remember(graph) { graph.useCases }
    var destination by remember(initialChatId) {
        mutableStateOf<AppDestination>(
            initialChatId?.let { AppDestination.CHAT_DETAIL(it) } ?: AppDestination.CHAT_LIST
        )
    }
    val scope = rememberCoroutineScope()

    PlatformBackHandler(
        enabled = destination.bottomPage == null,
        onBack = {
            destination = when (val current = destination) {
                is AppDestination.CHAT_DETAIL -> AppDestination.CHAT_LIST
                is AppDestination.CHAT_SETTINGS -> AppDestination.CHAT_DETAIL(current.chatId)
                is AppDestination.CHARACTER_PROFILE -> current.returnTo
                is AppDestination.CHARACTER_EDITOR -> current.returnTo
                is AppDestination.MEMORIES -> current.returnTo
                is AppDestination.GROUP_EDITOR -> AppDestination.CHAT_LIST
                is AppDestination.USER_PROFILE -> current.returnTo
                AppDestination.SETTINGS -> AppDestination.PROFILE
                is AppDestination.SETTINGS_CATEGORY -> AppDestination.SETTINGS
                AppDestination.CHARACTER_CONTINUITY_LIST -> AppDestination.SETTINGS
                is AppDestination.CHARACTER_CONTINUITY -> AppDestination.CHARACTER_CONTINUITY_LIST
                AppDestination.MODELS -> AppDestination.SETTINGS
                AppDestination.LOCAL_MODELS -> AppDestination.MODELS
                is AppDestination.ABOUT -> current.returnTo
                AppDestination.SCHEDULES -> AppDestination.SETTINGS
                AppDestination.DATA_MANAGEMENT -> AppDestination.SETTINGS
            AppDestination.AGENT_DEBUG -> AppDestination.SETTINGS
            AppDestination.CARD_IMPORT -> AppDestination.CHARACTERS
                else -> AppDestination.CHAT_LIST
            }
        }
    )

    LaunchedEffect(initialChatId, chats.size) {
        if (initialChatId != null && chats.any { it.id == initialChatId }) {
            graph.chats.markRead(initialChatId)
            destination = AppDestination.CHAT_DETAIL(initialChatId)
        }
    }

    fun updateSettings(updated: AppSettings) {
        scope.launch {
            val backgroundChanged = updated.backgroundCompanionEnabled != settings.backgroundCompanionEnabled
            val proactiveChanged = updated.proactiveMessagesEnabled != settings.proactiveMessagesEnabled
            val permissionSensitiveEnable = backgroundChanged && updated.backgroundCompanionEnabled
            if (permissionSensitiveEnable) {
                graph.settings.saveSettings(updated.copy(backgroundCompanionEnabled = false))
                onBackgroundPermissionRequest {
                    scope.launch {
                        graph.settings.saveSettings(updated)
                        if (backgroundChanged || proactiveChanged) {
                            graph.backgroundCompanion.setEnabled(updated.backgroundCompanionEnabled)
                            graph.agentGoalScheduler.setEnabled(updated.proactiveMessagesEnabled)
                        }
                    }
                }
            } else {
                graph.settings.saveSettings(updated)
                if (backgroundChanged || proactiveChanged) {
                    graph.backgroundCompanion.setEnabled(updated.backgroundCompanionEnabled)
                    graph.agentGoalScheduler.setEnabled(updated.proactiveMessagesEnabled)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .layerBackdrop(glassButtonBackdrop)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(navigationBackdrop)
                ) {
                    if (!initialized) {
                        ChatListSkeleton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            reducedMotion = reducedMotion
                        )
                    } else {
                        AnimatedContent(
                        targetState = destination,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            val initialBottomPageIndex = initialState.bottomPage?.let(AppPage.entries::indexOf)
                            val targetBottomPageIndex = targetState.bottomPage?.let(AppPage.entries::indexOf)
                            val direction = when {
                                initialBottomPageIndex != null && targetBottomPageIndex != null -> {
                                    if (targetBottomPageIndex >= initialBottomPageIndex) {
                                        SlideDirection.Left
                                    } else {
                                        SlideDirection.Right
                                    }
                                }
                                targetState.rank() >= initialState.rank() -> SlideDirection.Left
                                else -> SlideDirection.Right
                            }
                            (
                                slideIntoContainer(
                                    towards = direction,
                                    animationSpec = tween(
                                        if (reducedMotion) 0 else MotionTokens.pageDurationMillis
                                    )
                                ) + fadeIn(
                                    animationSpec = tween(
                                        if (reducedMotion) 0 else MotionTokens.smallDurationMillis
                                    )
                                )
                            ) togetherWith (
                                slideOutOfContainer(
                                    towards = direction,
                                    animationSpec = tween(
                                        if (reducedMotion) 0 else MotionTokens.pageDurationMillis
                                    )
                                ) + fadeOut(
                                    animationSpec = tween(
                                        if (reducedMotion) 0 else MotionTokens.smallDurationMillis
                                    )
                                )
                            ) using SizeTransform(clip = false)
                        },
                        label = "screen-transition"
                        ) { screen ->
                            CompositionLocalProvider(LocalGlassButtonBackdrop provides glassButtonBackdrop) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (screen.bottomPage == null) {
                                            Modifier.navigationBarsPadding()
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                when (screen) {
                                    AppDestination.CHAT_LIST -> ChatListPage(
                                                                                chats = chats,
                                        characters = allCharacters,
                                        groups = groups,
                                        reducedMotion = reducedMotion,
                                        onOpenChat = {
                                            scope.launch { graph.chats.markRead(it) }
                                            destination = AppDestination.CHAT_DETAIL(it)
                                        },
                                        onTogglePinned = { chat ->
                                            scope.launch { graph.chats.saveChat(chat.copy(pinned = !chat.pinned)) }
                                        },
                                        onToggleMuted = { chat ->
                                            scope.launch { graph.chats.saveChat(chat.copy(muted = !chat.muted)) }
                                        },
                                        onMarkUnread = { chat ->
                                            scope.launch { graph.chats.saveChat(chat.copy(unreadCount = maxOf(1, chat.unreadCount))) }
                                        },
                                        onDeleteChat = { chat -> scope.launch { useCases.deleteChat(chat.id) } },
                                        onCreateGroup = { destination = AppDestination.GROUP_EDITOR() },
                                        onEditGroup = { groupId -> destination = AppDestination.GROUP_EDITOR(groupId) }
                                    )
                                    AppDestination.CHARACTERS -> CharactersPage(
                                                                                characters = characters,
                                        archivedCharacters = archivedCharacters,
                                        onCreate = { destination = AppDestination.CHARACTER_EDITOR(null) },
                                        onImportCard = { destination = AppDestination.CARD_IMPORT },
                                        onOpenProfile = { characterId ->
                                            destination = AppDestination.CHARACTER_PROFILE(characterId)
                                        },
                                        onEdit = { destination = AppDestination.CHARACTER_EDITOR(it) },
                                        onCreateChat = { character ->
                                            scope.launch {
                                                val chat = useCases.createDirectChat(character)
                                                destination = AppDestination.CHAT_DETAIL(chat.id)
                                            }
                                        }
                                    )
                                    AppDestination.PROFILE -> ProfilePage(
                                                                                settings = settings,
                                        onPickAvatar = onPickAvatar,
                                        onSettingsChanged = ::updateSettings,
                                        onOpenUserProfile = { destination = AppDestination.USER_PROFILE() }
                                    )
                                    is AppDestination.USER_PROFILE -> UserProfilePage(
                                                                                settings = settings,
                                        onSettingsChanged = ::updateSettings,
                                        onPickAvatar = onPickAvatar,
                                        onBack = { destination = screen.returnTo }
                                    )
                                    is AppDestination.CHAT_DETAIL -> ChatDetailPage(
                                                                                chatId = screen.chatId,
                                        chats = chats,
                                        characters = allCharacters,
                                        groups = groups,
                                        modelProfiles = modelProfiles,
                                        graph = graph,
                                        sendMessage = useCases.sendChatMessage,
                                        retryMessage = { messageId, currentSettings ->
                                            useCases.retryChatResponse(messageId, currentSettings)
                                        },
                                        settings = settings,
                                        onBack = { destination = AppDestination.CHAT_LIST },
                                        onOpenModels = { destination = AppDestination.MODELS },
                                        onOpenChatSettings = { destination = AppDestination.CHAT_SETTINGS(screen.chatId) },
                                        onOpenCharacterProfile = { characterId ->
                                            destination = AppDestination.CHARACTER_PROFILE(
                                                characterId = characterId,
                                                returnTo = AppDestination.CHAT_DETAIL(screen.chatId)
                                            )
                                        },
                                        onOpenUserProfile = {
                                            destination = AppDestination.USER_PROFILE(AppDestination.CHAT_DETAIL(screen.chatId))
                                        }
                                    )
                                    is AppDestination.CHARACTER_PROFILE -> CharacterDetailPage(
                                                                                character = allCharacters.firstOrNull { it.id == screen.characterId },
                                        groups = groups,
                                        chats = chats,
                                        onBack = { destination = screen.returnTo },
                                        onOpenChat = {
                                            val character = allCharacters.firstOrNull { it.id == screen.characterId }
                                            if (character != null) {
                                                val directChat = chats
                                                    .filter { it.characterId == character.id && it.deletedAt == null }
                                                    .maxByOrNull { it.lastActivityAt }
                                                if (directChat != null) {
                                                    destination = AppDestination.CHAT_DETAIL(directChat.id)
                                                } else if (!character.archived) {
                                                    scope.launch {
                                                        val chat = useCases.createDirectChat(character)
                                                        destination = AppDestination.CHAT_DETAIL(chat.id)
                                                    }
                                                }
                                            }
                                        },
                                        onOpenMemory = {
                                            destination = AppDestination.MEMORIES(
                                                characterId = screen.characterId,
                                                returnTo = screen
                                            )
                                        },
                                        onEdit = {
                                            destination = AppDestination.CHARACTER_EDITOR(
                                                characterId = screen.characterId,
                                                returnTo = screen
                                            )
                                        },
                                        onOpenGroup = { groupId ->
                                            val groupChat = chats
                                                .filter { it.groupId == groupId && it.deletedAt == null }
                                                .maxByOrNull { it.lastActivityAt }
                                            if (groupChat != null) destination = AppDestination.CHAT_DETAIL(groupChat.id)
                                        }
                                    )
                                    is AppDestination.CHAT_SETTINGS -> ChatSettingsPage(
                                                                                graph = graph,
                                        chatId = screen.chatId,
                                        chat = chats.firstOrNull { it.id == screen.chatId },
                                        characters = allCharacters,
                                        groups = groups,
                                        onChatChanged = { updated -> scope.launch { graph.chats.saveChat(updated) } },
                                        onBack = { destination = AppDestination.CHAT_DETAIL(screen.chatId) }
                                    )
                                    is AppDestination.CHARACTER_EDITOR -> CharacterEditorPage(
                                                                                existing = allCharacters.firstOrNull { it.id == screen.characterId },
                                        modelProfiles = modelProfiles,
                                        defaultModelProfileId = settings.defaultModelProfileId,
                                        onBack = { destination = screen.returnTo },
                                        onPickAvatar = onPickAvatar,
                                        onOpenMemory = screen.characterId?.let { id ->
                                            {
                                                destination = AppDestination.MEMORIES(
                                                    characterId = id,
                                                    returnTo = AppDestination.CHARACTER_EDITOR(id, screen.returnTo)
                                                )
                                            }
                                        },
                                        onArchive = screen.characterId?.let { id ->
                                            { archived -> scope.launch { useCases.archiveCharacter(id, archived) } }
                                        },
                                        onDelete = screen.characterId?.let { id ->
                                            {
                                                scope.launch {
                                                    useCases.deleteCharacter(id)
                                                    destination = screen.returnTo
                                                }
                                            }
                                        },
                                        onSave = { character ->
                                            scope.launch {
                                                useCases.saveCharacter(character)
                                                destination = screen.returnTo
                                            }
                                        }
                                    )
                                    is AppDestination.MEMORIES -> MemoryPage(
                                                                                character = allCharacters.firstOrNull { it.id == screen.characterId },
                                        graph = graph,
                                        onBack = { destination = screen.returnTo }
                                    )
                                    is AppDestination.GROUP_EDITOR -> GroupEditorPage(
                                                                                graph = graph,
                                        existing = screen.groupId?.let { id -> groups.firstOrNull { it.id == id } },
                                        characters = characters,
                                        onPickAvatar = onPickAvatar,
                                        onBack = { destination = AppDestination.CHAT_LIST },
                                        onSave = { group ->
                                            scope.launch {
                                                if (screen.groupId == null) useCases.createGroupChat(group)
                                                else useCases.updateGroupChat(group, group.memberCharacterIds.toSet())
                                                destination = AppDestination.CHAT_LIST
                                            }
                                        }
                                    )
                                    AppDestination.SETTINGS -> SettingsPage(
                                        settings = settings,
                                        onOpenCategory = { category ->
                                            destination = AppDestination.SETTINGS_CATEGORY(category)
                                        },
                                        onOpenCharacterContinuity = { destination = AppDestination.CHARACTER_CONTINUITY_LIST },
                                        onOpenUserProfile = {
                                            destination = AppDestination.USER_PROFILE(AppDestination.SETTINGS)
                                        },
                                        onOpenModels = { destination = AppDestination.MODELS },
                                        onOpenDataManagement = { destination = AppDestination.DATA_MANAGEMENT },
                                        onOpenAgentDebug = { destination = AppDestination.AGENT_DEBUG },
                                        onOpenAbout = { destination = AppDestination.ABOUT(AppDestination.SETTINGS) }
                                    )
                                    is AppDestination.SETTINGS_CATEGORY -> SettingsCategoryPage(
                                                                                category = screen.category,
                                        settings = settings,
                                        onSettingsChanged = ::updateSettings,
                                        onOpenSchedules = { destination = AppDestination.SCHEDULES },
                                        onBack = { destination = AppDestination.SETTINGS }
                                    )
                                    AppDestination.CHARACTER_CONTINUITY_LIST -> CharacterContinuityListPage(
                                        characters = allCharacters,
                                        onOpenCharacter = { characterId -> destination = AppDestination.CHARACTER_CONTINUITY(characterId) },
                                        onBack = { destination = AppDestination.SETTINGS }
                                    )
                                    is AppDestination.CHARACTER_CONTINUITY -> {
                                        val character = allCharacters.firstOrNull { it.id == screen.characterId }
                                        if (character == null) {
                                            LaunchedEffect(screen.characterId) {
                                                destination = AppDestination.CHARACTER_CONTINUITY_LIST
                                            }
                                        } else {
                                            val state by useCases.observeCharacterRuntimeState(screen.characterId).collectAsState(null)
                                            CharacterContinuityPage(
                                                character = character,
                                                state = state?.let {
                                                    CharacterContinuityUiState(
                                                        moodValence = it.moodValence,
                                                        trust = it.trust,
                                                        closeness = it.closeness
                                                    )
                                                },
                                                onSave = { mood, trust, closeness ->
                                                    scope.launch { useCases.updateCharacterContinuity(screen.characterId, mood, trust, closeness) }
                                                },
                                                onBack = { destination = AppDestination.CHARACTER_CONTINUITY_LIST }
                                            )
                                        }
                                    }
                                    AppDestination.MODELS -> ModelsPage(
                                                                                graph = graph,
                                        profiles = modelProfiles,
                                        localArtifactCount = localArtifacts.size,
                                        settings = settings,
                                        onSettingsChanged = ::updateSettings,
                                        onOpenLocalModels = { destination = AppDestination.LOCAL_MODELS },
                                        onBack = { destination = AppDestination.SETTINGS }
                                    )
                                    AppDestination.LOCAL_MODELS -> LocalModelsPage(
                                        graph = graph,
                                        profiles = modelProfiles,
                                        localArtifacts = localArtifacts,
                                        settings = settings,
                                        onSettingsChanged = ::updateSettings,
                                        onPickLocalModel = onPickLocalModel,
                                        onBack = { destination = AppDestination.MODELS }
                                    )
                                    is AppDestination.ABOUT -> AboutPage(
                                        appIcon = appIcon,
                                        currentVersionName = appVersionName,
                                        onCheckForUpdates = onCheckForUpdates,
                                        onOpenExternalUrl = onOpenExternalUrl,
                                        onBack = { destination = screen.returnTo }
                                    )
                                    AppDestination.SCHEDULES -> SchedulesPage(
                                                                                graph = graph,
                                        characters = characters,
                                        chats = chats,
                                        onBack = { destination = AppDestination.SETTINGS }
                                    )
                                    AppDestination.AGENT_DEBUG -> AgentDebugPage(
                                        diagnostics = useCases.agentRunDiagnostics,
                                        onBack = { destination = AppDestination.SETTINGS }
                                    )
                                    AppDestination.CARD_IMPORT -> CardImportPage(
                                        importCard = useCases.importCharacterCard,
                                        onPickFile = onPickCardFile,
                                        onImported = { characterId, _ -> destination = AppDestination.CHARACTER_PROFILE(characterId) },
                                        onBack = { destination = AppDestination.CHARACTERS }
                                    )
                                    AppDestination.DATA_MANAGEMENT -> DataManagementPage(
                                                                                graph = graph,
                                        onExportData = onExportData,
                                        onImportDataRequest = onImportDataRequest,
                                        onBack = { destination = AppDestination.SETTINGS }
                                    )
                                }
                            }
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = destination.bottomPage != null,
                modifier = Modifier.fillMaxWidth(),
                enter = fadeIn(tween(if (reducedMotion) 0 else MotionTokens.bottomBarDurationMillis)) +
                    slideInVertically(tween(if (reducedMotion) 0 else MotionTokens.bottomBarDurationMillis)) { it / 3 },
                exit = fadeOut(tween(if (reducedMotion) 0 else MotionTokens.bottomBarDurationMillis)) +
                    slideOutVertically(tween(if (reducedMotion) 0 else MotionTokens.bottomBarDurationMillis)) { it / 3 }
            ) {
                destination.bottomPage?.let { selectedPage ->
                    MiuixFloatingBottomBar(
                        backdrop = navigationBackdrop,
                        selectedPage = selectedPage,
                        onPageSelected = { destination = it.toDestination() },
                        reducedMotion = reducedMotion,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (!skipAdultConfirmationForDebug && !settings.adultConfirmed) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("开始使用 Yumerin") },
            text = {
                Text("Yumerin 面向已满 18 岁的用户。你可以创建和自定义虚拟角色，但不会支持性化未成年人、冒充现实人物或协助伤害行为。")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { graph.settings.saveSettings(settings.copy(adultConfirmed = true)) }
                }) { Text("我已满 18 岁，继续") }
            }
        )
    }
}
