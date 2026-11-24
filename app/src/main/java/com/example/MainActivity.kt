package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.audio.MusicPlayerManager
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.configureAppCheck
import com.example.data.firebase.signOutUser
import com.example.data.model.CatalogDefaults
import com.example.data.model.UiState
import com.example.ui.components.MiniPlayerBar
import com.example.ui.screens.AiChatScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MyMusicScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.ProfileAndAdminScreen
import com.example.ui.screens.SongStudioScreen
import com.example.ui.screens.VideoStudioScreen
import com.example.ui.theme.AiMusicWorldTheme
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.MusicGradients
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MusicStudioViewModel
import com.example.ui.viewmodel.StudioNavEvent
import com.example.util.DownloadExportHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

private object StudioRoutes {
    const val HOME = "home"
    const val EXPLORE = "explore"
    const val AI_CHAT = "ai_chat"
    const val MY_MUSIC = "my_music"
    const val PROFILE = "profile"
    const val SONG_STUDIO = "song_studio"
    const val VIDEO_STUDIO = "video_studio"
    const val PLAYER = "player"

    fun songStudio(prompt: String = "", genre: String = "DJ Bass Boost"): String {
        return "$SONG_STUDIO?prompt=${Uri.encode(prompt)}&genre=${Uri.encode(genre)}"
    }

    fun videoStudio(songId: String): String {
        return "$VIDEO_STUDIO/${Uri.encode(songId)}"
    }
}

private data class TopNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val isProminentCenter: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureAppCheck(this, intent)

        setContent {
            AiMusicWorldTheme {
                AppContent()
            }
        }
    }
}

@Composable
private fun AppContent() {
    val authUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val uid = authUser?.uid
    if (uid == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(uid) {
            MainStudioShell(currentUserId = uid)
        }
    }
}

