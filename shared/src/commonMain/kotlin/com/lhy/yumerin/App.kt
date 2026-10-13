package com.lhy.yumerin

import androidx.compose.animation.*
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.lhy.yumerin.core.model.AppSettings
import com.lhy.yumerin.core.model.EntityId
import com.lhy.yumerin.core.model.ThemeMode
import com.lhy.yumerin.data.memory.InMemoryAppGraph
import com.lhy.yumerin.designsystem.ChatListSkeleton
import com.lhy.yumerin.designsystem.MotionTokens
import com.lhy.yumerin.designsystem.isSystemReducedMotion
import com.lhy.yumerin.domain.AppGraph
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

internal enum class AppPage(
    val label: String
) {
    HOME("聊天"),
    EXPLORE("角色"),
    PROFILE("我的")
}

private sealed interface AppDestination {
    data object CHAT_LIST : AppDestination
    data object CHARACTERS : AppDestination
    data object PROFILE : AppDestination
    data class USER_PROFILE(val returnTo: AppDestination = PROFILE) : AppDestination
    data class CHAT_DETAIL(val chatId: EntityId) : AppDestination
    data class CHAT_SETTINGS(val chatId: EntityId) : AppDestination
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
    data object CHARACTER_CONTINUITY_LIST : AppDestination
    data class CHARACTER_CONTINUITY(val characterId: EntityId) : AppDestination
    data object MODELS : AppDestination
    data object ABOUT : AppDestination
    data object SCHEDULES : AppDestination
    data object DATA_MANAGEMENT : AppDestination
}

private val AppDestination.bottomPage: AppPage?
    get() = when (this) {
        AppDestination.CHAT_LIST -> AppPage.HOME
        AppDestination.CHARACTERS -> AppPage.EXPLORE
        AppDestination.PROFILE -> AppPage.PROFILE
        else -> null
    }

private fun AppPage.toDestination(): AppDestination = when (this) {
    AppPage.HOME -> AppDestination.CHAT_LIST
    AppPage.EXPLORE -> AppDestination.CHARACTERS
    AppPage.PROFILE -> AppDestination.PROFILE
}

private fun AppDestination.rank(): Int = when (this) {
    AppDestination.CHAT_LIST -> 0
    AppDestination.CHARACTERS -> 1
    AppDestination.PROFILE -> 2
    is AppDestination.USER_PROFILE -> 3
    is AppDestination.CHAT_DETAIL -> 4
    is AppDestination.CHAT_SETTINGS -> 5
    is AppDestination.CHARACTER_EDITOR -> 6
    is AppDestination.MEMORIES -> 7
    is AppDestination.GROUP_EDITOR -> 8
    AppDestination.SETTINGS -> 9
    AppDestination.CHARACTER_CONTINUITY_LIST -> 14
    is AppDestination.CHARACTER_CONTINUITY -> 15
    AppDestination.MODELS -> 10
    AppDestination.ABOUT -> 11
    AppDestination.SCHEDULES -> 12
    AppDestination.DATA_MANAGEMENT -> 13
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
    onPickLocalModel: ((onSelected: (String?) -> Unit) -> Unit) = { onSelected -> onSelected(null) }
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

    val themeController = remember(isDarkTheme) {
        ThemeController(
            colorSchemeMode = if (isDarkTheme) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
            keyColor = Color(0xFF6659B8)
        )
    }

    MiuixTheme(controller = themeController) {
        MaterialTheme(
            colorScheme = if (isDarkTheme) yumerinDarkColorScheme() else yumerinLightColorScheme()
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground
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
                    onPickLocalModel = onPickLocalModel
                )
            }
        }
    }
}

private fun yumerinLightColorScheme() = lightColorScheme(
    primary = Color(0xFF5F55A8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E3FF),
    onPrimaryContainer = Color(0xFF211A55),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E1F1),
    onSecondaryContainer = Color(0xFF201A2C),
    tertiary = Color(0xFF8A5268),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E5),
    onTertiaryContainer = Color(0xFF391020),
    background = Color(0xFFF6F4FA),
    onBackground = Color(0xFF201E27),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF201E27),
    surfaceVariant = Color(0xFFEAE7F1),
    onSurfaceVariant = Color(0xFF4D4957),
    outline = Color(0xFF7C7787),
    outlineVariant = Color(0xFFCAC5D3)
)