@Composable
private fun MainStudioShell(currentUserId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val studioVm: MusicStudioViewModel = viewModel(
        key = currentUserId,
        factory = MusicStudioViewModel.provideFactory(currentUserId)
    )

    val userProfileState by studioVm.userProfile.collectAsStateWithLifecycle()
    val mySongsState by studioVm.mySongs.collectAsStateWithLifecycle()
    val playlistsState by studioVm.playlists.collectAsStateWithLifecycle()
    val chatMessagesState by studioVm.chatMessages.collectAsStateWithLifecycle()
    val publicSongsState by studioVm.publicSongs.collectAsStateWithLifecycle()
    val categoriesState by studioVm.categories.collectAsStateWithLifecycle()
    val appSettings by studioVm.appSettings.collectAsStateWithLifecycle()
    val playerState by studioVm.playerState.collectAsStateWithLifecycle()
    val studioState by studioVm.studioState.collectAsStateWithLifecycle()
    val chatState by studioVm.chatState.collectAsStateWithLifecycle()

    val userProfile = (userProfileState as? UiState.Success)?.data
    val mySongs = (mySongsState as? UiState.Success)?.data.orEmpty()
    val publicSongs = (publicSongsState as? UiState.Success)?.data.orEmpty()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: StudioRoutes.HOME
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(studioVm) {
        studioVm.toastMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(studioVm) {
        studioVm.navEvents.collect { event ->
            when (event) {
                is StudioNavEvent.OpenVideoStudio -> {
                    navController.navigate(StudioRoutes.videoStudio(event.songId))
                }
                is StudioNavEvent.OpenPlayer -> {
                    navController.navigate(StudioRoutes.PLAYER)
                }
                is StudioNavEvent.OpenSongStudio -> {
                    navController.navigate(StudioRoutes.songStudio(event.presetPrompt, event.presetGenre))
                }
            }
        }
    }

    val navItems = remember {
        listOf(
            TopNavItem(StudioRoutes.HOME, "Home", Icons.Default.Home),
            TopNavItem(StudioRoutes.EXPLORE, "Explore", Icons.Default.Explore),
            TopNavItem(StudioRoutes.AI_CHAT, "AI Chat", Icons.Default.AutoAwesome, isProminentCenter = true),
            TopNavItem(StudioRoutes.MY_MUSIC, "My Music", Icons.Default.LibraryMusic),
            TopNavItem(StudioRoutes.PROFILE, "Profile", Icons.Default.Person)
        )
    }

    val showChrome = currentRoute != StudioRoutes.PLAYER
    val showMiniPlayer = showChrome && playerState.currentSong != null

    val navigateTab: (String) -> Unit = { targetRoute ->
        navController.navigate(targetRoute) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        val useRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail && showChrome) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = DeepSpace,
                    contentColor = TextPrimary
                ) {
                    Spacer(Modifier.height(16.dp))
                    navItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTab(item.route) },
                            icon = {
                                if (item.isProminentCenter) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(MusicGradients.primaryBrand),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(item.icon, contentDescription = item.label, tint = TextPrimary)
                                    }
                                } else {
                                    Icon(item.icon, contentDescription = item.label)
                                }
                            },
                            label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = NeonCyan,
                                selectedTextColor = NeonCyan,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = ElectricViolet.copy(alpha = 0.25f)
                            )
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                containerColor = ObsidianBlack,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (showChrome) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (showMiniPlayer) {
                                MiniPlayerBar(
                                    playerState = playerState,
                                    onTogglePlay = { MusicPlayerManager.togglePlayPause(context) },
                                    onNext = { MusicPlayerManager.playNext(context) },
                                    onOpenPlayer = { navController.navigate(StudioRoutes.PLAYER) }
                                )
                            }
                            if (!useRail) {
                                StudioBottomNavigationBar(
                                    items = navItems,
                                    currentRoute = currentRoute,
                                    onSelect = navigateTab
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = StudioRoutes.HOME,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    composable(StudioRoutes.HOME) {
                        HomeScreen(
                            userProfile = userProfile,
                            mySongsState = mySongsState,
                            publicSongsState = publicSongsState,
                            onOpenAiChat = { navigateTab(StudioRoutes.AI_CHAT) },
                            onOpenSongStudio = { prompt, genre ->
                                navController.navigate(StudioRoutes.songStudio(prompt, genre))
                            },
                            onPlaySong = { song, queue ->
                                studioVm.playSong(context, song, queue)
                            },
                            onOpenExplore = { navigateTab(StudioRoutes.EXPLORE) }
                        )
                    }

                    composable(StudioRoutes.EXPLORE) {
                        ExploreScreen(
                            publicSongsState = publicSongsState,
                            categoriesState = categoriesState,
                            onPlaySong = { song, queue ->
                                studioVm.playSong(context, song, queue)
                            },
                            onSelectCategoryForCreation = { category ->
                                navController.navigate(StudioRoutes.songStudio("", category))
                            },
                            onLikePublicSong = { pubSong ->
                                studioVm.likePublicSong(pubSong)
                            },
                            onSaveToLibrary = { song ->
                                studioVm.saveShowcaseOrPublicToLibrary(song)
                            },
                            onReportSong = { songId, title, reason ->
                                studioVm.reportSong(songId, title, reason)
                            }
                        )
                    }

                    composable(StudioRoutes.AI_CHAT) {
                        AiChatScreen(
                            chatMessagesState = chatMessagesState,
                            chatState = chatState,
                            mySongs = mySongs,
                            onSendMessage = { prompt ->
                                studioVm.sendChatMessage(context, prompt)
                            },
                            onClearChat = { studioVm.clearChatHistory() },
                            onPlaySong = { song ->
                                studioVm.playSong(context, song, listOf(song))
                            },
                            onEditSongInStudio = { song ->
                                studioVm.selectActiveSong(song)
                                navController.navigate(StudioRoutes.songStudio(song.prompt, song.genre))
                            },
                            onMakeVideo = { song ->
                                studioVm.openVideoStudioFor(song)
                            }
                        )
                    }

                    composable(StudioRoutes.MY_MUSIC) {
                        MyMusicScreen(
                            mySongsState = mySongsState,
                            playlistsState = playlistsState,
                            onPlaySong = { song, queue ->
                                studioVm.playSong(context, song, queue)
                            },
                            onEditSong = { song ->
                                studioVm.selectActiveSong(song)
                                navController.navigate(StudioRoutes.songStudio(song.prompt, song.genre))
                            },
                            onRenameSong = { song, newTitle ->
                                studioVm.renameSong(song, newTitle)
                            },
                            onDeleteSong = { song ->
                                studioVm.deleteSong(song)
                            },
                            onToggleFavorite = { song ->
                                studioVm.toggleFavorite(song)
                            },
                            onPublishPublic = { song ->
                                studioVm.publishSongToFeed(song)
                            },
                            onMakePrivate = { song ->
                                studioVm.makeSongPrivate(song)
                            },
                            onMakeVideo = { song ->
                                studioVm.openVideoStudioFor(song)
                            },
                            onCreatePlaylist = { name, desc ->
                                studioVm.createPlaylist(name, desc)
                            },
                            onGenerateAiPlaylist = { mood ->
                                studioVm.createAiPlaylist(mood)
                            },
                            onAddSongToPlaylist = { playlist, songId ->
                                studioVm.addSongToPlaylist(playlist, songId)
                            },
                            onDeletePlaylist = { playlistId ->
                                studioVm.deletePlaylist(playlistId)
                            },
                            onDownloadWav = { song -> studioVm.exportSongAudioWav(context, song) },
                            onDownloadVideo = { song -> studioVm.exportSongVideoMp4(context, song) },
                            onDownloadCover = { song -> studioVm.exportSongCoverImage(context, song) },
                            onDownloadLyrics = { song -> studioVm.exportSongLyrics(context, song) }
                        )
                    }

                    composable(StudioRoutes.PROFILE) {
                        ProfileAndAdminScreen(
                            currentUserId = currentUserId,
                            userProfile = userProfile,
                            isAdmin = studioVm.isAdminUser(),
                            appSettings = appSettings,
                            onUpdateProfile = { name, lang ->
                                studioVm.updateProfile(name, lang)
                            },
                            onSignOut = {
                                MusicPlayerManager.stopAndClear(context)
                                signOutUser(credentialManager, scope)
                            }
                        )
                    }

                    composable(
                        route = "${StudioRoutes.SONG_STUDIO}?prompt={prompt}&genre={genre}",
                        arguments = listOf(
                            navArgument("prompt") { type = NavType.StringType; defaultValue = "" },
                            navArgument("genre") { type = NavType.StringType; defaultValue = "DJ Bass Boost" }
                        )
                    ) { entry ->
                        val presetPrompt = Uri.decode(entry.arguments?.getString("prompt").orEmpty())
                        val presetGenre = Uri.decode(entry.arguments?.getString("genre").orEmpty())
                            .ifBlank { "DJ Bass Boost" }

                        SongStudioScreen(
                            presetPrompt = presetPrompt,
                            presetGenre = presetGenre,
                            studioState = studioState,
                            onBack = { navController.popBackStack() },
                            onGenerateLyricsOnly = { recipient, title, prompt, lang, genre, style, mood, voice, beat, inst, dur ->
                                studioVm.generateLyricsDraft(
                                    recipient, title, prompt, lang, genre, style, mood, voice, beat, inst, dur
                                )
                            },
                            onGenerateMusic = { recipient, title, customLyrics, prompt, lang, genre, style, mood, voice, beat, inst, dur, vars ->
                                studioVm.generatePersonalizedMusic(
                                    context, recipient, title, customLyrics, prompt, lang, genre, style, mood, voice, beat, inst, dur, vars
                                )
                            },
                            onEditableLyricsChanged = { studioVm.updateEditableLyrics(it) },
                            onTranslateLyrics = { song, targetLang ->
                                studioVm.translateActiveLyrics(song, targetLang)
                            },
                            onChangeMood = { song, mood ->
                                studioVm.changeSongMood(context, song, mood)
                            },
                            onGenerateCover = { song, customTheme ->
                                studioVm.generateAiAlbumCover(context, song, customTheme)
                            },
                            onPlaySong = { song ->
                                studioVm.playSong(context, song, listOf(song))
                            },
                            onDownloadWav = { studioVm.exportSongAudioWav(context, it) },
                            onDownloadVideo = { studioVm.exportSongVideoMp4(context, it) },
                            onDownloadCover = { studioVm.exportSongCoverImage(context, it) },
                            onDownloadLyrics = { studioVm.exportSongLyrics(context, it) },
                            onShareSong = { DownloadExportHelper.shareSongText(context, it) },
                            onCopyLink = { DownloadExportHelper.copySongLink(context, it) },
                            onToggleFavorite = { studioVm.toggleFavorite(it) },
                            onMakeVideo = { studioVm.openVideoStudioFor(it) }
                        )
                    }

                    composable(
                        route = "${StudioRoutes.VIDEO_STUDIO}/{songId}",
                        arguments = listOf(navArgument("songId") { type = NavType.StringType })
                    ) { entry ->
                        val songId = Uri.decode(entry.arguments?.getString("songId").orEmpty())
                        val resolvedSong = studioState.activeSong?.takeIf { it.id == songId || songId.isBlank() }
                            ?: mySongs.firstOrNull { it.id == songId }
                            ?: publicSongs.firstOrNull { it.id == songId }?.toSongRecord()
                            ?: CatalogDefaults.showcaseSongs.firstOrNull { it.id == songId }?.toSongRecord()
                            ?: playerState.currentSong

                        VideoStudioScreen(
                            song = resolvedSong,
                            studioState = studioState,
                            onBack = { navController.popBackStack() },
                            onRenderVideo = { song, style, overlay, showLyrics, accentHex, durSec, photos ->
                                studioVm.renderMusicVideo(
                                    context, song, style, overlay, showLyrics, accentHex, durSec, photos
                                )
                            },
                            onDownloadMp4 = { studioVm.exportSongVideoMp4(context, it) }
                        )
                    }

                    composable(StudioRoutes.PLAYER) {
                        PlayerScreen(
                            playerState = playerState,
                            onBack = { navController.popBackStack() },
                            onTogglePlay = { MusicPlayerManager.togglePlayPause(context) },
                            onNext = { MusicPlayerManager.playNext(context) },
                            onPrevious = { MusicPlayerManager.playPrevious(context) },
                            onSeek = { MusicPlayerManager.seekTo(it) },
                            onVolumeChange = { MusicPlayerManager.setVolume(it) },
                            onToggleShuffle = { MusicPlayerManager.toggleShuffle() },
                            onCycleRepeat = { MusicPlayerManager.cycleRepeatMode() },
                            onSelectQueueSong = { song ->
                                studioVm.playSong(context, song, playerState.queue)
                            },
                            onRemoveQueueSong = { id -> MusicPlayerManager.removeFromQueue(id) },
                            onDownloadWav = { studioVm.exportSongAudioWav(context, it) },
                            onDownloadVideo = { studioVm.exportSongVideoMp4(context, it) },
                            onDownloadCover = { studioVm.exportSongCoverImage(context, it) },
                            onDownloadLyrics = { studioVm.exportSongLyrics(context, it) },
                            onToggleFavorite = { studioVm.toggleFavorite(it) },
                            onMakeVideo = { studioVm.openVideoStudioFor(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioBottomNavigationBar(
    items: List<TopNavItem>,
    currentRoute: String,
    onSelect: (String) -> Unit
) {
    Surface(
        color = DeepSpace.copy(alpha = 0.96f),
        tonalElevation = 8.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(GlassBorder)
            )
            NavigationBar(
                containerColor = Color.Transparent,
                contentColor = TextPrimary,
                windowInsets = WindowInsets.navigationBars
            ) {
                items.forEach { item ->
                    val selected = currentRoute == item.route
                    if (item.isProminentCenter) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clickable { onSelect(item.route) }
                                .testTag("nav_${item.route}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .shadow(
                                            elevation = if (selected) 12.dp else 6.dp,
                                            shape = CircleShape,
                                            ambientColor = ElectricViolet,
                                            spotColor = NeonCyan
                                        )
                                        .clip(CircleShape)
                                        .background(MusicGradients.primaryBrand),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label,
                                        tint = TextPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) NeonCyan else TextPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    } else {
                        NavigationBarItem(
                            selected = selected,
                            onClick = { onSelect(item.route) },
                            modifier = Modifier.testTag("nav_${item.route}"),
                            icon = {
                                Icon(imageVector = item.icon, contentDescription = item.label)
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NeonCyan,
                                selectedTextColor = NeonCyan,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = ElectricViolet.copy(alpha = 0.22f)
                            )
                        )
                    }
                }
            }
        }
    }
}