private fun yumerinDarkColorScheme() = darkColorScheme(
    primary = Color(0xFFC9C1FF),
    onPrimary = Color(0xFF30256C),
    primaryContainer = Color(0xFF494184),
    onPrimaryContainer = Color(0xFFE7E3FF),
    secondary = Color(0xFFCBC3D9),
    onSecondary = Color(0xFF332D40),
    secondaryContainer = Color(0xFF4A4357),
    onSecondaryContainer = Color(0xFFE8E1F1),
    tertiary = Color(0xFFFFB1CA),
    onTertiary = Color(0xFF521D34),
    tertiaryContainer = Color(0xFF6E354B),
    onTertiaryContainer = Color(0xFFFFD9E5),
    background = Color(0xFF15131A),
    onBackground = Color(0xFFE8E2EE),
    surface = Color(0xFF1D1A23),
    onSurface = Color(0xFFE8E2EE),
    surfaceVariant = Color(0xFF2C2833),
    onSurfaceVariant = Color(0xFFCBC4D3),
    outline = Color(0xFF948D9E),
    outlineVariant = Color(0xFF494450)
)

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
    onPickLocalModel: ((onSelected: (String?) -> Unit) -> Unit)
) {
    val backdrop = rememberLayerBackdrop()
    val navigationBackdrop = rememberLayerBackdrop()
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
                is AppDestination.CHARACTER_EDITOR -> current.returnTo
                is AppDestination.MEMORIES -> current.returnTo
                is AppDestination.GROUP_EDITOR -> AppDestination.CHAT_LIST
                is AppDestination.USER_PROFILE -> current.returnTo
                AppDestination.SETTINGS -> AppDestination.PROFILE
                AppDestination.CHARACTER_CONTINUITY_LIST -> AppDestination.SETTINGS
                is AppDestination.CHARACTER_CONTINUITY -> AppDestination.CHARACTER_CONTINUITY_LIST
                AppDestination.MODELS -> AppDestination.SETTINGS
                AppDestination.ABOUT -> AppDestination.PROFILE
                AppDestination.SCHEDULES -> AppDestination.SETTINGS
                AppDestination.DATA_MANAGEMENT -> AppDestination.SETTINGS
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
                        }
                    }
                }
            } else {
                graph.settings.saveSettings(updated)
                if (backgroundChanged || proactiveChanged) {
                    graph.backgroundCompanion.setEnabled(updated.backgroundCompanionEnabled)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
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
                            val direction = if (targetState.rank() >= initialState.rank()) {
                                SlideDirection.Left
                            } else {
                                SlideDirection.Right
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
                            when (screen) {
                            AppDestination.CHAT_LIST -> ChatListPage(
                                backdrop = backdrop,
                                chats = chats,
                                characters = allCharacters,
                                groups = groups,
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
                                backdrop = backdrop,
                                characters = characters,
                                archivedCharacters = archivedCharacters,
                                onCreate = { destination = AppDestination.CHARACTER_EDITOR(null) },
                                onEdit = { destination = AppDestination.CHARACTER_EDITOR(it) },
                                onCreateChat = { character ->
                                    scope.launch {
                                        val chat = useCases.createDirectChat(character)
                                        destination = AppDestination.CHAT_DETAIL(chat.id)
                                    }
                                }
                            )
                            AppDestination.PROFILE -> ProfilePage(
                                backdrop = backdrop,
                                settings = settings,
                                onOpenSettings = { destination = AppDestination.SETTINGS },
                                onOpenAbout = { destination = AppDestination.ABOUT },
                                onOpenUserProfile = { destination = AppDestination.USER_PROFILE() }
                            )
                            is AppDestination.USER_PROFILE -> UserProfilePage(
                                backdrop = backdrop,
                                settings = settings,
                                onSettingsChanged = ::updateSettings,
                                onPickAvatar = onPickAvatar,
                                onBack = { destination = screen.returnTo }
                            )
                            is AppDestination.CHAT_DETAIL -> ChatDetailPage(
                                backdrop = backdrop,
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
                                    destination = AppDestination.CHARACTER_EDITOR(
                                        characterId = characterId,
                                        returnTo = AppDestination.CHAT_DETAIL(screen.chatId)
                                    )
                                },
                                onOpenUserProfile = {
                                    destination = AppDestination.USER_PROFILE(AppDestination.CHAT_DETAIL(screen.chatId))
                                }
                            )
                            is AppDestination.CHAT_SETTINGS -> ChatSettingsPage(
                                backdrop = backdrop,
                                graph = graph,
                                chatId = screen.chatId,
                                chat = chats.firstOrNull { it.id == screen.chatId },
                                characters = allCharacters,
                                groups = groups,
                                onChatChanged = { updated -> scope.launch { graph.chats.saveChat(updated) } },
                                onBack = { destination = AppDestination.CHAT_DETAIL(screen.chatId) }
                            )
                            is AppDestination.CHARACTER_EDITOR -> CharacterEditorPage(
                                backdrop = backdrop,
                                existing = allCharacters.firstOrNull { it.id == screen.characterId },
                                modelProfiles = modelProfiles,
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
                                backdrop = backdrop,
                                character = allCharacters.firstOrNull { it.id == screen.characterId },
                                graph = graph,
                                onBack = { destination = screen.returnTo }
                            )
                            is AppDestination.GROUP_EDITOR -> GroupEditorPage(
                                backdrop = backdrop,
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
                                backdrop = backdrop,
                                settings = settings,
                                onSettingsChanged = ::updateSettings,
                                onOpenModels = { destination = AppDestination.MODELS },
                                onOpenSchedules = { destination = AppDestination.SCHEDULES },
                                onOpenDataManagement = { destination = AppDestination.DATA_MANAGEMENT },
                                onOpenCharacterContinuity = { destination = AppDestination.CHARACTER_CONTINUITY_LIST },
                                onBack = { destination = AppDestination.PROFILE }
                            )
                            AppDestination.CHARACTER_CONTINUITY_LIST -> CharacterContinuityListPage(
                                characters = characters,
                                onOpenCharacter = { id -> destination = AppDestination.CHARACTER_CONTINUITY(id) },
                                onBack = { destination = AppDestination.SETTINGS }
                            )
                            is AppDestination.CHARACTER_CONTINUITY -> {
                                val character = allCharacters.firstOrNull { it.id == screen.characterId }
                                if (character == null) {
                                    destination = AppDestination.CHARACTER_CONTINUITY_LIST
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
                                            scope.launch {
                                                useCases.updateCharacterContinuity(screen.characterId, mood, trust, closeness)
                                            }
                                        },
                                        onBack = { destination = AppDestination.CHARACTER_CONTINUITY_LIST }
                                    )
                                }
                            }
                            AppDestination.MODELS -> ModelsPage(
                                backdrop = backdrop,
                                graph = graph,
                                profiles = modelProfiles,
                                localArtifacts = localArtifacts,
                                settings = settings,
                                onSettingsChanged = ::updateSettings,
                                onPickLocalModel = onPickLocalModel,
                                onBack = { destination = AppDestination.SETTINGS }
                            )
                            AppDestination.ABOUT -> AboutPage(
                                backdrop = backdrop,
                                appIcon = appIcon,
                                onBack = { destination = AppDestination.PROFILE }
                            )
                            AppDestination.SCHEDULES -> SchedulesPage(
                                backdrop = backdrop,
                                graph = graph,
                                characters = characters,
                                chats = chats,
                                onBack = { destination = AppDestination.SETTINGS }
                            )
                            AppDestination.DATA_MANAGEMENT -> DataManagementPage(
                                backdrop = backdrop,
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

    if (!settings.adultConfirmed) {
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
